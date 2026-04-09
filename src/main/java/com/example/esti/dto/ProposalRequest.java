package com.example.esti.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ProposalRequest {

    // 템플릿 기반으로 만든 경우 (없으면 null)
    private Long templateId;

    private String projectName;
    private String manager;
    private String date;
    private String apartmentType;
    private Integer households;
    private String note;

    private List<String> areas;
    private List<String> requiredCategories;

    private BigDecimal globalMarginRate;
    private List<Line> lines;

    @Getter
    @Setter
    public static class Line {
        private Long productId;

        private String productName;
        private String vendorCode;
        private String vendorName;
        private String vendorItemName;
        private String mainItemCode;
        private String oldItemCode;

        private BigDecimal catalogUnitPrice; // 카탈로그 단가(원가)
        private Boolean manualMargin;
        private BigDecimal marginRate;       // 마진율
        private BigDecimal unitPrice;        // 마진 적용 단가
        private BigDecimal amount;           // 총금액

        private String remark;
        private String imageUrl;

        private String area;
        private String category;
        private Integer qty;
        private String note;
    }
}
