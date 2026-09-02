package com.example.esti.repository;

import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VendorProductRepository extends JpaRepository<VendorProduct, Long> {

    Optional<VendorProduct> findByVendorAndProductCode(Vendor vendor, String productCode);

    List<VendorProduct> findAllByProductCode(String productCode);

    /** 벤더의 특정 품목 유형만 훑는다. 이누스 이미지 매칭이 SET 인덱스를 만들 때 쓴다. */
    List<VendorProduct> findAllByVendor_VendorCodeAndItemType(String vendorCode, String itemType);

    /** 벤더의 제품 전량. ASTD 이미지 동기화가 "이미 이미지가 있는가"를 미리 재는 데 쓴다. */
    List<VendorProduct> findAllByVendor_VendorCode(String vendorCode);

    List<VendorProduct> findAllByProductNameAndCategoryLargeAndCategorySmall(
            String name, String categoryLarge, String categorySmall);
}
