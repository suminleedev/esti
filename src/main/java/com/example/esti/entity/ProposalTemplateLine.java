package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(length = 50)
    private String area;

    @Column(length = 100)
    private String category;

    private Integer defaultQty;

    @Column(length = 200)
    private String note;
}
