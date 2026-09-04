package com.example.esti.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 테스트가 실DB를 건드리지 않는지 지킨다 (F-006).
 *
 * <p>{@code application.properties}의 데이터소스는 파일 DB({@code ./data/estimateDB})다.
 * {@code @SpringBootTest}가 이걸 그대로 열면 두 가지가 따라온다 —
 * 앱을 켜 둔 채 테스트를 돌리면 <b>Derby 락이 부딪혀 빌드가 깨지고</b>,
 * {@code ddl-auto=update}라 <b>실DB 스키마에 손을 댈 여지</b>가 생긴다.
 * QA 중 실제로 두 번 빌드를 막았다.
 *
 * <p>그래서 클래스마다 인메모리 URL로 덮는 것이 이 저장소의 관례다. 관례는 잊히므로 검사로 만든다.
 * 소스를 직접 읽는 이유는 <b>선언을 보기 위해서</b>다 — 런타임 환경이 아니라
 * "이 클래스가 무엇을 적어 놨는가"가 지켜야 할 규칙이다.
 *
 * <p>추상 베이스에 붙여 상속시키는 것도 괜찮다. 그런 하위 클래스는 자기 파일에
 * {@code @SpringBootTest}를 적지 않으므로 여기서 걸리지 않는다.
 */
class TestsDoNotTouchRealDbTest {

    private static final Path TEST_SOURCES = Path.of("src", "test", "java");
    private static final String IN_MEMORY_URL = "jdbc:derby:memory:";

    @Test
    void SpringBootTest를_선언한_클래스는_인메모리_DB를_쓴다() throws IOException {
        try (Stream<Path> paths = Files.walk(TEST_SOURCES)) {
            List<String> offenders = paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(TestsDoNotTouchRealDbTest::declaresSpringBootTest)
                    .filter(p -> !read(p).contains(IN_MEMORY_URL))
                    .map(TEST_SOURCES::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();

            assertThat(offenders)
                    .as("@SpringBootTest를 선언했는데 인메모리 DB 오버라이드가 없다 — "
                            + "'%s'로 시작하는 spring.datasource.url을 @TestPropertySource에 넣어라. "
                            + "그대로 두면 실DB(./data/estimateDB)를 열어 앱 실행 중에는 빌드가 깨진다", IN_MEMORY_URL)
                    .isEmpty();
        }
    }

    /** 클래스 선언에 붙은 것만 본다 — 주석이나 문자열 안의 언급은 세지 않는다. */
    private static boolean declaresSpringBootTest(Path path) {
        return read(path).lines().anyMatch(line -> line.stripLeading().startsWith("@SpringBootTest"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("테스트 소스를 읽지 못했다: " + path, e);
        }
    }
}
