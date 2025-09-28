package com.example.esti.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers", schema = "APP")
public class Customer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name; // 고객명/회사명

    private String contactName;
    private String phone;
    private String email;
    private String address;
    private String remarks;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL)
    private List<Estimate> estimates = new ArrayList<>();
}

