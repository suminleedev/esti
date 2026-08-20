package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "proposal_template", schema = "APP")
@Getter
@Setter
@NoArgsConstructor
public class ProposalTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String templateName;

    @Column(length = 50)
    private String apartmentType;

    @Column(length = 1000)
    private String areasJson;

    @Column(length = 1000)
    private String requiredCategoriesJson;

    /** 일괄 마진율(%). 값이 없는 기존 템플릿은 null이고, 프론트에서 10% 폴백한다. */
    @Column(precision = 5, scale = 2)
    private BigDecimal globalMarginRate;
}
