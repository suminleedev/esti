package com.example.esti.controller;

import com.example.esti.entity.ProductCatalog;
import com.example.esti.service.ProductCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/catalog")
public class ProductCatalogController {

    private final ProductCatalogService service;

    public ProductCatalogController(ProductCatalogService service) {
        this.service = service;
    }

    // ===== 엑셀 업로드 =====
    @PostMapping("/import")
    public ResponseEntity<String> importExcel(@RequestParam("file") MultipartFile file) {
        try {
            service.importFromExcel(file);
            return ResponseEntity.ok("엑셀 데이터 업로드 성공");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("엑셀 업로드 실패: " + e.getMessage());
        }
    }

    // ===== 목록 조회 =====
    @GetMapping("/list")
    public ResponseEntity<List<ProductCatalog>> getAllProducts() {
        return ResponseEntity.ok(service.findAll());
    }

    // ===== 단일 조회 =====
    @GetMapping("/{id}")
    public ResponseEntity<ProductCatalog> getProduct(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ===== 등록 (엑셀 말고 단건 등록용) =====
    @PostMapping
    public ResponseEntity<ProductCatalog> createProduct(@RequestBody ProductCatalog product) {
        return ResponseEntity.ok(service.save(product));
    }

    // ===== 수정 =====
    @PutMapping("/{id}")
    public ResponseEntity<ProductCatalog> updateProduct(
            @PathVariable Long id,
            @RequestBody ProductCatalog updated
    ) {
        return service.findById(id)
                .map(existing -> {
                    existing.setName(updated.getName());
                    existing.setSpecs(updated.getSpecs());
                    existing.setBasePrice(updated.getBasePrice());
                    existing.setDescription(updated.getDescription());
                    existing.setImageUrl(updated.getImageUrl());
                    return ResponseEntity.ok(service.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===== 삭제 =====
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        if (service.existsById(id)) {
            service.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
