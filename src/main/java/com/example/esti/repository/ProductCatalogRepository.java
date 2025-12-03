package com.example.esti.repository;

import com.example.esti.entity.ProductCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductCatalogRepository extends JpaRepository<ProductCatalog, Long> {

    // 제품명으로 검색
    List<ProductCatalog> findByNameContaining(String keyword);

    // 규격으로 검색
    List<ProductCatalog> findBySpecsContaining(String specs);

    // 특정 가격 이상 제품 검색
    List<ProductCatalog> findByBasePriceGreaterThanEqual(java.math.BigDecimal price);

    // 특정 가격 이하 제품 검색
    List<ProductCatalog> findByBasePriceLessThanEqual(java.math.BigDecimal price);

    Optional<ProductCatalog> findByMasterCode(String masterCode);

    Optional<ProductCatalog> findByNameAndCategoryLargeAndCategorySmall(
            String name, String categoryLarge, String categorySmall);
}
