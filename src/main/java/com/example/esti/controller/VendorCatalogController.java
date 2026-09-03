package com.example.esti.controller;

import com.example.esti.dto.VendorCatalogUpdateRequest;
import com.example.esti.dto.VendorCatalogView;
import com.example.esti.dto.VendorProductPartView;
import com.example.esti.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.example.esti.progress.ImportProgress;
import com.example.esti.progress.ImportProgressStore;
import com.example.esti.service.CatalogImportAsyncService;
import com.example.esti.service.VendorCatalogCommandService;
import com.example.esti.service.VendorCatalogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vendor-catalog")
@RequiredArgsConstructor
public class VendorCatalogController {

    private final VendorCatalogQueryService vendorCatalogQueryService;
    private final VendorCatalogCommandService vendorCatalogCommandService;
    private final CatalogImportAsyncService catalogImportAsyncService;
    private final ImportProgressStore progressStore;
    private final ObjectMapper objectMapper;
    /**
     * 공급사별 카탈로그 엑셀 업로드 (비동기 + 진행률 job)
     * 예:
     *  - POST /api/vendor-catalog/upload-excel/A
     *  - POST /api/vendor-catalog/upload-excel/B
     *  - form-data: file = 엑셀파일
     * 응답: { "jobId": "..." }
     */
    @PostMapping("/upload-excel/{vendorCode}")
    public ResponseEntity<UploadResponse> uploadVendorExcel(
            @PathVariable String vendorCode,
            @RequestParam("file") MultipartFile file
    ) {
        // 1) 진행률 job 생성
        String jobId = progressStore.createJob();

        // 2) 톰캣 임시파일이 아니라, 우리가 관리하는 폴더에 저장
        //    (원하는 경로로 변경 가능: 예 "uploads/tmp")
        Path dir = Paths.get("uploads", "tmp");

        try {
            Files.createDirectories(dir);

            // 파일명 충돌 방지 + 원본 파일명 일부 유지
            String original = file.getOriginalFilename();
            String safeOriginal = (original == null) ? "upload.xlsx" : original.replaceAll("[\\\\/:*?\"<>|]", "_");
            String storedName = UUID.randomUUID() + "_" + safeOriginal;

            Path savedPath = dir.resolve(storedName);

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, savedPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // 3) 비동기 처리 시작 (MultipartFile 넘기면 안됨!)
            catalogImportAsyncService.importVendorCatalogAsync(jobId, vendorCode, savedPath);

            // 4) 프론트는 jobId로 진행률 폴링
            return ResponseEntity.ok(new UploadResponse(jobId));

        } catch (Exception e) {
            progressStore.fail(jobId, "업로드 파일 저장 실패: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new UploadResponse(jobId));
        }
    }

    public record UploadResponse(String jobId) {}

    /**
     * 업로드 진행률 조회
     * - GET /api/vendor-catalog/upload-progress/{jobId}
     */
    @GetMapping("/upload-progress/{jobId}")
    public ResponseEntity<ImportProgress> getProgress(@PathVariable String jobId) {
        return ResponseEntity.ok(progressStore.get(jobId));
    }

    /**
     * 공급사 카탈로그 목록 조회 (기존 list)
     * 제안서 작성 화면 카탈로그 목록
     */
    @GetMapping("/list")
    public ResponseEntity<List<VendorCatalogView>> getVendorCatalogAll() {
        return ResponseEntity.ok(
                vendorCatalogQueryService.getVendorCatalogAll()
        );
    }

    /**
     * 페이징 목록 조회
     * GET /api/vendor-catalog/page/B?page=0&size=20&sort=id,desc
     */
    @GetMapping("/page/{vendorCode}")
    public ResponseEntity<Page<VendorCatalogView>> getVendorCatalogPage(
            @PathVariable String vendorCode,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                vendorCatalogQueryService.getVendorCatalogPage(vendorCode, pageable)
        );
    }

    /**
     * 전체 페이징 목록 조회
     * GET /api/vendor-catalog/page/?page=0&size=20&sort=id,desc
     */
    @GetMapping("/page/")
    public ResponseEntity<Page<VendorCatalogView>> getVendorCatalogPageAll(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                vendorCatalogQueryService.getVendorCatalogPageAll(pageable)
        );
    }

    /**
     * 카탈로그 행(가격 라인)의 부속 구성 조회 (B-2 드릴다운)
     * GET /api/vendor-catalog/{vendorItemPriceId}/parts
     *
     * <p>목록에 부속을 미리 실으면 행마다 관계 조회가 나가므로(N+1), 화면에서 행을 펼친 시점에만 부른다.
     * 부속이 없으면 200 + 빈 배열, 가격 라인 자체가 없으면 404 — 화면이 "부속 없음"과 "조회 실패"를
     * 구분해 표시해야 한다.
     */
    @GetMapping("/{vendorItemPriceId}/parts")
    public ResponseEntity<List<VendorProductPartView>> getVendorCatalogParts(
            @PathVariable Long vendorItemPriceId
    ) {
        return vendorCatalogQueryService.getParts(vendorItemPriceId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 카탈로그 행(가격 라인) 수정 — <b>전체 교체</b>다.
     * PUT /api/vendor-catalog/{vendorItemPriceId}
     *
     * 본문을 바로 DTO로 받지 않고 {@link ObjectNode}로 한 번 받는 이유는, 빠진 키를 잡기 위해서다(F-017).
     * DTO로 바로 바인딩하면 «안 보낸 필드»와 «null로 보낸 필드»가 똑같이 null이 되어,
     * 단가만 담아 보내도 나머지가 조용히 지워졌다. 여기서 키 존재를 먼저 확인해 400으로 돌려준다.
     */
    @PutMapping("/{vendorItemPriceId}")
    public ResponseEntity<VendorCatalogView> updateVendorCatalog(
            @PathVariable Long vendorItemPriceId,
            @RequestBody ObjectNode body
    ) {
        List<String> missing = VendorCatalogUpdateRequest.requiredKeys().stream()
                .filter(key -> !body.has(key))
                .toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException(
                    "카탈로그 수정은 전체 교체입니다. 빠진 항목: " + String.join(", ", missing));
        }

        VendorCatalogUpdateRequest request = objectMapper.convertValue(body, VendorCatalogUpdateRequest.class);
        return ResponseEntity.ok(
                vendorCatalogCommandService.update(vendorItemPriceId, request)
        );
    }

    /**
     * 카탈로그 행(가격 라인) 삭제
     * DELETE /api/vendor-catalog/{vendorItemPriceId}
     */
    @DeleteMapping("/{vendorItemPriceId}")
    public ResponseEntity<Void> deleteVendorCatalog(@PathVariable Long vendorItemPriceId) {
        vendorCatalogCommandService.delete(vendorItemPriceId);
        return ResponseEntity.noContent().build();
    }

}

