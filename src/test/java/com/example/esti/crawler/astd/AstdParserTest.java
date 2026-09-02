package com.example.esti.crawler.astd;

import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.support.TestSamples;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 파서 단위 테스트. 네트워크를 타지 않는다 — 사이트 HTML은 픽스처로 고정한다.
 *
 * <p>합성 케이스는 태그 텍스트를 직접 만들어 규칙 하나씩을 겨냥하고,
 * 픽스처 케이스는 실제 리스트 HTML로 <b>추출률</b>을 지킨다.
 * 픽스처는 gitignore라 없으면 로컬 스킵 / CI fail이다({@link TestSamples}).
 */
class AstdParserTest {

    private static final Path FIXTURE_DIR = Path.of("docs/samples/astd");

    private final AstdParser parser = new AstdParser();

    /** 태그 텍스트만 담은 최소 리스트 항목을 만든다. */
    private Element listItem(String tagHtml) {
        String html = """
                <ul class="list_wrap"><li>
                  <a href="/main/product/ajaxList.do?proc_type=view&v_product=1"></a>
                  <div class="img"><img src="/main/product/img.do?v_product=1"></div>
                  <p class="tit">제품명</p>
                  <p class="tag">%s</p>
                </li></ul>
                """.formatted(tagHtml);

        return Jsoup.parse(html, "https://example.com/").selectFirst("ul.list_wrap > li");
    }

    private String codeOf(String tagHtml) {
        return parser.parseFromListItem(listItem(tagHtml), "ASTD", "A")
                .map(CrawledProduct::getProductCode)
                .orElse(null);
    }

    @Test
    @DisplayName("라벨이 있으면 라벨 뒤 품번을 쓴다")
    void readsCodeAfterKnownLabel() {
        assertThat(codeOf("품번 : <br>FB2311-0GAK311AA")).isEqualTo("FB2311-0GAK311AA");
        assertThat(codeOf("도기 : CCAS6507")).isEqualTo("CCAS6507");
    }

    @Test
    @DisplayName("라벨이 아예 없어도 품번 모양을 직접 뽑는다 — 실패의 최다 유형이었다")
    void fallsBackToCodeShapeWhenNoLabel() {
        assertThat(codeOf("FC2321-0GAK311AA")).isEqualTo("FC2321-0GAK311AA");
        assertThat(codeOf("FC5797-0KAK212GU")).isEqualTo("FC5797-0KAK212GU");
    }

    @Test
    @DisplayName("라벨 오타도 같은 폴백이 흡수한다 — 오타마다 별칭을 등록하지 않는다")
    void fallbackAbsorbsMisspelledLabel() {
        assertThat(codeOf("풉번 : FD1771-0GAK111AA")).isEqualTo("FD1771-0GAK111AA");
    }

    @Test
    @DisplayName("품번이 여럿이면 뽑지 않는다 — 대표를 고를 근거가 없다")
    void refusesWhenSeveralCodesArePresent() {
        assertThat(codeOf("옷걸이: FFAS0281-908500BC0 <br>비누대: FFAS0282-908500BC0")).isNull();
    }

    @Test
    @DisplayName("품번이 아닌 낱말을 품번으로 오인하지 않는다")
    void doesNotMistakeNonCodeTokens() {
        assertThat(codeOf("코너선반 L-TYPE (840 파이프)")).isNull();
        assertThat(codeOf("2WAY 선반형 레인샤워")).isNull();
    }

    @Test
    @DisplayName("라벨이 있으면 폴백보다 라벨이 이긴다")
    void labelWinsOverFallback() {
        // 뒤에 다른 품번이 더 있어도 라벨이 가리키는 것을 쓴다
        assertThat(codeOf("품번 : <br>FH1048-0KAK500AJ <br>매립박스 : <br>M1BH56"))
                .isEqualTo("FH1048-0KAK500AJ");
    }

    @Test
    @DisplayName("픽스처 전량에서 품번을 뽑는다 — 다중 품번 항목만 남는다")
    void extractsCodeFromEveryFixtureItemExceptMultiCode() throws Exception {
        TestSamples.requireSample(FIXTURE_DIR.resolve("cate5-page1.html"));

        List<String> withoutCode = new ArrayList<>();
        int total = 0;

        try (DirectoryStream<Path> files = Files.newDirectoryStream(FIXTURE_DIR, "*.html")) {
            for (Path file : files) {
                Document doc = Jsoup.parse(
                        Files.readString(file, StandardCharsets.UTF_8), "https://example.com/");

                for (Element li : doc.select("ul.list_wrap > li")) {
                    Optional<CrawledProduct> parsed = parser.parseFromListItem(li, "ASTD", "A");
                    if (parsed.isEmpty()) {
                        continue;
                    }
                    total++;
                    if (parsed.get().getProductCode() == null) {
                        withoutCode.add(parsed.get().getRawTagText());
                    }
                }
            }
        }

        assertThat(total).isGreaterThan(30);

        // 남는 것은 한 항목에 품번이 여럿인 액세서리 세트뿐이다(C-7 범위).
        assertThat(withoutCode)
                .allSatisfy(tag -> assertThat(tag).contains("옷걸이"));
    }
}
