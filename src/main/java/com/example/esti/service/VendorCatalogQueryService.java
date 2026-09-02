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
     * <p><b>부속 가격행({@code priceType='PART'})은 항상 빈 리스트다</b> — 부속은 세트를 구성하는 쪽이라
     * 그 자체로는 구성이 없다. 근거는 {@link #partsOf} 주석.
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
        // 부속 가격행은 펼치지 않는다.
        //
        // 관계가 (제품 → 제품)이라 가격행의 역할을 구분하지 못한다. 같은 품번이 어떤 세트에선
        // 부속이고 다른 곳에선 대표품목이면, 부속으로 올라온 행에도 그 세트의 부속이 딸려 나온다
        // — 화면에서 "부속을 눌렀는데 부속이 또 나오는" 상태다.
        // (A사 7건. 예: [부속/폽업] 폽업을 누르면 P트랩·패킹·호스·앵글밸브가 나왔다)
        //
        // 근본 해결은 관계에 기준 축(세트 또는 가격행)을 넣는 것이고, 이건 그 전까지의 차단이다.
        // docs/analysis-a-set-parts.md §4·§10-1
        if (VendorItemPrice.PRICE_TYPE_PART.equals(price.getPriceType())) return List.of();

        Vendor vendor = price.getVendor();
        VendorProduct main = price.getVendorProduct();

        // 이 가격행이 가리키는 <b>세트 하나</b>의 부속만 (G-1).
        //
        // 관계가 (제품 → 제품)이던 때는 같은 품번의 모든 세트 부속이 한꺼번에 나왔다 —
        // 세면기 긴다리/반다리처럼 택1인 것이 동시에 보였다(A사 22종).
        //
        // setHash가 null인 행은 세트 축 도입 전에 적재된 것이다. 그때 만들어진 관계도 null이라
        // 서로 맞물린다(하위호환). JPA는 파라미터가 null이면 `= ?`로 만들어 매칭에 실패하므로
        // IsNull 변형을 따로 쓴다.
        String setHash = price.getSetHash();
        List<VendorProductRelation> relations = (setHash == null)
                ? vendorProductRelationRepository.findAllBySourceProductAndSetHashIsNull(main)
                : vendorProductRelationRepository.findAllBySourceProductAndSetHash(main, setHash);

        return relations.stream()
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

        // 그 세트에 적힌 이름을 우선한다(G-4). VendorProduct.productName은 품번당 하나라
        // 파일에서 마지막에 나온 이름으로 통일되고, 그게 최빈값도 아닌 경우가 33종 중 27종이다.
        String displayName = relation.getPartName() != null
                ? relation.getPartName()
                : part.getProductName();

        return new VendorProductPartView(
                part.getId(),
                part.getProductCode(),
                displayName,
                relation.getRelationType(),
                unitPrice,
                relation.getQuantity() != null ? relation.getQuantity() : 1,
                part.getImageUrl()
        );
    }
}
