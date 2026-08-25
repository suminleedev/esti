package com.example.esti.service;

import com.example.esti.dto.VendorCatalogUpdateRequest;
import com.example.esti.dto.VendorCatalogView;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.entity.VendorProduct;
import com.example.esti.repository.VendorItemPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VendorCatalogCommandService {

    private final VendorItemPriceRepository vendorItemPriceRepository;

    /**
     * 카탈로그 행(가격 라인) 수정.
     * - 품번/비고/단가는 VendorItemPrice에 반영
     * - 분류/세트명/이미지/설명은 VendorProduct에 반영
     *   (같은 상품을 공유하는 다른 가격 라인에도 함께 적용된다)
     */
    @Transactional
    public VendorCatalogView update(Long vendorItemPriceId, VendorCatalogUpdateRequest request) {
        VendorItemPrice vip = vendorItemPriceRepository.findById(vendorItemPriceId)
                .orElseThrow(() -> new RuntimeException("카탈로그 항목을 찾을 수 없음. id=" + vendorItemPriceId));

        vip.setMainItemCode(request.mainItemCode());
        vip.setRemark(request.remark());
        // 단가는 NOT NULL 컬럼 — 값이 온 경우에만 반영
        if (request.unitPrice() != null) {
            vip.setUnitPrice(request.unitPrice());
        }

        VendorProduct product = vip.getVendorProduct();
        product.setCategoryLarge(request.categoryLarge());
        product.setCategorySmall(request.categorySmall());
        product.setProductName(request.productName());
        product.setImageUrl(request.imageUrl());
        product.setDescription(request.description());
        // 단위는 비우는 것을 허용하지 않는다 — 견적서 C열이 빈칸이 되므로 기본값 SET으로 접는다(O-1b)
        product.setUnit(VendorProduct.unitOrDefault(request.unit()));

        return VendorCatalogView.from(vip);
    }

    /**
     * 카탈로그 행(가격 라인) 삭제. 상품 마스터(VendorProduct)는 유지된다.
     */
    @Transactional
    public void delete(Long vendorItemPriceId) {
        if (!vendorItemPriceRepository.existsById(vendorItemPriceId)) {
            throw new RuntimeException("카탈로그 항목을 찾을 수 없음. id=" + vendorItemPriceId);
        }
        vendorItemPriceRepository.deleteById(vendorItemPriceId);
    }
}
