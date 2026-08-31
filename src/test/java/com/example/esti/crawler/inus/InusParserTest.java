package com.example.esti.crawler.inus;

import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.support.TestSamples;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InusParser} 단위 테스트 — 네트워크를 타지 않는다.
 *
 * <p>입력은 {@code docs/samples/inus/}에 받아 둔 리스트 HTML 픽스처다(gitignore).
 * 부재 시 로컬 스킵 / CI fail — {@link TestSamples} 관례를 따른다.
 */
class InusParserTest {

    private static final Path FIXTURE_DIR = Path.of("docs", "samples", "inus");
    private static final String BASE = "http://www.inushaus.kr/product/";

    private final InusParser parser = new InusParser();

    /**
     * 리스트별 제품 수 — 사이트가 선언한 "총 N개"와 일치하는 값이다(계획서 §5-6 실측).
     * 어긋나면 사이트가 바뀐 것이거나 선택자가 틀린 것이다.
     */
    @ParameterizedTest(name = "{0} → {1}건")
    @CsvSource({
            "wcList, 31",
            "wbList, 39",
            "bList,  21",
            "urList, 13",
            "faList, 195",
            "acList, 103"
    })
    @DisplayName("리스트별 파싱 건수가 실측값과 일치한다")
    void parsesDeclaredProductCount(String list, int expected) throws IOException {
        assertThat(parse(list)).hasSize(expected);
    }

    @Test
    @DisplayName("본선 6개 리스트 합계가 402건이고, 이미지 누락이 0건이다")
    void collectsEveryProductWithAnImage() throws IOException {
        List<CrawledProduct> all = parseAll();

        assertThat(all).hasSize(402);
        assertThat(all).allSatisfy(p -> {
            assertThat(p.getProductCode()).isNotBlank();
            assertThat(p.getImageUrl())
                    .as("이미지 누락: %s", p.getProductCode())
                    .isNotNull()
                    .startsWith("http://www.inushaus.kr/upload/");
        });
    }

    @Test
    @DisplayName("별칭 품번을 rawTagText로 넘긴다 — 별칭이 없는 쪽이 정상 경로다")
    void extractsAliasCodeWhenPresent() throws IOException {
        List<CrawledProduct> all = parseAll();

        // 사이트에 별칭 표시(㉿)가 붙은 건 81건이지만, 그중 1건은 사이트 오타라 쓸 수 없다.
        // → keepsUnusableAliasOutOfTheMatchingKey() 참고
        assertThat(all).filteredOn(p -> p.getRawTagText() != null).hasSize(80);

        assertThat(findByCode(all, "IBL1681"))
                .get()
                .extracting(CrawledProduct::getRawTagText)
                .isEqualTo("L610");
    }

    @Test
    @DisplayName("사이트 오타로 읽을 수 없는 별칭은 매칭 키로 넘기지 않는다")
    void keepsUnusableAliasOutOfTheMatchingKey() throws IOException {
        // wbList의 L631BE는 별칭이 'ㅣ620'으로 적혀 있다 — 라틴 I나 L이 아니라
        // 한글 자모 'ㅣ'(U+3163)다. 눈으로는 구분이 안 가지만 코드로는 다른 글자다.
        // 의도한 글자를 확정할 수 없으므로 추측해서 만들어내지 않고 버린다.
        // 제품 자체는 정상으로 수집되고, 품번(L631BE)으로만 매칭된다.
        assertThat(findByCode(parse("wbList"), "L631BE"))
                .get()
                .extracting(CrawledProduct::getRawTagText)
                .isNull();
    }

    @Test
    @DisplayName("<a> 위치가 달라도 파싱된다 — bList는 div.img 안, wbList는 li 전체를 감싼다")
    void parsesRegardlessOfAnchorPosition() throws IOException {
        assertThat(findByCode(parse("bList"), "DSB-5420NV"))
                .get()
                .extracting(CrawledProduct::getImageUrl)
                .asString()
                .startsWith("http://www.inushaus.kr/upload/bidet/");

        assertThat(findByCode(parse("wbList"), "IL401"))
                .get()
                .extracting(CrawledProduct::getImageUrl)
                .asString()
                .startsWith("http://www.inushaus.kr/upload/washbasin/");
    }

