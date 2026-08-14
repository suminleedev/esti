package com.example.esti.repository;

import com.example.esti.entity.ProductCatalog;
import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.entity.VendorProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VendorItemPriceRepository extends JpaRepository<VendorItemPrice, Long> {

    // 제안서 작성 화면 전체 목록 전용.
    // VendorCatalogView.from()이 vendorProduct/vendor를 행마다 건드리므로, LAZY 프록시를 그대로 두면
    // 행 수만큼 select가 나간다(N+1). fetch join으로 한 번에 로딩한다.
    // 두 연관 모두 nullable=false라 inner join이어도 행이 누락되지 않는다.
    // 페이징 메서드에는 같은 방식을 쓰지 않는다(fetch join + Pageable은 전건 메모리 로딩이 된다).
    // order by id: 서비스의 분류/제품명 정렬은 stable sort라 동률 행의 순서가 입력 순서에 좌우된다.
    // 기존 findAll()이 우연히 보장하던 id 순서를 명시해 목록 순서를 종전과 동일하게 유지한다.
    @Query("select vip from VendorItemPrice vip "
            + "join fetch vip.vendorProduct "
            + "join fetch vip.vendor "
            + "order by vip.id")
    List<VendorItemPrice> findAllForCatalogView();

    // 공급사 + VendorProduct + 제안서 품번 기준으로 upsert
    Optional<VendorItemPrice> findByVendorAndVendorProductAndProposalItemCode(
            Vendor vendor, VendorProduct product, String proposalItemCode);

    // 가격 기준(시트)까지 포함 — 같은 품번이 시트별로 다른 가격(대표품목)일 때 분리 upsert
    Optional<VendorItemPrice> findByVendorAndVendorProductAndProposalItemCodeAndPriceBasis(
            Vendor vendor, VendorProduct product, String proposalItemCode, String priceBasis);

    // 가격 기준이 없는(공유 부속 등, D13) 경우의 upsert
    Optional<VendorItemPrice> findByVendorAndVendorProductAndProposalItemCodeAndPriceBasisIsNull(
            Vendor vendor, VendorProduct product, String proposalItemCode);

    // 제안서 품번이 없는(신품번 없음) 항목의 멱등 upsert용
    Optional<VendorItemPrice> findFirstByVendorAndVendorProduct(Vendor vendor, VendorProduct product);

    // VendorItemPrice.vendor.vendorCode 로 찾아오는 메서드
    List<VendorItemPrice> findByVendor_VendorCode(String vendorCode);

    // 페이징 처리하여 반환
    Page<VendorItemPrice> findByVendor_VendorCode(String vendorCode, Pageable pageable);

    // (선택) 검색까지 하고 싶으면
    // Page<VendorItemPrice> findByVendor_VendorCodeAndProposalItemCodeContaining(
    //        String vendorCode, String keyword, Pageable pageable);

    // 크롤링
    Optional<VendorItemPrice> findByVendor_VendorCodeAndProposalItemCode(String vendorCode, String proposalItemCode);

    // 크롤링 : ASTD
    List<VendorItemPrice> findAllByVendor_VendorCode(String vendorCode);
}

