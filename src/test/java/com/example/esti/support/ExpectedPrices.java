package com.example.esti.support;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 공급사 <b>금액 기대값</b>의 단일 출처.
 *
 * <p>단가·세트가는 공급사의 영업 정보라 저장소에 두지 않는다. 원본 엑셀이 이미
 * {@code docs/samples/}(gitignore)에만 있으므로, 그 값을 대조하는 기대값도 같은 자리에 둔다.
 * 이 테스트들은 어차피 샘플이 없으면 {@link TestSamples#requireSample}로 스킵되므로
 * 기대값을 밖으로 빼도 실행 가능 범위가 줄지 않는다.
 *
 * <p>부재 처리 정책은 {@link TestSamples}와 같다 — 로컬은 스킵, CI(strict)는 fail(§15).
 *
 * <p>키는 품번을 그대로 쓴다({@code AC8100}). 한 품번에 값이 여럿이면 점으로 뒤에 붙인다
 * ({@code IL672.도기}, {@code G-0130.분계표}). 매직넘버보다 무엇을 대조하는지가 드러난다.
 */
public final class ExpectedPrices {

    /** 샘플과 같은 자리에 둔다 — 둘 다 gitignore 대상이라 한 벌로 움직인다. */
    public static final Path FILE = Path.of("docs/samples/expected-prices.properties");

    private static Properties cache;

    private ExpectedPrices() {}

    /** 기대 금액. 키가 없으면 fail — 오타를 조용히 넘기지 않는다. */
    public static BigDecimal price(String key) {
        String v = load().getProperty(key);
        if (v == null) {
            Assertions.fail("기대값 키 없음: " + key + " (" + FILE + ")");
        }
        return new BigDecimal(v.trim());
    }

    /** {@link #price}와 같되 {@code long}이 필요한 자리용. */
    public static long priceLong(String key) {
        return price(key).longValueExact();
    }

    private static Properties load() {
        if (cache != null) return cache;
        if (!Files.exists(FILE)) {
            String msg = "기대값 파일 없음: " + FILE;
            if (TestSamples.strict()) {
                Assertions.fail(msg + " — CI(strict)에선 스킵을 fail로 승격(§15). "
                        + "샘플과 함께 기대값 파일도 CI에 제공해야 한다.");
            }
            Assumptions.abort(msg + " (로컬 스킵)");
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("기대값 파일을 읽을 수 없다: " + FILE, e);
        }
        cache = p;
        return cache;
    }
}
