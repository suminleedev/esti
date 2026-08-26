package com.example.esti.service;

import com.example.esti.exception.BadRequestException;
import com.example.esti.exception.InvalidStateException;
import com.example.esti.exception.NotFoundException;
import com.example.esti.dto.ProposalRequest;
import com.example.esti.dto.ProposalResponse;
import com.example.esti.entity.Proposal;
import com.example.esti.entity.ProposalLine;
import com.example.esti.entity.VendorProduct;
import com.example.esti.entity.ProposalTemplate;
import com.example.esti.repository.ProposalLineRepository;
import com.example.esti.repository.ProposalRepository;
import com.example.esti.repository.ProposalTemplateRepository;
import com.example.esti.repository.spec.ProposalSpecs;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final ObjectMapper mapper;

    /** 마진율 백분율 계산용 상수 */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(0);

//    /* CREATE */
//    public ProposalResponse create(ProposalRequest req) throws Exception {
//        Proposal p = new Proposal();
//
//        if (req.getTemplateId() != null) {
//            ProposalTemplate template = templateRepo.findById(req.getTemplateId())
//                    .orElseThrow(() -> new NotFoundException("Template not found"));
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
        Proposal p = proposalRepo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Proposal not found"));

        if (p.getStatus() != Proposal.Status.DRAFT) {
            throw new InvalidStateException("DRAFT 상태에서만 임시저장 수정 가능합니다.");
        }

        applyBasicFields(p, req);

        lineRepo.deleteByProposalId(id);
        saveLines(p, req);

        return get(id);
    }

    /* 제출 */
    public ProposalResponse submit(Long id, ProposalRequest req) throws Exception {
        Proposal p = proposalRepo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Proposal not found"));

        if (p.getStatus() == Proposal.Status.SENT) {
            throw new InvalidStateException("발송 완료된 최종 견적서는 수정할 수 없습니다.");
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
        Proposal p = proposalRepo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Proposal not found"));

        if (p.getStatus() != Proposal.Status.SUBMITTED) {
            throw new InvalidStateException("SUBMITTED 상태에서만 발송 확정할 수 있습니다.");
        }

        p.setStatus(Proposal.Status.SENT);
        proposalRepo.save(p);

        return get(id);
    }

    /* 견적서 복사 : SENT 상태 수정 필요시 복제 (원본 보존, 새 제안서 id 발급) */
    public ProposalResponse copyToDraft(Long id) throws Exception {
        Proposal src = proposalRepo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Proposal not found"));

        Proposal p = new Proposal();
        p.setStatus(Proposal.Status.DRAFT);

        // src 값 복사
        p.setTemplate(src.getTemplate());
        p.setProjectName(src.getProjectName() + " - 복사본");
        p.setManager(src.getManager());
        p.setDate(src.getDate());
        p.setApartmentType(src.getApartmentType());
        p.setHouseholds(src.getHouseholds());
        p.setNote(src.getNote());
        p.setClientName(src.getClientName());
        p.setQuoteTerms(src.getQuoteTerms());
        // quoteNo는 복사하지 않는다 — 복사본은 별개 문서라 새 견적번호를 받아야 한다
        p.setAreasJson(src.getAreasJson());
        p.setRequiredCategoriesJson(src.getRequiredCategoriesJson());
        p.setGlobalMarginRate(src.getGlobalMarginRate());

        proposalRepo.save(p);

        // lines 복사
        int copyOrder = 0;
        List<ProposalLine> srcLines = lineRepo.findByProposalIdOrderBySortOrderAscIdAsc(id);
        for (ProposalLine l : srcLines) {
            ProposalLine nl = new ProposalLine();
            nl.setProposal(p);
            nl.setProductId(l.getProductId());
            nl.setProductName(l.getProductName());
            nl.setVendorCode(l.getVendorCode());
            nl.setVendorName(l.getVendorName());
            nl.setVendorItemName(l.getVendorItemName());
            nl.setMainItemCode(l.getMainItemCode());
            nl.setOldItemCode(l.getOldItemCode());

            // 가격 관련
            nl.setCatalogUnitPrice(l.getCatalogUnitPrice()); // 카탈로그 원가
            nl.setManualMargin(l.getManualMargin() != null ? l.getManualMargin() : false); // 마진율 수동 설정 여부
            nl.setMarginRate(l.getMarginRate()); // 마진율
            nl.setUnitPrice(l.getUnitPrice()); // 최종 제안 단가
            nl.setAmount(l.getAmount()); // 총금액

            nl.setRemark(l.getRemark());
            nl.setImageUrl(l.getImageUrl());
            nl.setArea(l.getArea());
            nl.setCategory(l.getCategory());
            nl.setQty(l.getQty());
            nl.setNote(l.getNote());
            nl.setUnit(l.getUnit());
            nl.setApartmentType(l.getApartmentType());
            nl.setBuildingType(l.getBuildingType());
            nl.setCategorySmall(l.getCategorySmall());
            nl.setOptional(Boolean.TRUE.equals(l.getOptional()));
            // 원본이 legacy(sortOrder=null)여도 조회 순서(=id 순)대로 번호를 새로 매긴다
            nl.setSortOrder(copyOrder++);
            lineRepo.save(nl);
        }

        return get(p.getId());
    }

    private void applyBasicFields(Proposal p, ProposalRequest req) throws Exception {
        if (req.getTemplateId() != null) {
            ProposalTemplate template = templateRepo.findById(req.getTemplateId())
                    .orElseThrow(() -> new NotFoundException("Template not found"));
            p.setTemplate(template);
        } else {
            p.setTemplate(null);
        }

        p.setProjectName(req.getProjectName());
        p.setManager(req.getManager());
        p.setDate(req.getDate());
        p.setApartmentType(req.getApartmentType());
        p.setHouseholds(req.getHouseholds());
        p.setNote(req.getNote());
        p.setClientName(req.getClientName());
        p.setQuoteTerms(req.getQuoteTerms());
        p.setGlobalMarginRate(req.getGlobalMarginRate());

        p.setAreasJson(mapper.writeValueAsString(req.getAreas()));
        p.setRequiredCategoriesJson(mapper.writeValueAsString(req.getRequiredCategories()));
    }

    private void saveLines(Proposal p, ProposalRequest req) {

        if (req.getLines() == null || req.getLines().isEmpty()) return;

        // 요청 배열의 순서가 곧 표시 순서다. 인덱스를 sortOrder(0-based)로 저장한다.
        int sortOrder = 0;

        for (ProposalRequest.Line lineReq : req.getLines()) {
            ProposalLine line = new ProposalLine();
            line.setProposal(p);
            line.setSortOrder(sortOrder++);

            line.setProductId(lineReq.getProductId());
            line.setProductName(lineReq.getProductName());
            line.setVendorCode(lineReq.getVendorCode());
            line.setVendorName(lineReq.getVendorName());
            line.setVendorItemName(lineReq.getVendorItemName());
            line.setMainItemCode(lineReq.getMainItemCode());
            line.setOldItemCode(lineReq.getOldItemCode());
            // 가격 관련
            line.setCatalogUnitPrice(lineReq.getCatalogUnitPrice()); // 카탈로그 기준 단가
            line.setManualMargin(
                    lineReq.getManualMargin() != null ? lineReq.getManualMargin() : false
            );                                                       // 마진율 수동 설정 여부

            // 단가·금액은 클라이언트 값을 신뢰하지 않고 서버에서 재계산한다.
            // 적용 마진율: manualMargin이면 라인 개별 마진율, 아니면 제안서 일괄 마진율.
            BigDecimal appliedRate = resolveMarginRate(line.getManualMargin(),
                    lineReq.getMarginRate(), req.getGlobalMarginRate());
            BigDecimal unitPrice = calculateUnitPrice(lineReq.getCatalogUnitPrice(), appliedRate);

            line.setMarginRate(appliedRate);                         // 적용 마진율
            line.setUnitPrice(unitPrice);                            // 최종 제안 단가
            line.setAmount(calculateAmount(unitPrice, lineReq.getQty())); // 총금액

            line.setRemark(lineReq.getRemark());
            line.setImageUrl(lineReq.getImageUrl());

            line.setArea(lineReq.getArea());
            line.setCategory(lineReq.getCategory());
            line.setQty(lineReq.getQty());
            line.setNote(lineReq.getNote());

            // 단위는 카탈로그에서 담을 때 스냅샷된다. 값이 없으면 기본값 SET(O-1b).
            line.setUnit(VendorProduct.unitOrDefault(lineReq.getUnit()));
            line.setApartmentType(lineReq.getApartmentType());
            line.setBuildingType(lineReq.getBuildingType());
            line.setCategorySmall(lineReq.getCategorySmall());
            line.setOptional(Boolean.TRUE.equals(lineReq.getOptional()));

            lineRepo.save(line);
        }
    }
    /** 적용 마진율 결정
     *  manualMargin=true 이면 라인 개별 마진율, 아니면 제안서 일괄 마진율.
     *  둘 다 없으면 0%로 본다(프론트 toNumber(null)=0 과 동일).
     */
    private BigDecimal resolveMarginRate(Boolean manualMargin, BigDecimal lineRate, BigDecimal globalRate) {
        BigDecimal rate = Boolean.TRUE.equals(manualMargin) ? lineRate : globalRate;
        return rate != null ? rate : BigDecimal.ZERO;
    }

    /** 제안 단가 계산: 카탈로그 단가 x (1 + 마진율/100)
     *  원 단위 반올림(HALF_UP). 프론트 Proposal.vue recalculateLine() 과 동일 정책.
     */
    private BigDecimal calculateUnitPrice(BigDecimal catalogUnitPrice, BigDecimal marginRate) {
        BigDecimal base = catalogUnitPrice != null ? catalogUnitPrice : BigDecimal.ZERO;
        BigDecimal rate = marginRate != null ? marginRate : BigDecimal.ZERO;

        return base.multiply(HUNDRED.add(rate))
                .divide(HUNDRED, 0, RoundingMode.HALF_UP);
    }

    /** 금액 계산
     *  두 자리수 반올림
     */
    private BigDecimal calculateAmount(BigDecimal unitPrice, Integer qty) {
        BigDecimal price = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        int quantity = qty != null ? qty : 0;

        // return price.multiply(BigDecimal.valueOf(quantity));
        return price.multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /* 제출 전 강검증 */
    private void validateForSubmit(ProposalRequest req) {

        if (req.getProjectName() == null || req.getProjectName().isBlank()) {
            throw new BadRequestException("현장명은 필수입니다.");
        }

        if (req.getLines() == null || req.getLines().isEmpty()) {
            throw new BadRequestException("라인이 최소 1개 이상 필요합니다.");
        }
    }

    /* LIST: 간단 요약용 */
    @Transactional(readOnly = true)
    public List<ProposalResponse> list() {
//        return proposalRepo.findAll().stream().map(p -> {
        return proposalRepo.findByDeletedAtIsNull().stream().map(
                this::toResponse
        ).collect(Collectors.toList());
    }

    /* DETAIL */
    @Transactional(readOnly = true)
    public ProposalResponse get(Long id) {
//        Proposal p = proposalRepo.findById(id)
        Proposal p = proposalRepo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Proposal not found"));

        ProposalResponse res = toResponse(p);

        try {
            res.setAreas(Arrays.asList(mapper.readValue(p.getAreasJson(), String[].class)));
            res.setRequiredCategories(Arrays.asList(mapper.readValue(p.getRequiredCategoriesJson(), String[].class)));
        } catch (Exception e) {
            res.setAreas(List.of());
            res.setRequiredCategories(List.of());
        }

        List<ProposalLine> lines = lineRepo.findByProposalIdOrderBySortOrderAscIdAsc(id);

        res.setLines(lines.stream().map(l -> {
            ProposalResponse.Line o = new ProposalResponse.Line();
            o.setId(l.getId());

            o.setProductId(l.getProductId());
            o.setProductName(l.getProductName());
            o.setVendorCode(l.getVendorCode());
            o.setVendorName(l.getVendorName());
            o.setVendorItemName(l.getVendorItemName());
            o.setMainItemCode(l.getMainItemCode());
            o.setOldItemCode(l.getOldItemCode());

            o.setCatalogUnitPrice(l.getCatalogUnitPrice());
            o.setManualMargin(l.getManualMargin());
            o.setMarginRate(l.getMarginRate());
            o.setUnitPrice(l.getUnitPrice());
            o.setAmount(l.getAmount());

            o.setRemark(l.getRemark());
            o.setImageUrl(l.getImageUrl());

            o.setArea(l.getArea());
            o.setCategory(l.getCategory());
            o.setQty(l.getQty());
            o.setNote(l.getNote());
            o.setSortOrder(l.getSortOrder());
            o.setUnit(l.getUnit());
            o.setApartmentType(l.getApartmentType());
            o.setBuildingType(l.getBuildingType());
            o.setCategorySmall(l.getCategorySmall());
            o.setOptional(l.getOptional());
            return o;
        }).collect(Collectors.toList()));

        return res;
    }

    /* DELETE */
    public void delete(Long id) {
        Proposal p = proposalRepo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Proposal not found"));

        // SENT만 금지, SUBMITTED는 허용
        if (p.getStatus() == Proposal.Status.SENT) {
            throw new InvalidStateException("발송 완료된 최종 견적서는 삭제할 수 없습니다.");
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

        throw new InvalidStateException("삭제할 수 없는 상태입니다: " + p.getStatus());


    }

    /* 페이지 조회 */
    public Page<ProposalResponse> getProposalPage(
            int page, int size,
            String keyword,
            String apartmentType,
            String templateFilter,
            String status) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "id")
        );

        Specification<Proposal> spec = ProposalSpecs.search(keyword, apartmentType, templateFilter, status)
                .and(ProposalSpecs.notDeleted());

        return proposalRepo.findAll(spec, pageable).map(this::toResponse);
    }

    /* response dto 반환 */
    private ProposalResponse toResponse(Proposal p) {

        ProposalResponse res = new ProposalResponse();

        res.setId(p.getId());
        res.setTemplateId(p.getTemplate() != null ? p.getTemplate().getId() : null);
        res.setProjectName(p.getProjectName());
        res.setManager(p.getManager());
        res.setDate(p.getDate());
        res.setApartmentType(p.getApartmentType());
        res.setHouseholds(p.getHouseholds());
        res.setNote(p.getNote());
        res.setClientName(p.getClientName());
        res.setQuoteNo(p.getQuoteNo());
        res.setQuoteTerms(p.getQuoteTerms());
        res.setStatus(p.getStatus().name());
        res.setGlobalMarginRate(p.getGlobalMarginRate());
        // 상세 areas/lines 는 생략 (필요하면 확장)
        return res;
    }
}
