package com.example.esti.repository;

import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.entity.VendorProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // 세트 축 도입 후 대표품목 가격행의 upsert 키 — 세트별로 갈린다 (G-1).
    // 이게 없으면 같은 품번의 여러 세트가 한 행으로 접혀 세트가가 하나만 남는다.
    Optional<VendorItemPrice> findByVendorAndVendorProductAndProposalItemCodeAndPriceBasisAndSetHash(
            Vendor vendor, VendorProduct product, String proposalItemCode, String priceBasis, String setHash);

    /**
     * 재적재 시 <b>그 (제품, priceBasis)</b>의 낡은 대표품목 가격행을 걷어내기 위한 조회.
     *
     * <p><b>basis까지 좁히는 이유</b>: 한 공급사를 여러 파일로 나눠 적재하는 경우가 있다
     * (B사 수전부속 + 신규 OEM 부속). 제품 단위로 지우면 <b>앞 파일이 넣은 행을 뒤 파일이 지운다.</b>
     * 파일마다 basis가 다르므로 basis로 좁히면 서로 침범하지 않는다.
     */
    List<VendorItemPrice> findAllByVendorAndVendorProductAndPriceTypeAndPriceBasis(
            Vendor vendor, VendorProduct product, String priceType, String priceBasis);

    /**
     * 업로드가 덮은 <b>basis 하나</b>의 대표품목 가격행 전량 — 최신본에서 사라진 행을 걷어내기 위한 조회.
     *
     * <p>위 {@code findAllByVendorAndVendorProduct...}는 <b>이번 파일에 여전히 등장하는 제품</b>만
     * 훑는다. 파일에서 아예 빠진 제품은 순회 대상이 아니라 손이 닿지 않는다. 그래서 제품이 아니라
     * <b>basis 전체</b>를 한 번 훑어, 이번 실행이 건드리지 않은 행을 가려낸다.
     *
     * <p>basis가 범위인 이유는 위와 같다 — 한 공급사를 여러 파일로 나눠 올리므로, 전체를 기준으로
     * 지우면 이번에 올리지 않은 파일의 제품이 통째로 날아간다. 실측(2026-09-07)으로 <b>같은 공급사
     * 안에서 basis가 두 파일에 걸치는 경우가 없음</b>을 확인했다.
     */
    List<VendorItemPrice> findAllByVendorAndPriceTypeAndPriceBasis(
            Vendor vendor, String priceType, String priceBasis);

    // 제안서 품번이 없는(신품번 없음) 항목의 멱등 upsert용
    Optional<VendorItemPrice> findFirstByVendorAndVendorProduct(Vendor vendor, VendorProduct product);

    // VendorItemPrice.vendor.vendorCode 로 찾아오는 메서드
    List<VendorItemPrice> findByVendor_VendorCode(String vendorCode);

    // 페이징 처리하여 반환
    Page<VendorItemPrice> findByVendor_VendorCode(String vendorCode, Pageable pageable);

    /**
     * 카탈로그 화면 검색 (F-015) — 공급사 지정.
     *
     * <p><b>서버에서 찾아야 한다.</b> 화면이 서버 페이징이라 클라이언트에서 거르면 <b>지금 보이는
     * 한 페이지</b>만 뒤지게 된다. 2,247건을 10건씩 보면 225페이지다.
     *
     * <p>비교 대상은 제안서 화면 검색과 같은 축이다 — 품번(신·구·제안서), 제품명(제품·공급사 표기),
     * 분류(대·소), 규격, 비고. {@code vendorName}은 넣지 않는다: 공급사는 옆에 전용 필터가 있고,
     * 여기 넣으면 공급사명 한 글자에 전건이 걸린다.
     *
     * <p>대소문자를 가리지 않으려고 양쪽을 {@code lower}로 눕힌다. 패턴({@code %...%})과
     * 와일드카드 이스케이프는 서비스가 만들어 넘긴다 — JPQL에서 문자열을 잇지 않으려는 것이다.
     */
    @Query("""
            select vip from VendorItemPrice vip
             where vip.vendor.vendorCode = :vendorCode
               and (lower(coalesce(vip.mainItemCode, '')) like :pattern escape '!'
                 or lower(coalesce(vip.oldItemCode, '')) like :pattern escape '!'
                 or lower(coalesce(vip.proposalItemCode, '')) like :pattern escape '!'
                 or lower(coalesce(vip.vendorItemName, '')) like :pattern escape '!'
                 or lower(coalesce(vip.remark, '')) like :pattern escape '!'
                 or lower(coalesce(vip.vendorProduct.productName, '')) like :pattern escape '!'
                 or lower(coalesce(vip.vendorProduct.categoryLarge, '')) like :pattern escape '!'
                 or lower(coalesce(vip.vendorProduct.categorySmall, '')) like :pattern escape '!'
                 or lower(coalesce(vip.vendorProduct.specs, '')) like :pattern escape '!'
                 or lower(coalesce(vip.vendorProduct.collectionName, '')) like :pattern escape '!')
            """)
    Page<VendorItemPrice> searchByVendor(@Param("vendorCode") String vendorCode,
                                         @Param("pattern") String pattern,
                                         Pageable pageable);

    /** 카탈로그 화면 검색 (F-015) — 공급사 «전체». 조건은 {@link #searchByVendor}와 같다. */
    @Query("""
            select vip from VendorItemPrice vip
             where lower(coalesce(vip.mainItemCode, '')) like :pattern escape '!'
                or lower(coalesce(vip.oldItemCode, '')) like :pattern escape '!'
                or lower(coalesce(vip.proposalItemCode, '')) like :pattern escape '!'
                or lower(coalesce(vip.vendorItemName, '')) like :pattern escape '!'
                or lower(coalesce(vip.remark, '')) like :pattern escape '!'
                or lower(coalesce(vip.vendorProduct.productName, '')) like :pattern escape '!'
                or lower(coalesce(vip.vendorProduct.categoryLarge, '')) like :pattern escape '!'
                or lower(coalesce(vip.vendorProduct.categorySmall, '')) like :pattern escape '!'
                or lower(coalesce(vip.vendorProduct.specs, '')) like :pattern escape '!'
                or lower(coalesce(vip.vendorProduct.collectionName, '')) like :pattern escape '!'
            """)
    Page<VendorItemPrice> searchAll(@Param("pattern") String pattern, Pageable pageable);

    // 크롤링
    Optional<VendorItemPrice> findByVendor_VendorCodeAndProposalItemCode(String vendorCode, String proposalItemCode);
}

