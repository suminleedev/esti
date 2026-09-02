package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 *  상품 가격 엔티티
 *  엑셀 업로드 메인
 */
@Entity
@Table(name = "vendor_item_price", schema = "APP")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VendorItemPrice {

    /** {@link #priceType} — 대표품목(세트) 가격행. */
    public static final String PRICE_TYPE_SET = "SET";

    /** {@link #priceType} — 부속 가격행. 세트를 구성하는 쪽이라 그 자체로는 구성이 없다. */
    public static final String PRICE_TYPE_PART = "PART";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 공급사
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    // 상품 정보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_product_id", nullable = false)
    private VendorProduct vendorProduct;

    /** VendorProduct로 대체 -- 미사용 예정 */
    // 내 기준 카탈로그와 연결
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "catalog_id", nullable = false)
//    private ProductCatalog catalog;

    // ===== 품번/코드 관련 =====
    @Column(length = 50)
    private String proposalItemCode;  // 제안서에 찍힐 품번 (A: 메인부속 신품번, B: 제품 품번)

    @Column(length = 50)
    private String mainItemCode;      // 메인부속품 품번 (A사 중심)

    @Column(length = 50)
    private String subItemCode;       // 보조품번(B사) 또는 기타 보조 코드

    @Column(length = 50)
    private String oldItemCode;       // 구품번(A사)

    // ===== 표시용 이름/규격/비고 =====
    @Column(length = 200)
    private String vendorItemName;    // 공급사 기준 제품명

    @Column(length = 500)
    private String remark;            // 비고 전체

    // ===== 단가 =====
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;     // 세트(합계) 단가

    @Column(length = 20)
    private String priceType;         // 'SET', 'PART' 등

    // 가격 기준(출처 시트). 같은 품번이 시트별로 다른 가격일 때 분리 보존용.
    // 대표품목(SET)에만 설정, 공유 부속(PART)은 null(코드당 1건 유지, D13).
    @Column(name = "price_basis", length = 100)
    private String priceBasis;

    @Column(length = 10)
    private String currency;          // KRW 등

    /**
     * 세트 정체성 — 부속 구성 다이제스트 (G-1). <b>대표품목(SET) 행에만 채운다.</b>
     *
     * <p>같은 품번이 여러 세트의 대표품목일 때 가격행을 갈라 준다. 이게 없으면 세트가 접히면서
     * <b>세트가가 하나만 남는다</b> — A사에서 19종의 서로 다른 세트가 24개가 덮였다.
     * 공유 부속(PART) 행은 코드당 1건을 유지하므로(D13) null이다.
     *
     * <p><b>nullable이어야 한다.</b> {@code ddl-auto=update}로 기존 테이블에 NOT NULL 컬럼을
     * 붙이면 Derby가 기본값을 요구해 ALTER TABLE이 통째로 실패한다({@code VendorProductRelation.quantity}
     * 주석의 교훈). 적재 전 기존 행은 null로 남고, 조회는 그 상태에서도 동작한다(하위호환).
     */
    @Column(name = "set_hash", length = 64)
    private String setHash;
}
