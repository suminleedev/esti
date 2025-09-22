package com.example.esti.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "estimate")
public class Estimate {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
}
