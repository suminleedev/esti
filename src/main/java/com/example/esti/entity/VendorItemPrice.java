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
}
