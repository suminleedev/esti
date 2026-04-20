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
                @Index(name = "idx_vendor_product_vendor_rep", columnList = "vendor_id, representative_code"),
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
    @Column(name = "representative_code", length = 100)
    private String representativeCode;

    // 상세품번 (있으면 저장, 없으면 null 가능)
    @Column(name = "detail_code", length = 100)
    private String detailCode;

    // 원본 품번 전체값
    @Column(name = "product_code", length = 200)
    private String productCode;

    // 상품명
    @Column(name = "product_name", length = 500)
    private String productName;

    // 컬렉션명
    @Column(name = "collection_name", length = 200)
    private String collectionName;

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