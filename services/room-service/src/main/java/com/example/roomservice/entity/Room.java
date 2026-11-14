package com.example.roomservice.entity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.persistence.*;

@Entity
@Table(name = "rooms")
// Cho phép Jackson đọc/ghi thẳng vào field khi lưu và lấy Room từ Redis cache
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private int capacity;

    private double price;

    public Room() {}

    public Room(String name, int capacity, double price) {
        this.name = name;
        this.capacity = capacity;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public double getPrice() {
        return price;
    }
}
