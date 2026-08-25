package com.example.esti.controller;

import com.example.esti.dto.ProposalRequest;
import com.example.esti.dto.ProposalResponse;
import com.example.esti.dto.QuoteTargetView;
import com.example.esti.output.QuoteTarget;
import com.example.esti.service.ProposalExcelService;
import org.springframework.http.ContentDisposition;

import java.nio.charset.StandardCharsets;
import com.example.esti.service.ProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/proposals")
@RequiredArgsConstructor
public class ProposalController {

    private final ProposalService service;
    private final ProposalExcelService excelService;

//    @PostMapping
//    public ResponseEntity<ProposalResponse> create(@RequestBody ProposalRequest req) throws Exception {
//        return ResponseEntity.ok(service.create(req));
//    }

    /**
     * 기존 저장 로직에서
     * 1. 임시저장, 2. 제출, 3. 전송 으로 수정
     * */
    /* 임시저장 */
    @PostMapping("/drafts")
    public ResponseEntity<ProposalResponse> createDraft(@RequestBody ProposalRequest req) throws Exception {
        return ResponseEntity.ok(service.createDraft(req));
    }

    /* 임시저장 수정 */
    @PutMapping("/{id}/draft")
    public ResponseEntity<ProposalResponse> updateDraft(
            @PathVariable Long id,
            @RequestBody ProposalRequest req) throws Exception {

        return ResponseEntity.ok(service.updateDraft(id, req));
    }

    /* 제출 */
    @PostMapping("/{id}/submit")
    public ResponseEntity<ProposalResponse> submit(
            @PathVariable Long id,
            @RequestBody ProposalRequest req) throws Exception {

        return ResponseEntity.ok(service.submit(id, req));
    }

    /* 신규 작성 후 제출 */
    @PostMapping("/submit")
    public ResponseEntity<ProposalResponse> submitNew(@RequestBody ProposalRequest req) throws Exception {
        return ResponseEntity.ok(service.submitNew(req));
    }

    /* 최종 발송 */
    @PostMapping("/{id}/send")
    public ResponseEntity<ProposalResponse> send(@PathVariable Long id) {
        return ResponseEntity.ok(service.send(id));
    }

    /* 견적서 복사 */
    @PostMapping("/{id}/copy")
    public ResponseEntity<ProposalResponse> copy(@PathVariable Long id) throws Exception {
        return ResponseEntity.ok(service.copyToDraft(id));
    }

    @GetMapping
    public ResponseEntity<List<ProposalResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProposalResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 전체 페이징 목록 조회
     * GET /api/proposals/page?page=0&size=10&keyword=&apartmentType=&templateFilter=
     *
     * templateFilter:
     *  - (빈값) 전체
     *  - templated : template != null
     *  - manual    : template == null
     */
    @GetMapping("/page")
    public Page<ProposalResponse> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String apartmentType,
            @RequestParam(required = false) String templateFilter,
            @RequestParam(required = false) String status
    ) {
        return service.getProposalPage(page, size, keyword, apartmentType, templateFilter, status);
    }

    /**
     * 제안서(고객 제출용) 엑셀 — 카드 그리드. 사입가·마진이 없다.
     */
    @GetMapping("/{id}/export/proposal")
    public ResponseEntity<byte[]> exportProposal(@PathVariable Long id) {
        return xlsx(excelService.exportProposal(id));
    }

    /**
     * 견적서(내부 검토용) 엑셀 — 표 + 4단 집계. <b>사입가·마진이 들어 있다.</b>
     *
     * @param kind          {@code MAIN}(본세대) / {@code ANNEX}(부속동·상가 합본)
     * @param apartmentType 본세대일 때의 평형. 비우면 본세대 전부
     */
    @GetMapping("/{id}/export/quote")
    public ResponseEntity<byte[]> exportQuote(@PathVariable Long id,
                                              @RequestParam(defaultValue = "MAIN") String kind,
                                              @RequestParam(required = false) String apartmentType) {
        QuoteTarget target = "ANNEX".equalsIgnoreCase(kind)
                ? QuoteTarget.annex()
                : QuoteTarget.main(apartmentType);
        return xlsx(excelService.exportQuote(id, target));
    }

    /** 이 제안서에서 뽑을 수 있는 견적서 대상 목록 (평형별 본세대 + 부속동·상가 합본). */
    @GetMapping("/{id}/export/quote-targets")
    public List<QuoteTargetView> quoteTargets(@PathVariable Long id) {
        return excelService.listQuoteTargets(id);
    }

    /**
     * 파일명에 한글이 들어가므로 {@link ContentDisposition}으로 RFC 5987 인코딩까지 맡긴다 —
     * 헤더에 직접 문자열을 넣으면 브라우저마다 깨진다.
     */
    private ResponseEntity<byte[]> xlsx(ProposalExcelService.ExcelDownload download) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(download.fileName(), StandardCharsets.UTF_8)
                                .build().toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(download.content());
    }
}
