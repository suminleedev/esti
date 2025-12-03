package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "item_component", schema = "APP")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ItemComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 부모 세트
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id", nullable = false)
    private ProductCatalog catalog;

    @Column(length = 200, nullable = false)
    private String componentName;     // 부속명 (양변기, 양부속, 시트커버 등)

    @Column(length = 20)
    private String componentType;     // 'MAIN' / 'SUB'

    @Column(precision = 10, scale = 2)
    private BigDecimal componentPrice;

    @Column
    private Integer quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor; // A/B사별 부속 단가를 구분하고 싶으면 사용
}
