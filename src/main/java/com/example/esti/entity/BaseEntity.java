package com.example.esti.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@MappedSuperclass
public abstract class BaseEntity {

    @Column(updatable = false)
    private String createdBy;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private String updatedBy;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getter/Setter
}

