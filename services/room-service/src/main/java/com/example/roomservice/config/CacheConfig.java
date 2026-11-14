package com.example.roomservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.lettuce.core.ClientOptions;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Chương 4 – Redis cache.
 * Danh sách phòng gần như không đổi nhưng được đọc ở mọi lần khách mở trang đặt phòng.
 * Lưu kết quả vào Redis 10 phút: các lần sau (ở BẤT KỲ instance room-service nào, vì Redis
 * dùng chung) trả về từ bộ nhớ, không chạm database.
 *
 * Chương 8 – Redis chết thì không làm hỏng chức năng: lỗi cache chỉ được ghi log,
 * request vẫn đọc thẳng database.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(CacheProperties cacheProperties) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
        if (cacheProperties.getRedis().getTimeToLive() != null) {
            config = config.entryTtl(cacheProperties.getRedis().getTimeToLive());
        }
        return config;
    }

    /**
     * Mất kết nối Redis thì lệnh cache bị từ chối NGAY (không chờ timeout), request đọc thẳng database;
     * Lettuce tự nối lại khi Redis sống lại.
     */
    @Bean
    public LettuceClientConfigurationBuilderCustomizer failFastWhenRedisDown() {
        return builder -> builder.clientOptions(ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build());
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Redis lỗi khi đọc cache {}::{} – đọc thẳng database ({})", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Redis lỗi khi ghi cache {}::{} ({})", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Redis lỗi khi xoá cache {}::{} ({})", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Redis lỗi khi xoá cache {} ({})", cache.getName(), e.getMessage());
            }
        };
    }
}
