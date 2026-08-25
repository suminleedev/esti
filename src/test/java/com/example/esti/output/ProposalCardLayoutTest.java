package com.example.esti.output;

import com.example.esti.entity.ProposalLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 카드 열 배치 규칙 검증 — 근거는 `docs/samples/제안서_sample.xlsx` 20장 대조다.
 *
 * <p>샘플에서 규칙을 확정한 결정적 사례가 `욕실청소건`이다. 부위는 욕실인데 1열이 아니라 2열에 있다.
 * 즉 "욕실이면 1열"이 아니라 "욕실 + 메인"이라는 뜻이고, 그 케이스를 못으로 박아 둔다.
 */
class ProposalCardLayoutTest {

    @Test
    @DisplayName("1열 — 부위가 욕실이고 메인 위생기구")
    void 욕실_메인은_1열() {
        assertThat(ProposalCardLayout.columnOf(line("욕실1", "양변기", false))).isZero();
        assertThat(ProposalCardLayout.columnOf(line("욕실 공통", "세면기", false))).isZero();
        assertThat(ProposalCardLayout.columnOf(line("욕실2", "세면기 수전", false))).isZero();
        assertThat(ProposalCardLayout.columnOf(line("욕실1", "욕조 수전/슬라이드바", false))).isZero();
        assertThat(ProposalCardLayout.columnOf(line("욕실1", "해바라기샤워수전", false))).isZero();

        // 샘플 부위 표기(`공용욕실`)처럼 '욕실'이 뒤에 붙어도 걸려야 한다
        assertThat(ProposalCardLayout.columnOf(line("공용욕실", "양변기", false))).isZero();
    }

    @Test
    @DisplayName("2열 — 비욕실이거나, 욕실이지만 메인이 아닌 것")
    void 비욕실과_비메인은_2열() {
        assertThat(ProposalCardLayout.columnOf(line("주방", "씽크수전", false))).isEqualTo(1);
        assertThat(ProposalCardLayout.columnOf(line("세탁실", "씽크수전", false))).isEqualTo(1);

        // 샘플의 `욕실청소건` — 부위는 욕실이나 메인 유형이 아니므로 2열이다 (규칙의 근거 사례)
        assertThat(ProposalCardLayout.columnOf(line("욕실1", "욕실청소용품", false))).isEqualTo(1);

        // 부위가 비어 있어도 1열로 새지 않는다
        assertThat(ProposalCardLayout.columnOf(line(null, "양변기", false))).isEqualTo(1);
    }

    @Test
    @DisplayName("3열 — 선택사항(유상옵션)은 부위·유형과 무관하게 모인다")
    void 옵션은_3열() {
        assertThat(ProposalCardLayout.columnOf(line("욕실1", "비데", true))).isEqualTo(2);
        assertThat(ProposalCardLayout.columnOf(line("주방", "씽크수전", true))).isEqualTo(2);
        // 옵션이 아니면 원래 자리로 간다
        assertThat(ProposalCardLayout.columnOf(line("욕실1", "비데", false))).isEqualTo(1);
    }

    @Test
    @DisplayName("4열 — 악세사리")
    void 악세사리는_4열() {
        assertThat(ProposalCardLayout.columnOf(line("욕실1", "악세사리", false))).isEqualTo(3);
        assertThat(ProposalCardLayout.columnOf(line("주방", "악세사리", false))).isEqualTo(3);
    }

    @Test
    @DisplayName("유상옵션은 예외 없이 3열이다 — 유형·부위가 무엇이든 앞선다")
    void 옵션이_최우선() {
        // 악세사리는 실제로 유상옵션이 되지 않지만(2026-08-25 사용자 확인),
        // 옵션 판정이 가장 앞에 있어 "유상옵션은 3열"이 예외 없이 성립함을 못으로 박는다.
        assertThat(ProposalCardLayout.columnOf(line("욕실1", "악세사리", true))).isEqualTo(2);
        assertThat(ProposalCardLayout.columnOf(line("욕실1", "양변기", true))).isEqualTo(2);
        assertThat(ProposalCardLayout.columnOf(line("주방", "씽크수전", true))).isEqualTo(2);
    }

    @Test
    @DisplayName("distribute는 4열 자리를 항상 지키고 열 안에서는 입력 순서를 유지한다")
    void 분배와_순서() {
        ProposalLine a = named(line("욕실1", "양변기", false), "A");
        ProposalLine b = named(line("욕실1", "세면기", false), "B");
        ProposalLine c = named(line("주방", "씽크수전", false), "C");

        List<List<ProposalLine>> columns = ProposalCardLayout.distribute(List.of(a, b, c));

        assertThat(columns).hasSize(4);
        assertThat(columns.get(0)).extracting(ProposalLine::getProductName).containsExactly("A", "B");
        assertThat(columns.get(1)).extracting(ProposalLine::getProductName).containsExactly("C");
        assertThat(columns.get(2)).isEmpty();
        assertThat(columns.get(3)).isEmpty();

        // 블록 수 = 가장 긴 열
        assertThat(ProposalCardLayout.blockCount(columns)).isEqualTo(2);
        assertThat(ProposalCardLayout.blockCount(ProposalCardLayout.distribute(List.of()))).isZero();
    }

    private ProposalLine line(String area, String category, boolean optional) {
        ProposalLine l = new ProposalLine();
        l.setArea(area);
        l.setCategory(category);
        l.setOptional(optional);
        return l;
    }

    private ProposalLine named(ProposalLine l, String name) {
        l.setProductName(name);
        return l;
    }
}
