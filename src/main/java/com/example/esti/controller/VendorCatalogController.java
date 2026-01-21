package com.example.esti.controller;

import com.example.esti.dto.VendorCatalogView;
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

import java.util.List;

@RestController
@RequestMapping("/api/vendor-catalog")
@RequiredArgsConstructor
public class VendorCatalogController {

    private final CatalogImportService catalogImportService;
    private final VendorCatalogQueryService vendorCatalogQueryService;

    /**
     * 공급사별 카탈로그 엑셀 업로드(기존 업로드 API)
     * 예:
     *  - POST /api/vendor-catalog/upload-excel/A
     *  - POST /api/vendor-catalog/upload-excel/B
     *  - form-data: file = 엑셀파일
     */
    @PostMapping("/upload-excel/{vendorCode}")
    public ResponseEntity<String> uploadVendorExcel(
            @PathVariable String vendorCode,
            @RequestParam("file") MultipartFile file
    ) {
        catalogImportService.importVendorCatalog(vendorCode, file);
        return ResponseEntity.ok("[" + vendorCode + "] 공급사 카탈로그 엑셀 업로드/반영 완료");
    }

    // 공급사 카탈로그 목록 조회
    @GetMapping("/list/{vendorCode}")
    public ResponseEntity<List<VendorCatalogView>> getVendorCatalog(
            @PathVariable String vendorCode
    ) {
        return ResponseEntity.ok(
                vendorCatalogQueryService.getVendorCatalog(vendorCode)
        );
    }

    // GET /api/vendor-catalog/page/B?page=0&size=20&sort=id,desc
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

