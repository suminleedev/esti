package com.example.esti.excel;

import com.example.esti.support.DemoPlaceholderImages;
import com.example.esti.support.DemoSeedWorkbooks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 데모 시드 단가표가 <b>지금도 파싱되는지</b> 지킨다 (D-2).
 *
 * <p>시드는 커밋된 바이너리라 파서가 바뀌어도 저절로 따라오지 않는다. 어긋나면 배포본이
 * <b>빈 카탈로그로 뜬다</b> — 그것도 조용히, 기동 로그의 「0건」 한 줄로만. 그래서 커밋된 파일을
 * 직접 열어 세트·부속·분류가 기대한 모양으로 나오는지 본다.
 *
 * <p>파일을 다시 만들려면 {@code DemoSeedWorkbooks}를 고치고 아래 명령을 돌린다:
 *
 * <pre>./mvnw test -Dtest=DemoSeedParseTest -Ddemo.seed.write=true</pre>
 */
class DemoSeedParseTest {

    private static final Path SEED_DIR = Path.of("src", "main", "resources", "static", "samples");
    private static final Path SEED_A = SEED_DIR.resolve("vendor-a-sample.xlsx");
    private static final Path SEED_B = SEED_DIR.resolve("vendor-b-sample.xlsx");
    private static final Path IMAGE_DIR = Path.of("src", "main", "resources", "static", "demo-images");

    /**
     * 커밋된 시드 파일을 다시 만든다. 평소에는 돌지 않는다 —
     * 테스트가 {@code src/main/resources}를 건드리는 건 명시적으로 시켰을 때뿐이어야 한다.
     */
    @Test
    @EnabledIfSystemProperty(named = "demo.seed.write", matches = "true")
    void 시드를_다시_만든다() throws IOException {
        DemoSeedWorkbooks.writeVendorA(SEED_A);
        DemoSeedWorkbooks.writeVendorB(SEED_B);
        DemoPlaceholderImages.writeAll(IMAGE_DIR);
        System.out.println("[demo-seed] 다시 만들었다: " + SEED_A + " · " + SEED_B + " · " + IMAGE_DIR);
    }

    @Test
    @DisplayName("플레이스홀더 이미지가 대분류 매핑만큼 다 있다")
    void 플레이스홀더가_다_있다() {
        for (String name : DemoPlaceholderImages.names()) {
            assertThat(IMAGE_DIR.resolve(name + ".png"))
                    .as("데모 카탈로그가 %s 대분류에 붙일 이미지", name)
                    .exists();
        }
    }

    @Test
    @DisplayName("시드 파일이 저장소에 있다")
    void 시드가_있다() {
        assertThat(SEED_A).exists();
        assertThat(SEED_B).exists();
    }

    @Test
    @DisplayName("A사 양식 — 세트 100건 안팎, 대분류 11종, 세트가는 본품+부속합")
    void A사_시드() {
        List<VendorProductSet> sets = new VendorAExcelParser().parseSets(SEED_A);

        assertThat(sets).hasSizeGreaterThanOrEqualTo(100);
        assertThat(sets).extracting(VendorProductSet::categoryLarge).doesNotContainNull();
        assertThat(sets).extracting(VendorProductSet::categoryLarge).containsOnly(
                "양변기", "비데", "세면기", "욕조", "수전", "샤워수전",
                "주방수전", "액세서리", "발코니수전", "상업용제품", "부속");

        // 합계행이 부속합과 어긋나면 파서가 검수 플래그를 세우고 세트를 흩는다.
        // 데모에서 「검수필요」 배지가 잔뜩 뜨는 카탈로그를 보여줄 이유가 없다.
        assertThat(sets).allSatisfy(set -> {
            BigDecimal expected = set.parts().stream()
                    .map(VendorParsedItem::unitPrice)
                    .reduce(set.main().unitPrice(), BigDecimal::add);
            assertThat(set.setPrice()).usingComparator(BigDecimal::compareTo).isEqualTo(expected);
        });
        assertThat(sets).noneMatch(VendorProductSet::needsReview);

        // 시리즈명은 목록·검색에 나가는 값이다. 비면 그 기능이 데모에서 안 보인다.
        assertThat(sets.stream().filter(s -> s.seriesName() != null)).hasSizeGreaterThan(80);

        // 부속이 0건이면 세트/부속 배지가 데모에서 안 보인다.
        assertThat(sets.stream().filter(s -> !s.parts().isEmpty())).hasSizeGreaterThan(80);
        // 합계행 없이 끝나는 단품도 있어야 «세트가 아닌 제품» 표시를 볼 수 있다.
        assertThat(sets.stream().filter(s -> s.parts().isEmpty())).isNotEmpty();
    }

