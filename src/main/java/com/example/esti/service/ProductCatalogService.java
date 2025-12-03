package com.example.esti.service;

import com.example.esti.entity.ProductCatalog;
import com.example.esti.repository.ProductCatalogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductCatalogService {

    private final ProductCatalogRepository repository;

    public ProductCatalogService(ProductCatalogRepository repository) {
        this.repository = repository;
    }

    // ===== 목록 조회 =====
    public List<ProductCatalog> findAll() {
        return repository.findAll();
    }

    // ===== 단일 조회 (없으면 예외) =====
    public ProductCatalog findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("카탈로그를 찾을 수 없음. id=" + id));
    }

    // ===== 등록 =====
    public ProductCatalog create(ProductCatalog product) {
        // masterCode, categoryLarge/Small 등을 여기서 후처리해도 됨
        return repository.save(product);
    }

    // ===== 수정 =====
    public ProductCatalog update(Long id, ProductCatalog update) {
        ProductCatalog catalog = findById(id); // 없으면 예외

        catalog.setName(update.getName());
        catalog.setModel(update.getModel());
        catalog.setBrand(update.getBrand());
        catalog.setSpecs(update.getSpecs());
        catalog.setDescription(update.getDescription());
        catalog.setImageUrl(update.getImageUrl());

        catalog.setCategoryLarge(update.getCategoryLarge());
        catalog.setCategorySmall(update.getCategorySmall());
        catalog.setItemType(update.getItemType());

        // basePrice는 필요 시 사용
        catalog.setBasePrice(update.getBasePrice());

        // masterCode를 수동으로 바꾸게 할지 말지는 정책에 따라 결정
        if (update.getMasterCode() != null) {
            catalog.setMasterCode(update.getMasterCode());
        }

        return repository.save(catalog);
    }

    // ===== 삭제 =====
    public void delete(Long id) {
        // 존재 여부 체크 후 없으면 예외 던지는 패턴
        if (!repository.existsById(id)) {
            throw new RuntimeException("삭제할 카탈로그가 없습니다. id=" + id);
        }
        repository.deleteById(id);
    }
}
