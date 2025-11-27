package com.example.esti.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProposalTemplateResponse {

    private Long id;
    private String templateName;
    private String apartmentType;

    private List<String> areas;
    private List<String> requiredCategories;

    private List<Line> lines;

    @Getter
    @Setter
    public static class Line {
        private Long id;
        private Long productId;

        private String name;
        private String model;
        private String brand;
        private String specs;
        private String description;
        private String imageUrl;

        private String area;
        private String category;
        private Integer defaultQty;
        private String note;
    }
}

