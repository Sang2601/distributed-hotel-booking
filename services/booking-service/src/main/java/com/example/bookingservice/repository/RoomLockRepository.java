package com.example.bookingservice.repository;

import com.example.bookingservice.entity.RoomLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoomLockRepository extends JpaRepository<RoomLock, Long> {

    /** SELECT ... FOR UPDATE: giữ khoá dòng tới khi transaction kết thúc. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM RoomLock l WHERE l.roomId = :roomId")
    Optional<RoomLock> lockByRoomId(@Param("roomId") Long roomId);
}
