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

    /**
     * 품번이 없는 제품을 이름으로 찾는다 — 재적재 멱등 매칭용.
     *
     * <p>품번이 있는 제품은 품번으로 식별하므로 여기 섞이면 안 된다. 이름이 같은 부속이
     * 코드만 다르게 여럿 있는데(예: 세트마다 등장하는 "시트"·"도기"), 코드 있는 쪽까지 후보에
     * 넣으면 서로 다른 제품이 한 행으로 병합된다.
     *
     * <p>분류는 조건에 넣지 않는다. <b>분류가 바뀌는 것이 바로 이 조회가 상대하는 상황</b>이기
     * 때문이다 — 호출부가 결과를 받아 분류로 좁힐지 말지를 정한다.
     */
    List<VendorProduct> findAllByVendorAndProductCodeIsNullAndProductName(
            Vendor vendor, String productName);
}
