package com.example.esti.support;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 공급사 <b>전산코드</b>의 단일 출처.
 *
 * <p>전산코드는 공급사 내부 식별자라 대외 공개용이 아니다. 단가와 같은 이유로 저장소에 두지 않고
 * 원본 엑셀 옆({@code docs/samples/}, gitignore)에 둔다. 테스트는 뜻이 드러나는 별칭으로 지목한다 —
 * {@code byCode(sets, code("수전부속.냉수구형"))}. 원시 코드보다 무엇을 검증하는지가 오히려 잘 보인다.
 *
 * <p>부재 처리 정책은 {@link TestSamples}·{@link ExpectedPrices}와 같다 — 로컬 스킵 / CI(strict) fail(§15).
 *
 * @see ExpectedPrices 금액 기대값
 */
public final class ExpectedCodes {

    public static final Path FILE = Path.of("docs/samples/expected-codes.properties");

    private static Properties cache;

    private ExpectedCodes() {}

    /** 별칭 → 전산코드. 키가 없으면 fail — 오타를 조용히 넘기지 않는다. */
    public static String code(String alias) {
        String v = load().getProperty(alias);
        if (v == null) {
            Assertions.fail("전산코드 별칭 없음: " + alias + " (" + FILE + ")");
        }
        return v.trim();
    }

    /** 원본에서 그대로 온 기대 문자열(비고 등) — 코드·금액이 섞여 있어 저장소에 두지 않는다. */
    public static String text(String alias) {
        return code(alias);
    }

    private static Properties load() {
        if (cache != null) return cache;
        if (!Files.exists(FILE)) {
            String msg = "전산코드 사전 없음: " + FILE;
            if (TestSamples.strict()) {
                Assertions.fail(msg + " — CI(strict)에선 스킵을 fail로 승격(§15). "
                        + "샘플과 함께 사전도 CI에 제공해야 한다.");
            }
            Assumptions.abort(msg + " (로컬 스킵)");
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("전산코드 사전을 읽을 수 없다: " + FILE, e);
        }
        cache = p;
        return cache;
    }
}
