package com.example.esti.service;

import com.example.esti.dto.VendorCatalogView;
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

    // 제안서 작성 화면 : 전체 리스트
    @Transactional(readOnly = true)
    public List<VendorCatalogView> getVendorCatalogAll() {
        return vendorItemPriceRepository.findAll().stream()
                .map(VendorCatalogView::from)
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

    // 전체 페이지 목록 조회
    @Transactional(readOnly = true)
    public Page<VendorCatalogView> getVendorCatalogPageAll(Pageable pageable) {
        return vendorItemPriceRepository.findAll(pageable)
                .map(VendorCatalogView::from);
    }
}

