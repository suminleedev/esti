package com.example.esti.output;

import com.example.esti.entity.ProposalLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 견적서 출력 대상 판정 (O-7 ⓑ — 평형별 별도 견적서 / 부속동·상가 합본).
 */
class QuoteTargetTest {

    @Test
    @DisplayName("본세대 — 건물구분이 비었거나 '본세대'인 라인만, 지정 평형만 담는다")
    void 본세대_대상() {
        QuoteTarget target = QuoteTarget.main("59㎡");

        assertThat(target.matches(line("본세대", "59㎡"))).isTrue();
        // 건물구분이 없는 구 데이터도 본세대로 본다
        assertThat(target.matches(line(null, "59㎡"))).isTrue();
        assertThat(target.matches(line("  ", "59㎡"))).isTrue();

        assertThat(target.matches(line("본세대", "84㎡"))).isFalse();   // 다른 평형
        assertThat(target.matches(line("부속동", "59㎡"))).isFalse();   // 부속동
    }

    @Test
    @DisplayName("평형을 지정하지 않으면 본세대 전부를 담는다 (라인 평형이 없는 구 데이터 대응)")
    void 평형_미지정() {
        QuoteTarget target = QuoteTarget.main(null);

        assertThat(target.matches(line("본세대", "59㎡"))).isTrue();
        assertThat(target.matches(line("본세대", "84㎡"))).isTrue();
        assertThat(target.matches(line(null, null))).isTrue();
        assertThat(target.matches(line("부속동", null))).isFalse();
    }

    @Test
    @DisplayName("부속동 합본 — 본세대가 아닌 것 전부. 현장별 새 구분값도 자동으로 실린다")
    void 부속동_대상() {
        QuoteTarget target = QuoteTarget.annex();

        assertThat(target.matches(line("부속동", "59㎡"))).isTrue();
        assertThat(target.matches(line("상가", null))).isTrue();
        // Phase 7 이전에는 자유 입력이라 새 값이 들어올 수 있다 — 누락 없이 부속동 파일로 간다
        assertThat(target.matches(line("관리동", null))).isTrue();

        assertThat(target.matches(line("본세대", "59㎡"))).isFalse();
        assertThat(target.matches(line(null, "59㎡"))).isFalse();
    }

    private ProposalLine line(String buildingType, String apartmentType) {
        ProposalLine l = new ProposalLine();
        l.setBuildingType(buildingType);
        l.setApartmentType(apartmentType);
        return l;
    }
}
