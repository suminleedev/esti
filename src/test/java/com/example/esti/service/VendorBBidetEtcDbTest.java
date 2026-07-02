package com.example.esti.service;

import com.example.esti.entity.VendorProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비데·기타 시트 <b>DB 적재</b> 검증. 파싱 정확성은 {@code VendorBBidetEtcSheetTest},
 * 여기선 대분류 분리(비데/기타)·비데 소분류·전기/배터리 변형 유실 0·스펙 괄호/비고 description 적재를 단언한다.
 */
class VendorBBidetEtcDbTest extends AbstractVendorBSheetDbVerification {

    @Test
    void 유실0_충돌0_재업로드_멱등() {
        assertNoLossNoCollision("비데");
        assertNoLossNoCollision("기타");
        assertReimportIdempotent();
    }

    @Test
    void req1_대분류는_비데_기타로_분리되고_시트명통째는_없음() {
        assertThat(dbSetProductsOf("비데, 기타")).as("시트명 통째 대분류 잔존").isEmpty();
        assertThat(dbSetProductsOf("비데")).as("비데 6세트").hasSize(6);
        assertThat(dbSetProductsOf("기타")).as("기타 14세트").hasSize(14);
    }

    @Test
    void req2_비데는_대분류_소분류_모두_비데() {
        assertThat(dbSetProductsOf("비데"))
                .as("비데 소분류가 모두 '비데'")
                .allMatch(p -> "비데".equals(p.getCategorySmall()));

        VendorProduct dsb = dbSetProduct("비데", "DSB-5420");
        assertThat(dsb.getCategoryLarge()).isEqualTo("비데");
        assertThat(dsb.getCategorySmall()).isEqualTo("비데");
        assertThat(dbSetPrice("비데", dsb)).isEqualByComparingTo(new BigDecimal("120000"));
    }

    @Test
    void req3_전기배터리_변형은_품번뒤_구분글자로_둘다적재_원본품번은_충돌없음() {
        VendorProduct electric = dbSetProduct("기타", "E102e");
        VendorProduct battery = dbSetProduct("기타", "E102b");
        assertThat(dbSetPrice("기타", electric)).isEqualByComparingTo(new BigDecimal("77000"));
        assertThat(dbSetPrice("기타", battery)).isEqualByComparingTo(new BigDecimal("76000"));

        // 접미 없는 원본 품번 E102가 남아 있으면 업서트 충돌(유실) 위험
        assertThat(dbSetProductsOf("기타"))
                .as("원본 품번 E102 잔존 없음").noneMatch(p -> "E102".equals(p.getProductCode()));
    }

    @Test
    void req4_기타_스펙은_제품명괄호_비고는_description() {
        VendorProduct hd = dbSetProduct("기타", "HD101G");
        assertThat(hd.getProductName()).isEqualTo("핸드 드라이어 HD101G (일반)");
        assertThat(hd.getDescription()).isEqualTo("보급형");
        assertThat(hd.getCategorySmall()).isEqualTo("핸드 드라이어");

        // 배터리행도 품종·품번 carry-forward + 타입은 description으로 구분
        VendorProduct battery = dbSetProduct("기타", "E102b");
        assertThat(battery.getCategorySmall()).isEqualTo("소변기 매립감지기");
        assertThat(battery.getDescription()).isEqualTo("배터리 타입: 120x120");
    }
}
