package com.example.esti.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProposalTemplateRequest {

    private String templateName;
    private String apartmentType;
    private List<String> areas;
    private List<String> requiredCategories;

    private List<Line> lines;

    @Getter
    @Setter
    public static class Line {
        private Long id;          // update 시 사용
        private Long productId;
        private String area;
        private String category;
        private Integer defaultQty;
        private String note;
    }
}

