package com.example.esti.output;

import com.example.esti.entity.ProposalLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 견적서 소분류 소계 묶음 — 샘플의 도기류/수전류/악세사리 3덩어리 대응. */
class QuoteItemGroupTest {

    @Test
    @DisplayName("샘플의 세 덩어리를 유형으로 되짚는다")
    void 유형_매핑() {
        assertThat(QuoteItemGroup.of(line("양변기"))).isEqualTo(QuoteItemGroup.SANITARY_WARE);
        assertThat(QuoteItemGroup.of(line("비데"))).isEqualTo(QuoteItemGroup.SANITARY_WARE);
        assertThat(QuoteItemGroup.of(line("세면기"))).isEqualTo(QuoteItemGroup.SANITARY_WARE);

        assertThat(QuoteItemGroup.of(line("세면기 수전"))).isEqualTo(QuoteItemGroup.FAUCET);
        assertThat(QuoteItemGroup.of(line("욕조 수전/슬라이드바"))).isEqualTo(QuoteItemGroup.FAUCET);
        assertThat(QuoteItemGroup.of(line("해바라기샤워수전"))).isEqualTo(QuoteItemGroup.FAUCET);
        assertThat(QuoteItemGroup.of(line("씽크수전"))).isEqualTo(QuoteItemGroup.FAUCET);

        assertThat(QuoteItemGroup.of(line("악세사리"))).isEqualTo(QuoteItemGroup.ACCESSORY);
    }

    @Test
    @DisplayName("모르는 유형·빈 값은 기타로 모여 항목이 사라지지 않는다")
    void 미분류는_기타() {
        assertThat(QuoteItemGroup.of(line("욕실장"))).isEqualTo(QuoteItemGroup.OTHER);
        assertThat(QuoteItemGroup.of(line(null))).isEqualTo(QuoteItemGroup.OTHER);
    }

    @Test
    @DisplayName("출력 순서는 enum 선언 순서 — 도기 → 수전 → 악세사리 → 기타")
    void 선언_순서() {
        assertThat(QuoteItemGroup.values()).containsExactly(
                QuoteItemGroup.SANITARY_WARE,
                QuoteItemGroup.FAUCET,
                QuoteItemGroup.ACCESSORY,
                QuoteItemGroup.OTHER);
    }

    private ProposalLine line(String category) {
        ProposalLine l = new ProposalLine();
        l.setCategory(category);
        return l;
    }
}
