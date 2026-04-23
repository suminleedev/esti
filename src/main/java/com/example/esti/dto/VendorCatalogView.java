package com.example.esti.dto;

import com.example.esti.entity.VendorItemPrice;

import java.math.BigDecimal;

public record VendorCatalogView(
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
        String imageUrl          // VendorProduct.imageUrl
) {
    public static VendorCatalogView from(VendorItemPrice vip) {
        return new VendorCatalogView(
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
                vip.getVendorProduct().getImageUrl()
        );
    }
}
