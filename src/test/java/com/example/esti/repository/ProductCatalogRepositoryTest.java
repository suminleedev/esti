package com.example.esti.repository;

import com.example.esti.entity.ProductCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ProductCatalogRepositoryTest {

    @Autowired
    private ProductCatalogRepository productCatalogRepository;

    @Test
    @DisplayName("상품을 저장할 수 있다")
    void testCreateProduct() {
        ProductCatalog product = new ProductCatalog();
        product.setName("세면대");
        product.setSpec("600mm");
        product.setBasePrice(new BigDecimal("120000"));
        product.setDescription("화이트 세면대");
        product.setImageUrl("http://example.com/sink.png");

        ProductCatalog saved = productCatalogRepository.save(product);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("세면대");
    }

    @Test
    @DisplayName("상품을 조회할 수 있다")
    void testReadProduct() {
        ProductCatalog product = new ProductCatalog();
        product.setName("변기");
        product.setSpec("양변기");
        product.setBasePrice(new BigDecimal("200000"));
        product.setDescription("고급 변기");
        product.setImageUrl("http://example.com/toilet.png");
        productCatalogRepository.save(product);

        Optional<ProductCatalog> found = productCatalogRepository.findById(product.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("변기");
    }

    @Test
    @DisplayName("상품 정보를 수정할 수 있다")
    void testUpdateProduct() {
        ProductCatalog product = new ProductCatalog();
        product.setName("욕조");
        product.setSpec("1200mm");
        product.setBasePrice(new BigDecimal("500000"));
        product.setDescription("기본 욕조");
        product.setImageUrl("http://example.com/bathtub.png");
        ProductCatalog saved = productCatalogRepository.save(product);

        saved.setBasePrice(new BigDecimal("550000"));
        ProductCatalog updated = productCatalogRepository.save(saved);

        assertThat(updated.getBasePrice()).isEqualTo(new BigDecimal("550000"));
    }

    @Test
    @DisplayName("상품을 삭제할 수 있다")
    void testDeleteProduct() {
        ProductCatalog product = new ProductCatalog();
        product.setName("거울");
        product.setSpec("800x600");
        product.setBasePrice(new BigDecimal("80000"));
        product.setDescription("욕실 거울");
        product.setImageUrl("http://example.com/mirror.png");
        ProductCatalog saved = productCatalogRepository.save(product);

        productCatalogRepository.delete(saved);
        Optional<ProductCatalog> deleted = productCatalogRepository.findById(saved.getId());

        assertThat(deleted).isEmpty();
    }
}
