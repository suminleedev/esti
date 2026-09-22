package com.example.esti.controller;

import com.example.esti.dto.VendorCatalogUpdateRequest;
import com.example.esti.dto.VendorCatalogView;
import com.example.esti.dto.VendorOption;
import com.example.esti.dto.VendorProductPartView;
import com.example.esti.excel.VendorExcelParserFactory;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vendor-catalog")
@RequiredArgsConstructor
public class VendorCatalogController {

    /** 업로드가 받아들일 수 있는 확장자. 화면(`UploadCatalog.vue`)의 검사와 같은 목록이다. */
    private static final Set<String> UPLOAD_EXTENSIONS = Set.of("xlsx", "xls");

    private final VendorCatalogQueryService vendorCatalogQueryService;
    private final VendorCatalogCommandService vendorCatalogCommandService;
    private final CatalogImportAsyncService catalogImportAsyncService;
    private final ImportProgressStore progressStore;
    private final ObjectMapper objectMapper;
    private final VendorExcelParserFactory parserFactory;

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
        // 0) 받을 수 있는 요청인지 먼저 본다 (F-009).
        //    전에는 무엇이 오든 200 + jobId로 받고 임시파일까지 쓴 뒤 비동기 단계에서 실패했다.
        //    화면 쪽 검사만 있어서 API를 직접 부르면 그대로 통과했다.
        //    여기서 막으면 잘못된 요청이 디스크에도, 진행률 저장소에도 자국을 남기지 않는다.
        requireUploadable(vendorCode, file);

