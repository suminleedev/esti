package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "product_catalog", schema = "APP")
@Getter
@Setter
@AllArgsConstructor  // 모든 필드 생성자
public class ProductCatalog extends BaseEntity {

    // ===== Getter & Setter =====
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, length = 100)
    private String name;        // 제품명

    @Setter
    @Column(length = 100)
    private String spec;        // 규격/모델명

    @Setter
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal basePrice;  // 기준 단가

    @Setter
    @Column(length = 255)
    private String description; // 설명

    @Setter
    @Column(length = 255)
    private String imageUrl;    // 제품 이미지 경로

    // ===== 기본 생성자 =====
    public ProductCatalog() {}

}
