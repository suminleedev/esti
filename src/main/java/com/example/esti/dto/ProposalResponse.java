package com.example.esti.dto;

import lombok.Getter;
import lombok.Setter;

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
        private Integer qty;
        private String note;
    }
}

