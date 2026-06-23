package com.example.esti.repository;

import com.example.esti.entity.ProductCatalog;
import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.entity.VendorProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VendorItemPriceRepository extends JpaRepository<VendorItemPrice, Long> {

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

