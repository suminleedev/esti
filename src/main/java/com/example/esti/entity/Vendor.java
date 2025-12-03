package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "vendor", schema = "APP")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20, unique = true)
    private String vendorCode; // 'A', 'B' 등

    @Column(nullable = false, length = 100)
    private String vendorName; // 예: 'A사', 'B사'
}

