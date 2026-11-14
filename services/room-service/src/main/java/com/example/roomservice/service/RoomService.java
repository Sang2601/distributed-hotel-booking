package com.example.roomservice.service;

import com.example.roomservice.entity.Room;
import com.example.roomservice.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    // 🔍 SEARCH AVAILABLE ROOMS
    // Lọc theo sức chứa; ngày chỉ dùng ở booking-service (nơi kiểm tra trùng lịch)
    @Cacheable(cacheNames = "roomSearch", key = "#capacity")
    public List<Room> searchAvailableRooms(
            int capacity,
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        log.info("Cache miss roomSearch::{} – truy vấn database", capacity);
        return roomRepository.findByCapacity(capacity);
    }

    // 📋 ADMIN: GET ALL ROOMS
    @Cacheable(cacheNames = "rooms", key = "'all'")
    public List<Room> getAllRooms() {
        log.info("Cache miss rooms::all – truy vấn database");
        return roomRepository.findAll();
    }

    // 🔥 GET ROOM BY ID (CHO BOOKING / PAYMENT)
    @Cacheable(cacheNames = "room", key = "#id")
    public Room getRoomById(Long id) {
        log.info("Cache miss room::{} – truy vấn database", id);
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
    }
}
