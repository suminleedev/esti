package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
}
