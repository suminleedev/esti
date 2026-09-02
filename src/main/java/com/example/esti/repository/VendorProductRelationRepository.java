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

    /** 세트 축 도입 후의 멱등 upsert 조회 — 유일키가 (source, target, type, setHash)다 (G-1). */
    Optional<VendorProductRelation> findBySourceProductAndTargetProductAndRelationTypeAndSetHash(
            VendorProduct sourceProduct, VendorProduct targetProduct, String relationType, String setHash);

    List<VendorProductRelation> findAllBySourceProduct(VendorProduct sourceProduct);

    /**
     * 한 세트의 부속만. {@code setHash}가 null인 행(세트 축 도입 전에 적재된 것)은
     * JPA가 {@code = ?}로 만들어 매칭되지 않으므로 {@link #findAllBySourceProductAndSetHashIsNull}을 쓴다.
     */
    List<VendorProductRelation> findAllBySourceProductAndSetHash(VendorProduct sourceProduct, String setHash);

    /** 세트 축 도입 전에 적재된 관계(하위호환 경로). */
    List<VendorProductRelation> findAllBySourceProductAndSetHashIsNull(VendorProduct sourceProduct);

    /** 재적재 시 그 세트의 낡은 관계만 걷어낸다 (Task 3 흡수). 다른 세트는 건드리지 않는다. */
    void deleteAllBySourceProductAndSetHash(VendorProduct sourceProduct, String setHash);

    /** 세트 축 도입 전에 적재된 관계를 걷어낸다 — 재적재 한 번으로 낡은 상태가 정리된다. */
    void deleteAllBySourceProductAndSetHashIsNull(VendorProduct sourceProduct);
}
