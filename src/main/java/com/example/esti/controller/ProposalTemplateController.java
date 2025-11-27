package com.example.esti.controller;

import com.example.esti.dto.ProposalTemplateRequest;
import com.example.esti.dto.ProposalTemplateResponse;
import com.example.esti.service.ProposalTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/proposal-templates")
@RequiredArgsConstructor
public class ProposalTemplateController {

    private final ProposalTemplateService service;

    @PostMapping
    public ResponseEntity<ProposalTemplateResponse> create(@RequestBody ProposalTemplateRequest req) throws Exception {
        return ResponseEntity.ok(service.create(req));
    }

    @GetMapping
    public ResponseEntity<List<ProposalTemplateResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProposalTemplateResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProposalTemplateResponse> update(
            @PathVariable Long id,
            @RequestBody ProposalTemplateRequest req
    ) throws Exception {
        return ResponseEntity.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

