package com.example.esti.service;

import com.example.esti.dto.VendorCatalogView;
import com.example.esti.dto.VendorProductPartView;
import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.entity.VendorProduct;
import com.example.esti.entity.VendorProductRelation;
import com.example.esti.repository.VendorItemPriceRepository;
import com.example.esti.repository.VendorProductRelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VendorCatalogQueryService {

    private final VendorItemPriceRepository vendorItemPriceRepository;
    private final VendorProductRelationRepository vendorProductRelationRepository;

    // 제안서 작성 화면 : 전체 리스트
    @Transactional(readOnly = true)
    public List<VendorCatalogView> getVendorCatalogAll() {
        return vendorItemPriceRepository.findAllForCatalogView().stream()
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

    /**
     * 카탈로그 행(가격 라인) 하나의 <b>부속 구성</b>을 조회한다 (B-2 드릴다운).
     *
     * <p>목록 조회에 섞지 않고 별도 엔드포인트로 둔 이유는 N+1 방지다 — 목록 렌더링 시 행마다
     * 관계를 끌어오면 페이지당 관계 조회가 행 수만큼 나간다. 사용자가 행을 펼친 시점에만 부른다.
     *
     * <p>{@code @Transactional(readOnly = true)}가 필수다: 관계의 {@code sourceProduct}·
     * {@code targetProduct}가 LAZY라 세션 밖에서는 초기화되지 않는다.
     *
     * @return 부속 목록(구성이 없으면 빈 리스트). 해당 가격 라인 자체가 없으면 {@link Optional#empty()} —
     *         "부속 없음"과 "조회 실패"를 호출자가 구분할 수 있어야 한다.
     */
    @Transactional(readOnly = true)
    public Optional<List<VendorProductPartView>> getParts(Long vendorItemPriceId) {
        return vendorItemPriceRepository.findById(vendorItemPriceId)
                .map(this::partsOf);
    }

    private List<VendorProductPartView> partsOf(VendorItemPrice price) {
        Vendor vendor = price.getVendor();
        VendorProduct main = price.getVendorProduct();

        return vendorProductRelationRepository.findAllBySourceProduct(main).stream()
                // 관계 id 순 = 엑셀에 적힌 부속 순서. 정렬을 명시해야 화면 순서가 흔들리지 않는다.
                .sorted(Comparator.comparing(VendorProductRelation::getId, Comparator.nullsLast(Long::compareTo)))
                .map(relation -> toPartView(vendor, relation))
                .toList();
    }

    private VendorProductPartView toPartView(Vendor vendor, VendorProductRelation relation) {
        VendorProduct part = relation.getTargetProduct();
        // 공유 부속 단가는 코드당 1건 유지(priceBasis=null, D13) → 첫 행이 곧 그 부속의 단가다.
        BigDecimal unitPrice = vendorItemPriceRepository
                .findFirstByVendorAndVendorProduct(vendor, part)
                .map(VendorItemPrice::getUnitPrice)
                .orElse(null);

        return new VendorProductPartView(
                part.getId(),
                part.getProductCode(),
                part.getProductName(),
                relation.getRelationType(),
                unitPrice,
                relation.getQuantity() != null ? relation.getQuantity() : 1,
                part.getImageUrl()
        );
    }
}
