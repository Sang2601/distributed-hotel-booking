package com.example.bookingservice.lock;

import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Chương 6 – Khoá phân tán bằng Redis (Redisson RLock, thuật toán Redlock một node).
 *
 * Khoá "lock:room:{id}" nằm trong Redis dùng chung, nên mọi instance booking-service đều thấy:
 * request đặt CÙNG phòng phải xếp hàng, kể cả khi chúng rơi vào hai instance khác nhau.
 *  - Chờ khoá tối đa 5 giây, quá thời gian thì báo 409 để khách thử lại.
 *  - Khoá tự hết hạn sau 10 giây (lease), nên instance chết khi đang giữ khoá cũng không làm kẹt phòng.
 *
 * Chương 8 – Redis không phản hồi (timeout 0,5s): trả về Optional.empty(), BookingService chuyển sang
 * khoá pessimistic của PostgreSQL (SELECT ... FOR UPDATE), và trong 10 giây sau đó bỏ qua Redis luôn.
 * Hệ thống chậm hơn chút nhưng vẫn đúng.
 */
@Component
public class DistributedRoomLock {

    private static final Logger log = LoggerFactory.getLogger(DistributedRoomLock.class);
    private static final long WAIT_SECONDS = 5;
    private static final long LEASE_SECONDS = 10;
    private static final long RECONNECT_BACKOFF_MS = 10_000;
    /** Sau một lần Redis lỗi, bỏ qua Redis trong khoảng này (kiểu circuit breaker) để không request nào phải chờ. */
    private static final long REDIS_DOWN_BACKOFF_MS = 10_000;

    private final String redisAddress;
    private final MeterRegistry meterRegistry;

    private volatile RedissonClient client;
    private volatile long nextConnectAttempt = 0;
    private volatile long redisDownUntil = 0;

    public DistributedRoomLock(@Value("${spring.data.redis.host:localhost}") String host,
                               @Value("${spring.data.redis.port:6379}") int port,
                               MeterRegistry meterRegistry) {
        this.redisAddress = "redis://" + host + ":" + port;
        this.meterRegistry = meterRegistry;
    }

    /**
     * @return khoá đã giữ; Optional.empty() nếu Redis không dùng được (gọi bên dùng khoá database).
     * @throws ResponseStatusException 409 nếu chờ quá lâu vì request khác đang đặt cùng phòng.
     */
    public Optional<RLock> acquire(Long roomId) {
        if (System.currentTimeMillis() < redisDownUntil) {
            return Optional.empty(); // Redis vừa lỗi gần đây: dùng ngay khoá database
        }
        RedissonClient redisson = client();
        if (redisson == null) {
            return Optional.empty();
        }
        RLock lock = redisson.getLock("lock:room:" + roomId);
        try {
            if (lock.tryLock(WAIT_SECONDS, LEASE_SECONDS, TimeUnit.SECONDS)) {
                meterRegistry.counter("booking.room.lock", "type", "redis").increment();
                return Optional.of(lock);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Bị gián đoạn khi chờ khoá phòng");
        } catch (RedisException e) {
            redisDownUntil = System.currentTimeMillis() + REDIS_DOWN_BACKOFF_MS;
            log.warn("Redis không phản hồi khi khoá phòng {} – dùng khoá database trong {}s tới ({})",
                    roomId, REDIS_DOWN_BACKOFF_MS / 1000, e.getMessage());
            return Optional.empty();
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Phòng đang được người khác đặt, vui lòng thử lại sau giây lát");
    }

    public void release(RLock lock) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (RedisException e) {
            // Không nhả được thì khoá tự hết hạn sau LEASE_SECONDS
            log.warn("Không nhả được khoá {} ({}), khoá sẽ tự hết hạn", lock.getName(), e.getMessage());
        }
    }

    /** Tạo kết nối lần đầu khi cần; Redis chưa sẵn sàng thì thử lại sau 10 giây thay vì làm hỏng khởi động. */
    private RedissonClient client() {
        RedissonClient current = client;
        if (current != null || System.currentTimeMillis() < nextConnectAttempt) {
            return current;
        }
        synchronized (this) {
            if (client == null && System.currentTimeMillis() >= nextConnectAttempt) {
                try {
                    Config config = new Config();
                    config.useSingleServer()
                            .setAddress(redisAddress)
                            .setConnectTimeout(500)
                            .setTimeout(500)
                            .setRetryAttempts(0)
                            .setRetryInterval(100);
                    client = Redisson.create(config);
                    log.info("Đã kết nối Redis {} cho khoá phân tán", redisAddress);
                } catch (RuntimeException e) {
                    nextConnectAttempt = System.currentTimeMillis() + RECONNECT_BACKOFF_MS;
                    log.warn("Chưa kết nối được Redis {} – tạm dùng khoá database ({})", redisAddress, e.getMessage());
                }
            }
            return client;
        }
    }

    @PreDestroy
    public void shutdown() {
        if (client != null) {
            client.shutdown();
        }
    }
}
