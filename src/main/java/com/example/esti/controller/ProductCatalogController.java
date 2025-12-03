package com.example.esti.controller;

import com.example.esti.entity.ProductCatalog;
import com.example.esti.service.ProductCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class ProductCatalogController {

    private final ProductCatalogService service;

    // ===== 목록 조회 =====
    @GetMapping("/list")
    public ResponseEntity<List<ProductCatalog>> getAllProducts() {
        return ResponseEntity.ok(service.findAll());
    }

    // ===== 단일 조회 =====
    @GetMapping("/{id}")
    public ResponseEntity<ProductCatalog> getProduct(@PathVariable Long id) {
        ProductCatalog catalog = service.findById(id); // 없으면 내부에서 예외 던지는 형태
        return ResponseEntity.ok(catalog);
    }

    // ===== 등록 (엑셀 말고 단건 등록용) =====
    @PostMapping
    public ResponseEntity<ProductCatalog> createProduct(@RequestBody ProductCatalog product) {
        ProductCatalog saved = service.create(product);
        return ResponseEntity.ok(saved);
    }

    // ===== 수정 =====
    @PutMapping("/{id}")
    public ResponseEntity<ProductCatalog> updateProduct(
            @PathVariable Long id,
            @RequestBody ProductCatalog updated
    ) {
        ProductCatalog saved = service.update(id, updated);
        return ResponseEntity.ok(saved);
    }

    // ===== 삭제 =====
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
