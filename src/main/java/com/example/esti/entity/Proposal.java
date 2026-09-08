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

    /**
     * 낙관적 잠금 버전 (F-026).
     *
     * <p><b>JPA가 자동으로 올려 주지만, 그것만으로는 이 결함이 안 막힌다.</b>
     * JPA의 검사는 «한 트랜잭션 안에서 읽은 뒤 쓰기 전까지» 바뀌었는지만 본다.
     * 문제가 된 «탭 두 개»는 그 창을 벗어난다 — 탭 B의 트랜잭션은 이미 A가 고친 행을
     * 읽어 오므로 그 안에서는 아무 모순이 없다.
     *
     * <p>그래서 <b>클라이언트가 들고 있던 버전을 함께 보내</b> 서버가 대조한다.
     * 이 필드는 그 대조에 쓸 «세어지는 값»을 대 준다.
     *
     * <p>{@code @Setter}가 붙은 클래스지만 이 값은 <b>직접 세팅하지 않는다.</b> JPA가 관리한다.
     */
    @Version
    @Column(name = "version")
    private Long version;

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

    /** 제출처(건설사) — 견적서 머리글의 `[제출처] 貴下` 자리. 비면 `貴下`만 찍는다. */
    @Column(name = "client_name", length = 200)
    private String clientName;

    /**
     * 견적번호(`syt-YYYYMMDDNN`) — 견적서를 처음 출력할 때 한 번 부여하고 이후 재사용한다(O-9).
     * 한 제안서에서 평형별로 여러 견적서가 나와도 번호는 하나를 공유한다.
     */
    @Column(name = "quote_no", length = 30)
    private String quoteNo;

    /**
     * 견적서 하단 조건 문구. 줄바꿈으로 구분한다. 비어 있으면 기본 4줄을 쓴다(O-9).
     */
    @Column(name = "quote_terms", length = 2000)
    private String quoteTerms;

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
    private String deletedBy;
}

