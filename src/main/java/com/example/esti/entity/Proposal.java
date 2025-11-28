package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "proposal", schema = "APP")
@Getter
@Setter
@NoArgsConstructor
public class Proposal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 템플릿 기반인지 (없으면 null)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private ProposalTemplate template;

    @Column(nullable = false, length = 200)
    private String projectName;      // 현장명

    @Column(length = 100)
    private String manager;          // 담당자

    @Column(length = 10)
    private String date;             // 작성일 (yyyy-MM-dd 문자열로 저장, 나중에 LocalDate로 바꿔도 됨)

    @Column(length = 50)
    private String apartmentType;    // 평형

    private Integer households;      // 세대수

    @Column(length = 500)
    private String note;             // 기타 메모

    @Column(length = 1000)
    private String areasJson;        // ["욕실1","욕실2"] JSON 문자열

    @Column(length = 1000)
    private String requiredCategoriesJson; // ["양변기","세면기"] JSON 문자열
}

