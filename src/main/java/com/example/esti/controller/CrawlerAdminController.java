package com.example.esti.controller;

import com.example.esti.crawler.service.CrawlerRunStatus;
import com.example.esti.crawler.service.ImageSyncReport;
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
     * POST /api/admin/crawler/INUS/images?dryRun=true
     *
     * <p>수집·매칭·반영 건수를 돌려준다. 예전에는 무조건 "완료" 문자열이라
     * 한 건도 저장되지 않아도 성공으로 보였다.
     *
     * @param dryRun 참이면 내려받지도 저장하지도 않고 매칭 결과만 본다.
     *               기존 이미지를 덮어쓰므로 먼저 무엇이 바뀔지 확인하는 용도다
     */
    @PostMapping("/{maker}/images")
    public ResponseEntity<ImageSyncReport> syncImages(
            @PathVariable String maker,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam(defaultValue = "false") boolean force
    ) throws Exception {
        return ResponseEntity.ok(productImageSyncService.syncByMaker(maker.toUpperCase(), dryRun, force));
    }

    /**
     * 실행 상태 (C-3).
     * 예) GET /api/admin/crawler/{maker}/status
     *
     * <p>지금 돌아도 되는지(실행 중·쿨다운)와 <b>돌 필요가 있는지</b>(마지막 업로드 이후
     * 크롤링했는가)를 함께 낸다. 막는 대신 알려 주는 자리라, 판단은 사람이 한다.
     */
    @GetMapping("/{maker}/status")
    public ResponseEntity<CrawlerRunStatus> status(@PathVariable String maker) {
        return ResponseEntity.ok(productImageSyncService.statusOf(maker.toUpperCase()));
    }
}
