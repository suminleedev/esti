package com.example.esti.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ProposalTemplateRequest {

    private String templateName;
    private String apartmentType;
    private List<String> areas;
    private List<String> requiredCategories;
    private BigDecimal globalMarginRate;

    private List<Line> lines;

    @Getter
    @Setter
    public static class Line {
        private Long id;          // update 시 사용
        private Long productId;

        private String specs;
        private String description;
        private String imageUrl;

        private String vendorCode;
        private String vendorName;
        private String vendorItemName;
        private String mainItemCode;
        private String oldItemCode;
        private BigDecimal unitPrice;
        private String remark;

        private String area;
        private String category;
        private Integer defaultQty;
        private String note;

        private String unit;   // 단위(SET/EA 등). 평형·건물구분은 현장별 값이라 템플릿에 두지 않는다
    }
}