    @Test
    @DisplayName("<dt>에 마케팅 문구가 섞여도 품번만 뽑는다")
    void ignoresMarketingTextInDt() throws IOException {
        // "초절수 1등급 IC858RPG1" — 앞의 한글·등급 표기를 품번으로 잘못 집으면 안 된다
        assertThat(findByCode(parse("wcList"), "IC858RPG1")).isPresent();

        assertThat(parseAll())
                .extracting(CrawledProduct::getProductCode)
                .allSatisfy(code -> assertThat(code).matches("[A-Za-z0-9][A-Za-z0-9\\-()/\uFF0F]*"));
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource({
            "wcList, IW-700(IC700DE)",
            "bList,  UB-FH6510(G)",
            "bList,  UB-FH6515E(W)",
            "bList,  IS-7000AF/7100AF",
            "acList, HD501G／HD501P"
    })
    @DisplayName("괄호·슬래시가 든 품번을 사이트 표기 그대로 담는다")
    void keepsCompositeCodesVerbatim(String list, String code) throws IOException {
        // 이 5건을 놓치면 402건이 397건이 된다. 어디까지가 품번인지는 매칭 쪽이 정한다.
        assertThat(findByCode(parse(list), code)).isPresent();
    }

    @Test
    @DisplayName("배지 아이콘(New·Good Design)을 제품 이미지로 집지 않는다")
    void skipsBadgeIcons() throws IOException {
        assertThat(parseAll())
                .extracting(CrawledProduct::getImageUrl)
                .allSatisfy(url -> assertThat(url).doesNotContain("/images/product/pIcon"));
    }

    @Test
    @DisplayName("상세 페이지가 없으므로 productUrl은 리스트 URL이고 siteProductId는 비어 있다")
    void usesListUrlBecauseThereIsNoDetailPage() throws IOException {
        assertThat(parse("urList")).allSatisfy(p -> {
            assertThat(p.getProductUrl()).isEqualTo(listUrl("urList"));
            assertThat(p.getSiteProductId()).isNull();
        });
    }

    @Test
    @DisplayName("품목 분류명을 productName으로 담는다")
    void keepsCategoryLabelAsProductName() throws IOException {
        assertThat(findByCode(parse("wbList"), "IBL1681"))
                .get()
                .extracting(CrawledProduct::getProductName)
                .isEqualTo("대형세면기(일체형)");
    }

    // ===== helpers =====

    private static final String[] LISTS = {"wcList", "wbList", "bList", "urList", "faList", "acList"};

    private List<CrawledProduct> parseAll() throws IOException {
        List<CrawledProduct> all = new java.util.ArrayList<>();
        for (String list : LISTS) {
            all.addAll(parse(list));
        }
        return all;
    }

    private List<CrawledProduct> parse(String list) throws IOException {
        Path fixture = FIXTURE_DIR.resolve(list + ".html");
        TestSamples.requireSample(fixture);

        // baseUri를 비워 둔 채 파싱한다 — 파서가 문서 baseUri가 아니라
        // 넘겨받은 listUrl로 절대 URL을 만드는지 함께 확인하려는 것이다.
        Document doc = Jsoup.parse(Files.readString(fixture, StandardCharsets.UTF_8));

        return parser.parseList(doc, listUrl(list), "INUS", "B");
    }

    private String listUrl(String list) {
        return BASE + list + ".asp?count=9999";
    }

    private Optional<CrawledProduct> findByCode(List<CrawledProduct> products, String code) {
        return products.stream().filter(p -> code.equals(p.getProductCode())).findFirst();
    }
}
