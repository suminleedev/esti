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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ProposalService {

    private final ProposalRepository proposalRepo;
    private final ProposalLineRepository lineRepo;
    private final ProposalTemplateRepository templateRepo;
    private final ProductCatalogRepository catalogRepo;
    private final ObjectMapper mapper = new ObjectMapper();

//    /* CREATE */
//    public ProposalResponse create(ProposalRequest req) throws Exception {
//        Proposal p = new Proposal();
//
//        if (req.getTemplateId() != null) {
//            ProposalTemplate template = templateRepo.findById(req.getTemplateId())
//                    .orElseThrow(() -> new RuntimeException("Template not found"));
//            p.setTemplate(template);
//        }
//
//        p.setProjectName(req.getProjectName());
//        p.setManager(req.getManager());
//        p.setDate(req.getDate());
//        p.setApartmentType(req.getApartmentType());
//        p.setHouseholds(req.getHouseholds());
//        p.setNote(req.getNote());
//
//        p.setAreasJson(mapper.writeValueAsString(req.getAreas()));
//        p.setRequiredCategoriesJson(mapper.writeValueAsString(req.getRequiredCategories()));
//
//        proposalRepo.save(p);
//
//        // lines
//        for (ProposalRequest.Line lineReq : req.getLines()) {
//            ProposalLine line = new ProposalLine();
//            line.setProposal(p);
//
//            ProductCatalog product = catalogRepo.findById(lineReq.getProductId())
//                    .orElseThrow(() -> new RuntimeException("Product not found: " + lineReq.getProductId()));
//
//            line.setProduct(product);
//            line.setArea(lineReq.getArea());
//            line.setCategory(lineReq.getCategory());
//            line.setQty(lineReq.getQty());
//            line.setNote(lineReq.getNote());
//
//            lineRepo.save(line);
//        }
//
//        return get(p.getId());
//    }

    /**
     * 기존 저장 로직에서
     * 1. 임시저장, 2. 제출, 3. 전송 으로 수정
     * */
    /* 임시저장 생성 */
    public ProposalResponse createDraft(ProposalRequest req) throws Exception {
        Proposal p = new Proposal();
        p.setStatus(Proposal.Status.DRAFT);

        applyBasicFields(p, req);
        proposalRepo.save(p);

        saveLines(p, req); // req.getLines()가 null이면 그냥 스킵하도록 만들면 더 좋음
        return get(p.getId());
    }

    /* 임시저장 수정 */
    public ProposalResponse updateDraft(Long id, ProposalRequest req) throws Exception {
        Proposal p = proposalRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Proposal not found"));

        if (p.getStatus() != Proposal.Status.DRAFT) {
            throw new RuntimeException("DRAFT 상태에서만 임시저장 수정 가능합니다.");
        }

        applyBasicFields(p, req);

        lineRepo.deleteByProposalId(id);
        saveLines(p, req);

        return get(id);
    }

    /* 제출 */
    public ProposalResponse submit(Long id, ProposalRequest req) throws Exception {
        Proposal p = proposalRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Proposal not found"));

        if (p.getStatus() == Proposal.Status.SENT) {
            throw new RuntimeException("발송 완료된 최종 견적서는 수정할 수 없습니다.");
        }

        validateForSubmit(req); // 강검증

        applyBasicFields(p, req);

        lineRepo.deleteByProposalId(id);
        saveLines(p, req);

        p.setStatus(Proposal.Status.SUBMITTED);
        proposalRepo.save(p);

        return get(id);
    }

    /* 신규 작성 후 제출 */
    public ProposalResponse submitNew(ProposalRequest req) throws Exception {
        validateForSubmit(req);

        Proposal p = new Proposal();
        p.setStatus(Proposal.Status.DRAFT);

        applyBasicFields(p, req);
        proposalRepo.save(p);

        saveLines(p, req);

        p.setStatus(Proposal.Status.SUBMITTED);
        proposalRepo.save(p);

        return get(p.getId());
    }

    /* 최종 발송 확정 */
    public ProposalResponse send(Long id) {
        Proposal p = proposalRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Proposal not found"));

        if (p.getStatus() != Proposal.Status.SUBMITTED) {
            throw new RuntimeException("SUBMITTED 상태에서만 발송 확정할 수 있습니다.");
        }

        p.setStatus(Proposal.Status.SENT);
        proposalRepo.save(p);

        return get(id);
    }

    /* 견적서 복사 : SENT 상태 수정 필요시 복제 (원본 보존, 새 제안서 id 발급) */
    public ProposalResponse copyToDraft(Long id) throws Exception {
        Proposal src = proposalRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Proposal not found"));

        Proposal p = new Proposal();
        p.setStatus(Proposal.Status.DRAFT);

        // src 값 복사
        p.setTemplate(src.getTemplate());
        p.setProjectName(src.getProjectName());
        p.setManager(src.getManager());
        p.setDate(src.getDate());
        p.setApartmentType(src.getApartmentType());
        p.setHouseholds(src.getHouseholds());
        p.setNote(src.getNote());
        p.setAreasJson(src.getAreasJson());
        p.setRequiredCategoriesJson(src.getRequiredCategoriesJson());

        proposalRepo.save(p);

        // lines 복사
        List<ProposalLine> srcLines = lineRepo.findByProposalId(id);
        for (ProposalLine l : srcLines) {
            ProposalLine nl = new ProposalLine();
            nl.setProposal(p);
            nl.setProduct(l.getProduct());
            nl.setArea(l.getArea());
            nl.setCategory(l.getCategory());
            nl.setQty(l.getQty());
            nl.setNote(l.getNote());
            nl.setUnitPrice(l.getUnitPrice());
            lineRepo.save(nl);
        }

        return get(p.getId());
    }

    private void applyBasicFields(Proposal p, ProposalRequest req) throws Exception {
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
    }

    private void saveLines(Proposal p, ProposalRequest req) {

        for (ProposalRequest.Line lineReq : req.getLines()) {
            ProposalLine line = new ProposalLine();
            line.setProposal(p);

            ProductCatalog product = catalogRepo.findById(lineReq.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            line.setProduct(product);
            line.setArea(lineReq.getArea());
            line.setCategory(lineReq.getCategory());
            line.setQty(lineReq.getQty());
            line.setNote(lineReq.getNote());

            lineRepo.save(line);
        }
    }

    /* 제출 전 강검증 */
    private void validateForSubmit(ProposalRequest req) {

        if (req.getProjectName() == null || req.getProjectName().isBlank()) {
            throw new RuntimeException("현장명은 필수입니다.");
        }

        if (req.getLines() == null || req.getLines().isEmpty()) {
            throw new RuntimeException("라인이 최소 1개 이상 필요합니다.");
        }
    }

    /* LIST: 간단 요약용 */
    @Transactional(readOnly = true)
    public List<ProposalResponse> list() {
//        return proposalRepo.findAll().stream().map(p -> {
        return proposalRepo.findByDeletedAtIsNull().stream().map(p -> {
            ProposalResponse res = new ProposalResponse();
            res.setId(p.getId());
            res.setTemplateId(p.getTemplate() != null ? p.getTemplate().getId() : null);
            res.setProjectName(p.getProjectName());
            res.setManager(p.getManager());
            res.setDate(p.getDate());
            res.setApartmentType(p.getApartmentType());
            res.setHouseholds(p.getHouseholds());
            res.setStatus(p.getStatus().name());
            // 상세 areas/lines 는 생략 (필요하면 확장)
            return res;
        }).collect(Collectors.toList());
    }

    /* DETAIL */
    @Transactional(readOnly = true)
    public ProposalResponse get(Long id) {
//        Proposal p = proposalRepo.findById(id)
        Proposal p = proposalRepo.findByIdAndDeletedAtIsNull(id)
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
        res.setStatus(p.getStatus().name());

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
        Proposal p = proposalRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Proposal not found"));

        // SENT만 금지, SUBMITTED는 허용
        if (p.getStatus() == Proposal.Status.SENT) {
            throw new RuntimeException("발송 완료된 최종 견적서는 삭제할 수 없습니다.");
        }
        // 더 보수적으로 하려면 SUBMITTED도 막기:
        // if (p.getStatus() != Proposal.Status.DRAFT) { ... }

        // 기존 : 일괄 삭제 처리
//        lineRepo.deleteByProposalId(id);
//        proposalRepo.deleteById(id);

        // DRAFT: 하드삭제
        if (p.getStatus() == Proposal.Status.DRAFT) {
            lineRepo.deleteByProposalId(id);
            proposalRepo.deleteById(id);
            return;
        }

        // SUBMITTED: 소프트삭제
        if (p.getStatus() == Proposal.Status.SUBMITTED) {
            p.setDeletedAt(LocalDateTime.now());
            // p.setDeletedBy(currentUserId);  // 선택
            proposalRepo.save(p);
            return;
        }

        throw new RuntimeException("삭제할 수 없는 상태입니다: " + p.getStatus());


    }
}

