package com.example.esti.controller;

import com.example.esti.dto.ProposalRequest;
import com.example.esti.dto.ProposalResponse;
import com.example.esti.service.ProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/proposals")
@RequiredArgsConstructor
public class ProposalController {

    private final ProposalService service;

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
            @RequestParam(required = false) String templateFilter
    ) {
        return service.getProposalPage(page, size, keyword, apartmentType, templateFilter);
    }

}
