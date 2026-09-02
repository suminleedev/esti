package com.example.esti.dto;

import com.example.esti.entity.VendorItemPrice;
import com.example.esti.entity.VendorProduct;

import java.math.BigDecimal;

public record VendorCatalogView(
        Long vendorItemPriceId,  // 목록 행 식별자 (한 행 = 가격 라인)
        Long vendorProductId,
        String vendorCode,
        String vendorName,
        String categoryLarge,
        String categorySmall,
        String productName,      // 세트명
        String mainItemCode,     // 대표 신품번 (VendorItemPrice.mainItemCode)
        String oldItemCode,      // 구품번
        String vendorItemName,   // 공급사 기준 대표 품목명
        String remark,           // 비고 (VendorItemPrice.remark)
        BigDecimal unitPrice,    // 공급사 세트 단가
        String priceBasis,       // 가격 기준(출처 시트). 수전금구처럼 같은 품번이 시트별로 다른 가격일 때 어느 시트 값인지 (F-1)
        String imageUrl,         // VendorProduct.imageUrl
        String description,      // VendorProduct.description (원본 품번/부가 설명. 예: 수전부속 원본 B열)
        String specs,            // VendorProduct.specs (규격. 비고 분류 정책(C-2)의 규격성 비고 + 향후 크롤링 규격)
        String unit,             // VendorProduct.unit (견적서 C열 단위. 미설정 행은 기본값 SET으로 접힌다, O-1b)
        String setSummary        // 구성 요약 한 줄. 같은 품번의 여러 세트가 각각 행이 되므로 목록에서 이걸로 가른다(G-1)
) {
    public static VendorCatalogView from(VendorItemPrice vip) {
        return new VendorCatalogView(
                vip.getId(),
                vip.getVendorProduct().getId(),
                vip.getVendor().getVendorCode(),
                vip.getVendor().getVendorName(),
                vip.getVendorProduct().getCategoryLarge(),
                vip.getVendorProduct().getCategorySmall(),
                vip.getVendorProduct().getProductName(),
                vip.getMainItemCode(),
                vip.getOldItemCode(),
                vip.getVendorItemName(),
                vip.getRemark(),
                vip.getUnitPrice(),
                vip.getPriceBasis(),
                vip.getVendorProduct().getImageUrl(),
                vip.getVendorProduct().getDescription(),
                vip.getVendorProduct().getSpecs(),
                VendorProduct.unitOrDefault(vip.getVendorProduct().getUnit()),
                vip.getSetSummary()
        );
    }
}
