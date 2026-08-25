package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 *  공급사 상품 마스터 : 상품 정보
 *  크롤링 저장 메인
 */
@Entity
@Table(
        name = "vendor_product",
        schema = "APP",
        indexes = {
                @Index(name = "idx_vendor_product_vendor_master", columnList = "vendor_id, master_code"),
                @Index(name = "idx_vendor_product_vendor_detail", columnList = "vendor_id, detail_code")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorProduct extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 공급사
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    // 대표품번 (DB 비교 기준)
    @Column(name = "master_code", length = 100)
    private String masterCode;

    // 상세품번 (있으면 저장, 없으면 null 가능)
    @Column(name = "detail_code", length = 100)
    private String detailCode;

    // 원본 품번 전체값
    @Column(name = "product_code", length = 200)
    private String productCode;

    // 상품명(세트명)
    @Column(name = "product_name", length = 500)
    private String productName;

    // 컬렉션명(시리즈명)
    @Column(name = "collection_name", length = 200)
    private String collectionName;

    // 표준 규격 (공통 스펙)
    @Column(length = 200)
    private String specs;

    // 설명
    @Column(length = 500)
    private String description;

    // ===== 카테고리/타입 =====
    @Column(length = 100)
    private String categoryLarge;  // 대분류 (양변기, 세면기 등)

    @Column(length = 100)
    private String categorySmall;  // 소분류 (원피스양변기 등)

    @Column(length = 20)
    private String itemType;       // 'SET', 'PART' 등

    /**
     * 견적서 C열에 찍히는 단위(SET/EA 등). 단가표 원본에는 `부속류` 시트 D3에만 있어 파싱으로 못 얻는다(O-1b).
     * 기본값 `SET`으로 두고 예외(수건걸이·휴지걸이 등 EA)만 카탈로그 화면에서 고친다.
     * 기존 행은 null이며 읽는 쪽에서 SET으로 폴백한다.
     */
    @Column(length = 20)
    @Builder.Default
    private String unit = UNIT_DEFAULT;

    /** 단위 기본값. 원본에 값이 없을 때 쓴다. */
    public static final String UNIT_DEFAULT = "SET";

    /** null·공백을 기본값으로 접어 준다. 표시·스냅샷 경로가 공통으로 쓴다. */
    public static String unitOrDefault(String unit) {
        return (unit == null || unit.isBlank()) ? UNIT_DEFAULT : unit;
    }

    // ===== 이미지 =====
    // 대표 이미지
    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    // 원본 상세 URL
    @Column(name = "detail_url", length = 1000)
    private String detailUrl;

    // 크롤링 원문 보관용
    @Lob
    @Column(name = "raw_tag_text")
    private String rawTagText;
}