package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

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

    // 내 기준 카탈로그와 연결
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id", nullable = false)
    private ProductCatalog catalog;

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

    @Column(length = 200)
    private String vendorSpec;        // 공급사 기준 규격/특징

    @Column(length = 500)
    private String remark;            // 비고 전체

    // ===== 단가 =====
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;     // 세트(합계) 단가

    @Column(length = 20)
    private String priceType;         // 'SET', 'PART' 등

    @Column(length = 10)
    private String currency;          // KRW 등
}
