package com.example.esti.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "estimate_items", schema = "APP")
public class EstimateItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estimate_id")
    private Estimate estimate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private ProductCatalog product;
}


