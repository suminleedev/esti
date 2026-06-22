package com.example.esti.repository;

import com.example.esti.entity.VendorProduct;
import com.example.esti.entity.VendorProductRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VendorProductRelationRepository extends JpaRepository<VendorProductRelation, Long> {

    /** (대표품목, 부속품, 관계유형) 기준 멱등 upsert 조회 */
    Optional<VendorProductRelation> findBySourceProductAndTargetProductAndRelationType(
            VendorProduct sourceProduct, VendorProduct targetProduct, String relationType);

    List<VendorProductRelation> findAllBySourceProduct(VendorProduct sourceProduct);
}
