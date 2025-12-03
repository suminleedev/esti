package com.example.esti.controller;

import com.example.esti.service.CatalogImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/vendor-catalog")
@RequiredArgsConstructor
public class VendorCatalogController {

    private final CatalogImportService catalogImportService;

    /**
     * 공급사별 카탈로그 엑셀 업로드
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
}

