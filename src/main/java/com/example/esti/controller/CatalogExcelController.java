package com.example.esti.controller;

import com.example.esti.service.CatalogImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogExcelController {

    private final CatalogImportService catalogImportService;

    @PostMapping("/upload-excel/{vendorCode}")
    public ResponseEntity<?> uploadCatalogExcel(
            @PathVariable String vendorCode,
            @RequestParam("file") MultipartFile file) {

        catalogImportService.importVendorCatalog(vendorCode, file);
        return ResponseEntity.ok("카탈로그 엑셀 업로드/반영 완료");
    }
}

