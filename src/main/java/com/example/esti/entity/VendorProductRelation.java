package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 *  대표 품목 - 부속 품목 연결 엔티티
 */
@Entity
@Table(name = "vendor_product_relation", schema = "APP")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorProductRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 기준 상품
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_vendor_product_id", nullable = false)
    private VendorProduct sourceProduct;

    // 연결 상품
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_vendor_product_id", nullable = false)
    private VendorProduct targetProduct;

    // 예: MAIN, ACCESSORY, SEAT_COVER, TANK
    @Column(name = "relation_type", nullable = false, length = 50)
    private String relationType;
}