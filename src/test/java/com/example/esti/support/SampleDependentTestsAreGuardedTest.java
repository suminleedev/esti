package com.example.esti.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 샘플을 직접 만지는 테스트에 부재 가드가 있는지 지킨다 (F-004).
 *
 * <p>{@code docs/samples/}는 gitignore라 새 클론·워크트리·다른 사람 컴퓨터에는 없다.
 * 그래서 샘플을 여는 테스트는 {@link TestSamples#requireSample}로 «없으면 로컬 스킵 / CI fail»을
 * 먼저 선언해야 한다. 대부분은 {@code setsOf(...)} 같은 헬퍼가 대신 불러 주는데,
 * <b>헬퍼를 안 거치고 직접 파싱하는 메서드가 둘 있었다.</b> 뒤에 {@code assumeTrue}가 있었지만
 * 그 앞줄에서 이미 {@code NoSuchFileException}이 터져 도달하지 못했다 — 스킵 의도만 있고
 * 가드는 죽어 있던 셈이다. 샘플 없는 트리에서 빌드가 통째로 깨졌다.
 *
 * <p>규칙은 하나다 — <b>{@code @Test} 본문이 {@code SAMPLE}을 직접 쓰면 {@code requireSample}도 부른다.</b>
 * 헬퍼만 쓰는 메서드는 본문에 {@code SAMPLE}이 안 나오므로 여기 걸리지 않는다.
 *
 * <p>CI가 없어 «샘플 없는 트리에서 돌려 보기»를 자동으로 할 데가 없다. 그래서 정적으로 본다.
 */
class SampleDependentTestsAreGuardedTest {

    private static final Path TEST_SOURCES = Path.of("src", "test", "java");

    /**
     * {@code @Test}로 시작하되 {@code @TestPropertySource} 같은 더 긴 이름은 아닌 것.
     * (이걸 구분하지 않으면 클래스 헤더가 테스트 메서드로 잡힌다 — 실제로 한 번 헛짚었다.)
     */
    private static final Pattern TEST_ANNOTATION = Pattern.compile("@Test\\b(?!\\w)");
    private static final Pattern METHOD_NAME = Pattern.compile("void\\s+(\\w+)\\s*\\(");
    private static final Pattern SAMPLE_REF = Pattern.compile("\\bSAMPLE\\b");

    @Test
    void 샘플을_직접_여는_테스트는_requireSample로_가드한다() throws IOException {
        try (Stream<Path> paths = Files.walk(TEST_SOURCES)) {
            List<String> offenders = paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .flatMap(p -> unguardedMethods(p).stream())
                    .sorted()
                    .toList();

            assertThat(offenders)
                    .as("샘플(SAMPLE)을 직접 쓰는데 requireSample(SAMPLE)이 없다 — "
                            + "메서드 첫 줄에 넣어라. 그대로 두면 docs/samples가 없는 트리"
                            + "(새 클론·워크트리)에서 스킵이 아니라 빌드 실패가 된다")
                    .isEmpty();
        }
    }

    /** 한 파일에서 «SAMPLE을 직접 쓰는데 가드가 없는» @Test 메서드 이름들. */
    private static List<String> unguardedMethods(Path path) {
        String source = read(path);
        if (!source.contains("docs/samples/")) return List.of();

        List<String> offenders = new ArrayList<>();
        Matcher tests = TEST_ANNOTATION.matcher(source);
        while (tests.find()) {
            String body = methodBody(source, tests.end());
            if (!SAMPLE_REF.matcher(body).find()) continue;      // 헬퍼만 쓰는 메서드
            if (body.contains("requireSample")) continue;        // 가드 있음

            Matcher name = METHOD_NAME.matcher(body);
            offenders.add(TEST_SOURCES.relativize(path) + " :: "
                    + (name.find() ? name.group(1) : "(이름 미상)"));
        }
        return offenders;
    }

    /** 애노테이션 다음부터 «들여쓴 닫는 중괄호»까지를 메서드 본문으로 본다. */
    private static String methodBody(String source, int from) {
        int end = source.indexOf("\n    }", from);
        return end < 0 ? source.substring(from) : source.substring(from, end);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("테스트 소스를 읽지 못했다: " + path, e);
        }
    }
}
