package com.example.esti.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ProposalResponse {

    private Long id;
    private Long templateId;

    private String projectName;
    private String manager;
    private String date;
    private String apartmentType;
    private Integer households;
    private String note;
    private String status; // "DRAFT" / "SUBMITTED" / "SENT"

    private List<String> areas;
    private List<String> requiredCategories;

    private BigDecimal globalMarginRate;
    private List<Line> lines;

    @Getter
    @Setter
    public static class Line {
        private Long id;
        private Long productId;

        private String productName;
        private String vendorCode;
        private String vendorName;
        private String vendorItemName;
        private String mainItemCode;
        private String oldItemCode;

        private BigDecimal catalogUnitPrice;
        private Boolean manualMargin;
        private BigDecimal marginRate;
        private BigDecimal unitPrice;
        private BigDecimal amount;

        private String remark;
        private String imageUrl;

        private String area;
        private String category;
        private Integer qty;
        private String note;
    }
}

