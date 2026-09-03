package com.example.esti.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 제안서 저장 요청.
 *
 * 길이·범위 제약은 {@code Proposal}·{@code ProposalLine} 엔티티의 컬럼 정의와 같은 값이다.
 * 여기서 걸러 내지 않으면 DB에서 truncation/range 오류가 나고, 그건 어느 필드가 문제인지
 * 알려주지 못한 채 500으로 끝난다(F-024). <b>엔티티 컬럼을 늘리면 여기도 같이 늘려야 한다.</b>
 *
 * 필수값(현장명, 라인 최소 1건)은 상태에 따라 달라서 서비스가 판단한다 —
 * 임시저장은 비어 있어도 되고 제출은 안 된다. 여기서는 «담을 수 있는 크기»만 본다.
 */
@Getter
@Setter
public class ProposalRequest {

    // 템플릿 기반으로 만든 경우 (없으면 null)
    private Long templateId;

    @Size(max = 200, message = "현장명은 200자까지 입력할 수 있습니다.")
    private String projectName;

    @Size(max = 100, message = "담당자는 100자까지 입력할 수 있습니다.")
    private String manager;

    @Size(max = 10, message = "작성일은 yyyy-MM-dd 형식이어야 합니다.")
    private String date;

    @Size(max = 50, message = "평형은 50자까지 입력할 수 있습니다.")
    private String apartmentType;

    private Integer households;

    @Size(max = 500, message = "비고는 500자까지 입력할 수 있습니다.")
    private String note;

    @Size(max = 200, message = "제출처는 200자까지 입력할 수 있습니다.")
    private String clientName;   // 제출처(건설사) — 견적서 머리글

    @Size(max = 2000, message = "견적서 조건 문구는 2000자까지 입력할 수 있습니다.")
    private String quoteTerms;   // 견적서 조건 문구(줄바꿈 구분). 비면 기본 문구

    private List<String> areas;
    private List<String> requiredCategories;

    // DECIMAL(5,2) — 정수부 3자리까지라 999.99가 한계다.
    @DecimalMin(value = "-999.99", message = "일괄 마진율은 -999.99 ~ 999.99 사이여야 합니다.")
    @DecimalMax(value = "999.99", message = "일괄 마진율은 -999.99 ~ 999.99 사이여야 합니다.")
    private BigDecimal globalMarginRate;

    @Valid
    private List<Line> lines;

    @Getter
    @Setter
    public static class Line {
        private Long productId;

        @Size(max = 200, message = "품목명은 200자까지 입력할 수 있습니다.")
        private String productName;

        @Size(max = 20, message = "공급사 코드는 20자까지 입력할 수 있습니다.")
        private String vendorCode;

        @Size(max = 100, message = "공급사명은 100자까지 입력할 수 있습니다.")
        private String vendorName;

        @Size(max = 200, message = "공급사 품목명은 200자까지 입력할 수 있습니다.")
        private String vendorItemName;

        @Size(max = 100, message = "품번은 100자까지 입력할 수 있습니다.")
        private String mainItemCode;

        @Size(max = 100, message = "구품번은 100자까지 입력할 수 있습니다.")
        private String oldItemCode;

        // 금액 3종은 DECIMAL(15,2) — 정수부 13자리.
        @DecimalMin(value = "-9999999999999.99", message = "원가가 담을 수 있는 범위를 넘습니다.")
        @DecimalMax(value = "9999999999999.99", message = "원가가 담을 수 있는 범위를 넘습니다.")
        private BigDecimal catalogUnitPrice; // 카탈로그 단가(원가)

        private Boolean manualMargin;

        @DecimalMin(value = "-999.99", message = "마진율은 -999.99 ~ 999.99 사이여야 합니다.")
        @DecimalMax(value = "999.99", message = "마진율은 -999.99 ~ 999.99 사이여야 합니다.")
        private BigDecimal marginRate;       // 마진율

        @DecimalMin(value = "-9999999999999.99", message = "단가가 담을 수 있는 범위를 넘습니다.")
        @DecimalMax(value = "9999999999999.99", message = "단가가 담을 수 있는 범위를 넘습니다.")
        private BigDecimal unitPrice;        // 마진 적용 단가

        @DecimalMin(value = "-9999999999999.99", message = "금액이 담을 수 있는 범위를 넘습니다.")
        @DecimalMax(value = "9999999999999.99", message = "금액이 담을 수 있는 범위를 넘습니다.")
        private BigDecimal amount;           // 총금액

        @Size(max = 500, message = "라인 비고는 500자까지 입력할 수 있습니다.")
        private String remark;

        @Size(max = 500, message = "이미지 경로는 500자까지 입력할 수 있습니다.")
        private String imageUrl;

        @Size(max = 50, message = "적용 부위는 50자까지 입력할 수 있습니다.")
        private String area;

        @Size(max = 100, message = "유형(카테고리)은 100자까지 입력할 수 있습니다.")
        private String category;

        private Integer qty;

        @Size(max = 200, message = "라인 메모는 200자까지 입력할 수 있습니다.")
        private String note;

        @Size(max = 20, message = "단위는 20자까지 입력할 수 있습니다.")
        private String unit;           // 단위(SET/EA 등) — 카탈로그에서 스냅샷

        @Size(max = 50, message = "라인 평형은 50자까지 입력할 수 있습니다.")
        private String apartmentType;  // 평형 — 라인 단위(O-7)

        @Size(max = 50, message = "건물 구분은 50자까지 입력할 수 있습니다.")
        private String buildingType;   // 건물 구분(본세대/부속동/상가 등, O-5)

        @Size(max = 100, message = "소분류는 100자까지 입력할 수 있습니다.")
        private String categorySmall;  // 소분류 — 카드 '사양' 행

        private Boolean optional;      // 선택사항(유상옵션) — 카드 3열 배치 기준
    }
}
