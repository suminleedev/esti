package com.example.esti.service;

import com.example.esti.dto.ProposalRequest;
import com.example.esti.dto.ProposalResponse;
import com.example.esti.entity.ProductCatalog;
import com.example.esti.entity.Proposal;
import com.example.esti.entity.ProposalLine;
import com.example.esti.entity.ProposalTemplate;
import com.example.esti.repository.ProductCatalogRepository;
import com.example.esti.repository.ProposalLineRepository;
import com.example.esti.repository.ProposalRepository;
import com.example.esti.repository.ProposalTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProposalService {

    private final ProposalRepository proposalRepo;
    private final ProposalLineRepository lineRepo;
    private final ProposalTemplateRepository templateRepo;
    private final ProductCatalogRepository catalogRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    /* CREATE */
    public ProposalResponse create(ProposalRequest req) throws Exception {
        Proposal p = new Proposal();

        if (req.getTemplateId() != null) {
            ProposalTemplate template = templateRepo.findById(req.getTemplateId())
                    .orElseThrow(() -> new RuntimeException("Template not found"));
            p.setTemplate(template);
        }

        p.setProjectName(req.getProjectName());
        p.setManager(req.getManager());
        p.setDate(req.getDate());
        p.setApartmentType(req.getApartmentType());
        p.setHouseholds(req.getHouseholds());
        p.setNote(req.getNote());

        p.setAreasJson(mapper.writeValueAsString(req.getAreas()));
        p.setRequiredCategoriesJson(mapper.writeValueAsString(req.getRequiredCategories()));

        proposalRepo.save(p);

        // lines
        for (ProposalRequest.Line lineReq : req.getLines()) {
            ProposalLine line = new ProposalLine();
            line.setProposal(p);

            ProductCatalog product = catalogRepo.findById(lineReq.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + lineReq.getProductId()));

            line.setProduct(product);
            line.setArea(lineReq.getArea());
            line.setCategory(lineReq.getCategory());
            line.setQty(lineReq.getQty());
            line.setNote(lineReq.getNote());

            lineRepo.save(line);
        }

        return get(p.getId());
    }

    /* LIST: 간단 요약용 */
    public List<ProposalResponse> list() {
        return proposalRepo.findAll().stream().map(p -> {
            ProposalResponse res = new ProposalResponse();
            res.setId(p.getId());
            res.setTemplateId(p.getTemplate() != null ? p.getTemplate().getId() : null);
            res.setProjectName(p.getProjectName());
            res.setManager(p.getManager());
            res.setDate(p.getDate());
            res.setApartmentType(p.getApartmentType());
            res.setHouseholds(p.getHouseholds());
            // 상세 areas/lines 는 생략 (필요하면 확장)
            return res;
        }).collect(Collectors.toList());
    }

    /* DETAIL */
    public ProposalResponse get(Long id) {
        Proposal p = proposalRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Proposal not found"));

        ProposalResponse res = new ProposalResponse();
        res.setId(p.getId());
        res.setTemplateId(p.getTemplate() != null ? p.getTemplate().getId() : null);

        res.setProjectName(p.getProjectName());
        res.setManager(p.getManager());
        res.setDate(p.getDate());
        res.setApartmentType(p.getApartmentType());
        res.setHouseholds(p.getHouseholds());
        res.setNote(p.getNote());

        try {
            res.setAreas(Arrays.asList(mapper.readValue(p.getAreasJson(), String[].class)));
            res.setRequiredCategories(Arrays.asList(mapper.readValue(p.getRequiredCategoriesJson(), String[].class)));
        } catch (Exception e) {
            res.setAreas(List.of());
            res.setRequiredCategories(List.of());
        }

        List<ProposalLine> lines = lineRepo.findByProposalId(id);

        res.setLines(lines.stream().map(l -> {
            ProposalResponse.Line o = new ProposalResponse.Line();
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
            o.setQty(l.getQty());
            o.setNote(l.getNote());
            return o;
        }).collect(Collectors.toList()));

        return res;
    }

    /* DELETE */
    public void delete(Long id) {
        lineRepo.deleteByProposalId(id);
        proposalRepo.deleteById(id);
    }
}

