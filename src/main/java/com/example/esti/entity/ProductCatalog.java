package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "product_catalog", schema = "APP")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductCatalog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== 기본 정보 =====
    @Column(nullable = false, length = 200)
    private String name;        // 제품명(세트명)

    @Column(length = 100)
    private String model;       // 모델명

    @Column(length = 100)
    private String brand;       // 브랜드

    @Column(length = 255)
    private String specs;       // 표준 규격 (공통 스펙)

    @Column(precision = 15, scale = 2)
    private BigDecimal basePrice;  // 내 기준 단가(없으면 null)

    @Column(length = 500)
    private String description; // 설명

    @Column(length = 500)
    private String imageUrl;    // 제품 이미지 경로

    // ===== 카테고리/타입 =====
    @Column(length = 100)
    private String categoryLarge;  // 대분류 (양변기, 세면기 등)

    @Column(length = 100)
    private String categorySmall;  // 소분류 (원피스양변기 등)

    @Column(length = 50, unique = true)
    private String masterCode;     // 내 기준 코드 (초기엔 공급사 품번 활용 가능)

    @Column(length = 20)
    private String itemType;       // 'SET', 'PART' 등
}
