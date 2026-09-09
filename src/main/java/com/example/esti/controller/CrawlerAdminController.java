package com.example.esti.controller;

import com.example.esti.crawler.service.CrawlerRunStatus;
import com.example.esti.crawler.service.ImageSyncReport;
import com.example.esti.crawler.service.ProductImageSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 이미지 크롤러 수동 실행·상태 조회 (관리자용).
 *
 * <p><b>{@code demo} 프로파일에서는 이 컨트롤러 자체가 뜨지 않는다(D-4).</b> 자원 문제가 아니라
 * <b>외부 사이트로 트래픽이 나가는</b> 문제라서다 — POST 한 번이 수십 분짜리 크롤 배치를 발사하고,
 * 「관리자가 수동으로 한 번 돌린다」는 전제로 정해 둔 요청 간격 설계가 공개 배포본에서는 성립하지 않는다.
 * 인증으로 가리는 것보다 <b>빈을 만들지 않는 쪽</b>이 확실하다.
 */
@Profile("!demo")
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
