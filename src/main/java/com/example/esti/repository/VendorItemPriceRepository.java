package com.example.esti.repository;

import com.example.esti.dto.VendorCatalogView;
import com.example.esti.entity.ProductCatalog;
import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorItemPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VendorItemPriceRepository extends JpaRepository<VendorItemPrice, Long> {

    // 공급사 + 카탈로그 + 제안서 품번 기준으로 upsert
    Optional<VendorItemPrice> findByVendorAndCatalogAndProposalItemCode(
            Vendor vendor, ProductCatalog catalog, String proposalItemCode);

    // VendorItemPrice.vendor.vendorCode 로 찾아오는 메서드
    List<VendorItemPrice> findByVendor_VendorCode(String vendorCode);
}

