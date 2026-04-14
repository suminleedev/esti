package com.example.esti.controller;

import com.example.esti.crawler.service.ProductImageSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/crawler")
@RequiredArgsConstructor
public class CrawlerAdminController {

    private final ProductImageSyncService productImageSyncService;

    /**
     * 예)
     * POST /api/admin/crawler/ASTD/images
     * POST /api/admin/crawler/INUS/images
     */
    @PostMapping("/{maker}/images")
    public ResponseEntity<String> syncImages(@PathVariable String maker) throws Exception {
        productImageSyncService.syncByMaker(maker.toUpperCase());
        return ResponseEntity.ok(maker.toUpperCase() + " 이미지 동기화 완료");
    }
}
