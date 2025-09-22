package com.example.esti.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "estimate_item")
public class EstimateItem {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
}
