package com.example.esti.service;

import com.example.esti.dto.VendorCatalogView;
import com.example.esti.entity.ProductCatalog;
import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.repository.VendorItemPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VendorCatalogQueryService {

    private final VendorItemPriceRepository vendorItemPriceRepository;

    // 기존 : 전체 리스트
    public List<VendorCatalogView> getVendorCatalog(String vendorCode) {
        List<VendorItemPrice> list = vendorItemPriceRepository.findByVendor_VendorCode(vendorCode);

        return list.stream()
                .map(vip -> {
                    Vendor v = vip.getVendor();
                    ProductCatalog c = vip.getCatalog(); // 필드명이 다르면 getProductCatalog() 등으로 변경

                    return new VendorCatalogView(
                            c.getId(),
                            v.getVendorCode(),
                            v.getVendorName(),
                            c.getCategoryLarge(),
                            c.getCategorySmall(),
                            c.getName(),
                            vip.getMainItemCode(),
                            vip.getOldItemCode(),
                            vip.getVendorItemName(),
                            vip.getRemark(),
                            vip.getUnitPrice(),
                            vip.getCatalog().getImageUrl()
                    );
                })
                // 정렬은 여기서 해주면 깔끔
                .sorted(Comparator
                        .comparing(VendorCatalogView::categoryLarge, Comparator.nullsLast(String::compareTo))
                        .thenComparing(VendorCatalogView::categorySmall, Comparator.nullsLast(String::compareTo))
                        .thenComparing(VendorCatalogView::productName, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
    }

    // 신규: 페이징
    @Transactional(readOnly = true)
    public Page<VendorCatalogView> getVendorCatalogPage(String vendorCode, Pageable pageable) {
        return vendorItemPriceRepository
                .findByVendor_VendorCode(vendorCode, pageable)
                .map(VendorCatalogView::from);
    }
}

