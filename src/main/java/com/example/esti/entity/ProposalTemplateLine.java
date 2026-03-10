package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "proposal_template_line", schema = "APP")
@Getter
@Setter
@NoArgsConstructor
public class ProposalTemplateLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 템플릿 FK
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ProposalTemplate template;

    // 카탈로그 FK
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductCatalog product;

    // ===== 상품 스냅샷 =====
    @Column(length = 500)
    private String specs;

    @Lob
    private String description;

    @Column(length = 1000)
    private String imageUrl;

    @Column(length = 100)
    private String vendorCode;

    @Column(length = 255)
    private String vendorName;

    @Column(length = 255)
    private String vendorItemName;

    @Column(length = 100)
    private String mainItemCode;

    @Column(length = 100)
    private String oldItemCode;

    @Column(precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(length = 500)
    private String remark;

    // ===== 템플릿 설정값 =====
    @Column(length = 50)
    private String area;

    @Column(length = 100)
    private String category;

    private Integer defaultQty;

    @Column(length = 200)
    private String note;
}