    @Test
    @DisplayName("B사 양식 — 시트 4종이 각각 제 대분류로 들어온다")
    void B사_시드() {
        List<VendorProductSet> sets = new VendorBExcelParser().parseSets(SEED_B);

        assertThat(sets).hasSizeGreaterThanOrEqualTo(90);
        assertThat(sets).extracting(VendorProductSet::categoryLarge)
                .containsOnly("양변기", "세면기", "악세사리", "수전금구");

        // 양변기는 計가 있는 시트다 — 세트가 = 부속 단가의 합.
        List<VendorProductSet> toilets = sets.stream()
                .filter(s -> "양변기".equals(s.categoryLarge())).toList();
        assertThat(toilets).hasSize(30);
        assertThat(toilets).allSatisfy(set -> {
            BigDecimal partsSum = set.parts().stream()
                    .map(VendorParsedItem::unitPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(set.setPrice()).usingComparator(BigDecimal::compareTo).isEqualTo(partsSum);
        });

        // 세면기는 計가 없는 «선택형»이다 — 기본 구성만 세트가에 들어가고
        // 나머지 도기·다리는 대체옵션으로 붙는다. 데모에서 이 차이를 보여주는 자리다.
        List<VendorProductSet> basins = sets.stream()
                .filter(s -> "세면기".equals(s.categoryLarge())).toList();
        assertThat(basins).hasSize(24);
        assertThat(basins).allSatisfy(set -> assertThat(set.parts())
                .anyMatch(p -> "대체옵션".equals(p.remark())));

        // 악세사리는 세트와 단품이 섞인다.
        List<VendorProductSet> accessories = sets.stream()
                .filter(s -> "악세사리".equals(s.categoryLarge())).toList();
        assertThat(accessories.stream().filter(s -> !s.parts().isEmpty())).hasSize(12);
        assertThat(accessories.stream().filter(s -> s.parts().isEmpty())).hasSize(6);
    }

    @Test
    @DisplayName("시드의 품번·전산코드는 전부 합성 네임스페이스다 — 실값이 섞여 들어오지 않았다")
    void 시드에_실값이_없다() {
        Stream<String> codes = Stream.concat(
                        new VendorAExcelParser().parseSets(SEED_A).stream(),
                        new VendorBExcelParser().parseSets(SEED_B).stream())
                .flatMap(set -> Stream.concat(Stream.of(set.main()), set.parts().stream()))
                .map(VendorParsedItem::productCode)
                .filter(java.util.Objects::nonNull);

        // 부속 코드는 «대표품번-부속식별» 꼴로 조립되므로 접두어만 본다.
        // 실단가표에서 값을 베껴 오면 이 접두어를 벗어난다 — 그게 이 검사가 잡으려는 것이다.
        assertThat(codes).allSatisfy(code -> assertThat(code)
                .as("합성 시드의 코드는 D로 시작한다: %s", code)
                .startsWith("D"));
    }

    @Test
    @DisplayName("시드 파일은 데모 업로드 한도(2MB) 안에 들어간다")
    void 시드가_업로드_한도_안이다() throws IOException {
        // 방문자가 내려받아 그대로 다시 올려 볼 수 있어야 한다(D-9). 한도를 넘으면 그 자리에서 413이다.
        assertThat(Files.size(SEED_A)).isLessThan(2L * 1024 * 1024);
        assertThat(Files.size(SEED_B)).isLessThan(2L * 1024 * 1024);
    }
}
