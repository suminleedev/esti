package com.example.esti.controller;

import com.example.esti.dto.VendorCatalogView;
import com.example.esti.progress.ImportProgress;
import com.example.esti.progress.ImportProgressStore;
import com.example.esti.service.CatalogImportAsyncService;
import com.example.esti.service.CatalogImportService;
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

    private final CatalogImportService catalogImportService;
    private final VendorCatalogQueryService vendorCatalogQueryService;
    private final CatalogImportAsyncService catalogImportAsyncService;
    private final ImportProgressStore progressStore;
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
     */
    @GetMapping("/list/{vendorCode}")
    public ResponseEntity<List<VendorCatalogView>> getVendorCatalog(
            @PathVariable String vendorCode
    ) {
        return ResponseEntity.ok(
                vendorCatalogQueryService.getVendorCatalog(vendorCode)
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

}

