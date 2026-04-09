package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "proposal", schema = "APP")
@Getter
@Setter
@NoArgsConstructor
public class Proposal extends BaseEntity {

    public enum Status {
        DRAFT,      // 임시저장 (수정 가능)
        SUBMITTED,  // 저장 (선택) 회수하면 DRAFT로 / 혹은 수정가능 유지
        SENT,       // 제출 (수정 불가)
    }

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;              // 상태

    @Column(precision = 5, scale = 2)
    private BigDecimal globalMarginRate;// 일괄 마진율(%)

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;    // 삭제일시

    // 선택
    @Column(name = "deleted_by")        // 삭제자
    private Long deletedBy;
}

