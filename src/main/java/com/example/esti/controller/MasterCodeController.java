package com.example.esti.controller;

import com.example.esti.dto.MasterCodeReorderRequest;
import com.example.esti.dto.MasterCodeRequest;
import com.example.esti.dto.MasterCodeResponse;
import com.example.esti.entity.MasterCodeType;
import com.example.esti.service.MasterCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/master-codes")
@RequiredArgsConstructor
public class MasterCodeController {

    private final MasterCodeService service;

    /** 드롭다운용 — 종류별 활성 라벨만. 화면 진입 시 한 번에 세 종류를 받는다. */
    @GetMapping("/options")
    public ResponseEntity<Map<MasterCodeType, List<String>>> options() {
        return ResponseEntity.ok(service.activeLabelsByType());
    }

    /** 설정 화면용 — 비활성 포함 전건. */
    @GetMapping
    public ResponseEntity<List<MasterCodeResponse>> list(@RequestParam MasterCodeType type) {
        return ResponseEntity.ok(service.list(type));
    }

    @PostMapping
    public ResponseEntity<MasterCodeResponse> create(@RequestBody MasterCodeRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    /** 이름 변경 · 숨김/복원. */
    @PutMapping("/{id}")
    public ResponseEntity<MasterCodeResponse> update(@PathVariable Long id, @RequestBody MasterCodeRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    /** soft delete — 드롭다운에서만 숨긴다. 이미 저장된 제안서의 값은 그대로다(M-6). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/reorder")
    public ResponseEntity<List<MasterCodeResponse>> reorder(@RequestBody MasterCodeReorderRequest req) {
        return ResponseEntity.ok(service.reorder(req));
    }
}
