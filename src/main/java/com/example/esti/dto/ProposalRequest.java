package com.example.esti.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
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
 * 여기에 «금액이 음수가 되는 길»도 함께 막는다(F-025). 서버가 단가·금액을 재계산하므로
 * 실제 입력은 <b>원가·마진율·수량</b> 셋이고, 그 셋이 음수 총액으로 가는 문이다.
 *
 * 필수값(현장명, 라인 최소 1건)은 상태에 따라 달라서 서비스가 판단한다 —
 * 임시저장은 비어 있어도 되고 제출은 안 된다. 여기서 보는 것은 «담을 수 있는 크기»와 «말이 되는 범위»다.
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

    @Min(value = 1, message = "세대수는 1 이상이어야 합니다.")
    private Integer households;

    @Size(max = 500, message = "비고는 500자까지 입력할 수 있습니다.")
    private String note;

    @Size(max = 200, message = "제출처는 200자까지 입력할 수 있습니다.")
    private String clientName;   // 제출처(건설사) — 견적서 머리글

    @Size(max = 2000, message = "견적서 조건 문구는 2000자까지 입력할 수 있습니다.")
    private String quoteTerms;   // 견적서 조건 문구(줄바꿈 구분). 비면 기본 문구

    private List<String> areas;
    private List<String> requiredCategories;

    // 상한은 컬럼(DECIMAL(5,2))이 담을 수 있는 999.99.
    // 하한이 -100인 이유는 계산식이다 — 단가 = 원가 × (100 + 마진율) / 100 이라
    // -100 아래로 가면 단가가 음수가 된다(F-025). -100은 "공짜", 그 아래는 "돈을 얹어 준다"는 뜻이다.
    // 원가보다 싸게 파는 것(-100 ~ 0)까지 막지는 않는다 — 그건 계산 오류가 아니라 영업 판단이다.
    @DecimalMin(value = "-100", message = "일괄 마진율은 -100 ~ 999.99 사이여야 합니다.")
    @DecimalMax(value = "999.99", message = "일괄 마진율은 -100 ~ 999.99 사이여야 합니다.")
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

        // 원가는 음수가 될 수 없다 — 여기가 음수면 단가·금액이 통째로 음수가 된다(F-025).
        // 상한은 컬럼(DECIMAL(15,2))이 담을 수 있는 값.
        @DecimalMin(value = "0", message = "원가는 0 이상이어야 합니다.")
        @DecimalMax(value = "9999999999999.99", message = "원가가 담을 수 있는 범위를 넘습니다.")
        private BigDecimal catalogUnitPrice; // 카탈로그 단가(원가)

        private Boolean manualMargin;

        @DecimalMin(value = "-100", message = "마진율은 -100 ~ 999.99 사이여야 합니다.")
        @DecimalMax(value = "999.99", message = "마진율은 -100 ~ 999.99 사이여야 합니다.")
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

        // 화면도 qty > 0일 때만 담기를 허용한다(ProposalView의 lineValid). 서버도 같은 선을 지킨다.
        // 0이면 금액이 0이 되고 음수면 총액이 음수가 된다 — 둘 다 제안서에 들어갈 값이 아니다.
        @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
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
