package com.example.esti.service;

import com.example.esti.dto.ProposalTemplateRequest;
import com.example.esti.dto.ProposalTemplateResponse;
import com.example.esti.entity.ProductCatalog;
import com.example.esti.entity.ProposalTemplate;
import com.example.esti.entity.ProposalTemplateLine;
import com.example.esti.repository.ProductCatalogRepository;
import com.example.esti.repository.ProposalTemplateLineRepository;
import com.example.esti.repository.ProposalTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProposalTemplateService {

    private final ProposalTemplateRepository templateRepo;
    private final ProposalTemplateLineRepository lineRepo;
    private final ProductCatalogRepository catalogRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    /* CREATE */
    public ProposalTemplateResponse create(ProposalTemplateRequest req) throws Exception {
        ProposalTemplate template = new ProposalTemplate();
        template.setTemplateName(req.getTemplateName());
        template.setApartmentType(req.getApartmentType());
        template.setAreasJson(mapper.writeValueAsString(req.getAreas()));
        template.setRequiredCategoriesJson(mapper.writeValueAsString(req.getRequiredCategories()));
        templateRepo.save(template);

        // Lines 저장
        for (ProposalTemplateRequest.Line lineReq : req.getLines()) {
            ProposalTemplateLine line = new ProposalTemplateLine();
            line.setTemplate(template);

            ProductCatalog product = catalogRepo.findById(lineReq.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            line.setProduct(product);
            line.setArea(lineReq.getArea());
            line.setCategory(lineReq.getCategory());
            line.setDefaultQty(lineReq.getDefaultQty());
            line.setNote(lineReq.getNote());

            lineRepo.save(line);
        }

        return get(template.getId());
    }

    /* LIST */
    public List<ProposalTemplateResponse> list() {
        return templateRepo.findAll().stream()
                .map(t -> {
                    ProposalTemplateResponse res = new ProposalTemplateResponse();
                    res.setId(t.getId());
                    res.setTemplateName(t.getTemplateName());
                    res.setApartmentType(t.getApartmentType());
                    return res;
                })
                .collect(Collectors.toList());
    }

    /* DETAIL */
    public ProposalTemplateResponse get(Long id) {
        ProposalTemplate t = templateRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found"));

        ProposalTemplateResponse res = new ProposalTemplateResponse();
        res.setId(t.getId());
        res.setTemplateName(t.getTemplateName());
        res.setApartmentType(t.getApartmentType());

        try {
            res.setAreas(Arrays.asList(mapper.readValue(t.getAreasJson(), String[].class)));
            res.setRequiredCategories(Arrays.asList(mapper.readValue(t.getRequiredCategoriesJson(), String[].class)));
        } catch (Exception e) {
            res.setAreas(List.of());
            res.setRequiredCategories(List.of());
        }

        List<ProposalTemplateLine> lines = lineRepo.findByTemplateId(id);

        res.setLines(
                lines.stream().map(l -> {
                    ProposalTemplateResponse.Line o = new ProposalTemplateResponse.Line();
                    o.setId(l.getId());
                    o.setProductId(l.getProduct().getId());
                    o.setName(l.getProduct().getName());
                    o.setModel(l.getProduct().getModel());
                    o.setBrand(l.getProduct().getBrand());
                    o.setSpecs(l.getProduct().getSpecs());
                    o.setDescription(l.getProduct().getDescription());
                    o.setImageUrl(l.getProduct().getImageUrl());

                    o.setArea(l.getArea());
                    o.setCategory(l.getCategory());
                    o.setDefaultQty(l.getDefaultQty());
                    o.setNote(l.getNote());
                    return o;
                }).collect(Collectors.toList())
        );

        return res;
    }

    /* UPDATE */
    public ProposalTemplateResponse update(Long id, ProposalTemplateRequest req) throws Exception {
        ProposalTemplate template = templateRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found"));

        template.setTemplateName(req.getTemplateName());
        template.setApartmentType(req.getApartmentType());
        template.setAreasJson(mapper.writeValueAsString(req.getAreas()));
        template.setRequiredCategoriesJson(mapper.writeValueAsString(req.getRequiredCategories()));
        templateRepo.save(template);

        // 기존 라인 삭제 후 재생성
        lineRepo.deleteByTemplateId(id);

        for (ProposalTemplateRequest.Line lineReq : req.getLines()) {
            ProposalTemplateLine line = new ProposalTemplateLine();
            line.setTemplate(template);

            ProductCatalog product = catalogRepo.findById(lineReq.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            line.setProduct(product);
            line.setArea(lineReq.getArea());
            line.setCategory(lineReq.getCategory());
            line.setDefaultQty(lineReq.getDefaultQty());
            line.setNote(lineReq.getNote());

            lineRepo.save(line);
        }

        return get(id);
    }

    /* DELETE */
    public void delete(Long id) {
        lineRepo.deleteByTemplateId(id);
        templateRepo.deleteById(id);
    }
}