        // 1) 톰캣 임시파일이 아니라, 우리가 관리하는 폴더에 저장
        //    양식 검사(2)가 파일을 열어 봐야 해서 job보다 먼저 저장한다.
        Path savedPath;
        try {
            savedPath = saveUpload(file);
        } catch (Exception e) {
            String jobId = progressStore.createJob();
            progressStore.fail(jobId, "업로드 파일 저장 실패: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new UploadResponse(jobId));
        }

        // 2) 파일 양식이 선택한 공급사와 모순되지 않는지 — 역시 jobId를 돌려주기 «전»에 본다.
        //    거부하면 임시파일을 지워, 1)에서 남긴 자국도 없앤다.
        try {
            requireMatchingFormat(vendorCode, savedPath);
        } catch (RuntimeException e) {
            deleteQuietly(savedPath);
            throw e;
        }

        // 3) 진행률 job 생성
        String jobId = progressStore.createJob();

        try {
            // 4) 비동기 처리 시작 (MultipartFile 넘기면 안됨!)
            catalogImportAsyncService.importVendorCatalogAsync(jobId, vendorCode, savedPath);

            // 5) 프론트는 jobId로 진행률 폴링
            return ResponseEntity.ok(new UploadResponse(jobId));

        } catch (Exception e) {
            deleteQuietly(savedPath);
            progressStore.fail(jobId, "적재 시작 실패: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new UploadResponse(jobId));
        }
    }

    /** 업로드를 {@code uploads/tmp}에 저장한다. 파일명 충돌 방지 + 원본 파일명 일부 유지. */
    private Path saveUpload(MultipartFile file) throws IOException {
        Path dir = Paths.get("uploads", "tmp");
        Files.createDirectories(dir);

        String original = file.getOriginalFilename();
        String safeOriginal = (original == null) ? "upload.xlsx" : original.replaceAll("[\\\\/:*?\"<>|]", "_");
        Path savedPath = dir.resolve(UUID.randomUUID() + "_" + safeOriginal);

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, savedPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return savedPath;
    }

    /**
     * 파일이 선택한 공급사 양식과 «명백히» 모순되면 {@code 400}. 판정 규칙은
     * {@link VendorExcelParserFactory#findFormatMismatch}에 있고, 여기서는 공급사 이름으로 문구만 만든다.
     *
     * <p>판정하려고 여는 중에 실패하면(손상·암호 등) 어차피 파싱도 못 하므로 같은 자리에서 400으로 돌려준다.
     */
    private void requireMatchingFormat(String vendorCode, Path savedPath) {
        Optional<VendorExcelParserFactory.FormatMismatch> mismatch;
        try {
            mismatch = parserFactory.findFormatMismatch(vendorCode, savedPath);
        } catch (RuntimeException e) {
            throw new BadRequestException("엑셀 파일을 열 수 없습니다. 손상됐거나 암호가 걸린 파일인지 확인해 주세요.");
        }
        mismatch.ifPresent(m -> {
            Map<String, String> names = vendorCatalogQueryService.getVendorOptions().stream()
                    .collect(Collectors.toMap(o -> o.vendorCode().toUpperCase(Locale.ROOT), VendorOption::vendorName, (a, b) -> a));
            String selected = names.getOrDefault(m.selectedVendorCode().toUpperCase(Locale.ROOT), m.selectedVendorCode());
            if (m.detectedVendorCode() != null) {
                String detected = names.getOrDefault(m.detectedVendorCode().toUpperCase(Locale.ROOT), m.detectedVendorCode());
                // 이름은 DB 값이라 받침을 모른다 — 조사를 붙이지 않는 문형으로 쓴다
                throw new BadRequestException("이 파일은 " + detected + " 양식으로 보입니다 (선택한 공급사: "
                        + selected + "). 공급사를 바꿔 다시 올려 주세요.");
            }
            throw new BadRequestException("이 파일은 " + selected + " 양식으로 보이지 않습니다. "
                    + "선택한 공급사가 맞는지 확인해 주세요.");
        });
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 지우지 못한 임시파일은 uploads/tmp 정리에 맡긴다 — 거부 응답을 500으로 바꿀 일은 아니다
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
     * 공급사 선택지 조회
     * - GET /api/vendor-catalog/vendors
     *
     * <p>업로드·필터의 «공급사» 드롭다운이 쓴다. 코드와 이름이 프론트에 박혀 있던 것을 걷어냈다.
     */
    @GetMapping("/vendors")
    public ResponseEntity<List<VendorOption>> getVendors() {
        return ResponseEntity.ok(vendorCatalogQueryService.getVendorOptions());
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
     * 페이징 목록 조회 + 검색
     * GET /api/vendor-catalog/page/B?page=0&size=20&sort=id,desc&keyword=세면
     *
     * <p>{@code keyword}는 선택이다. 없거나 공백뿐이면 종전처럼 전건을 페이징한다.
     * 검색은 <b>서버에서</b> 한다 — 화면이 서버 페이징이라 클라이언트에서 거르면 지금 보이는
     * 한 페이지만 뒤지게 된다(F-015).
     */
    @GetMapping("/page/{vendorCode}")
    public ResponseEntity<Page<VendorCatalogView>> getVendorCatalogPage(
            @PathVariable String vendorCode,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                vendorCatalogQueryService.getVendorCatalogPage(vendorCode, keyword, pageable)
        );
    }

    /**
     * 전체 페이징 목록 조회 + 검색
     * GET /api/vendor-catalog/page/?page=0&size=20&sort=id,desc&keyword=세면
     */
    @GetMapping("/page/")
    public ResponseEntity<Page<VendorCatalogView>> getVendorCatalogPageAll(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                vendorCatalogQueryService.getVendorCatalogPageAll(keyword, pageable)
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
     * 업로드 요청이 말이 되는지 본다 (F-009). 아니면 {@code 400}으로 바로 돌려준다.
     *
     * <p>내용이 진짜 엑셀인지까지는 보지 않는다 — 그건 파서가 열어 보며 판단하고,
     * 실패하면 «어느 시트에서 무엇이 안 맞는지»까지 알려 준다. 여기서 거르는 것은
     * <b>열어 볼 필요조차 없는 것들</b>이다.
     */
    private void requireUploadable(String vendorCode, MultipartFile file) {
        if (!parserFactory.supports(vendorCode)) {
            throw new BadRequestException("지원하지 않는 공급사 코드입니다: " + vendorCode);
        }
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("업로드할 파일이 비어 있습니다.");
        }
        String name = file.getOriginalFilename();
        String ext = (name == null || !name.contains("."))
                ? "" : name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!UPLOAD_EXTENSIONS.contains(ext)) {
            throw new BadRequestException("엑셀(.xlsx, .xls) 파일만 업로드할 수 있습니다.");
        }
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

