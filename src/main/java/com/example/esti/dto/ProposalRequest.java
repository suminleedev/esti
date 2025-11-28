package com.example.esti.dto;

import lombok.Getter;
import lombok.Setter;

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

    private List<Line> lines;

    @Getter
    @Setter
    public static class Line {
        private Long productId;
        private String area;
        private String category;
        private Integer qty;
        private String note;
        // 필요시 unitPrice 도 추가 가능
    }
}
