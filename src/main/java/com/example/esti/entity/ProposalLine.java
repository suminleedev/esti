package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "proposal_line", schema = "APP")
@Getter
@Setter
@NoArgsConstructor
public class ProposalLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 제안서 FK
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_id", nullable = false)
    private Proposal proposal;

    // 카탈로그 FK
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductCatalog product;

    @Column(length = 50)
    private String area;         // 욕실1, 주방 등

    @Column(length = 100)
    private String category;     // 양변기, 세면기, ...

    private Integer qty;         // 제안 수량

    @Column(length = 200)
    private String note;         // 색상/옵션 등

    // 옵션: 나중 견적 전환을 위해 참조 단가를 저장하고 싶다면
    @Column(precision = 15, scale = 2)
    private BigDecimal unitPrice;  // 선택사항 (지금은 안 써도 됨)
}

