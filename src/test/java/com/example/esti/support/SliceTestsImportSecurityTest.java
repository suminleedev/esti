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
 * {@code @WebMvcTest} 슬라이스는 <b>앱의</b> 보안 설정을 물고 돌아야 한다 (D-7).
 *
 * <p>{@code @WebMvcTest}는 {@code @Configuration} 클래스를 자동으로 끌어오지 않는다. 그래서
 * {@code SecurityConfig}를 {@code @Import}하지 않으면 Boot의 <b>기본 보안</b>(전부 인증 + CSRF)이
 * 적용된다 — 앱은 {@code /api/admin/**}만 잠그고 나머지를 여는데, 슬라이스는 <b>앱과 다른 자세</b>로
 * 검사하게 된다.
 *
 * <p>증상이 고약하다. 관련 없어 보이는 컨트롤러 테스트가 한꺼번에 <b>403</b>으로 무너지는데,
 * 정작 바뀐 것은 그 컨트롤러가 아니다. 보안을 붙일 때 실제로 12건이 그렇게 깨졌다.
 *
 * <p>{@code TestsDoNotTouchRealDbTest}와 같은 이유로 <b>소스를 읽는다</b> — 지켜야 할 것이
 * 런타임 동작이 아니라 "이 클래스가 무엇을 적어 놨는가"이기 때문이다.
 */
class SliceTestsImportSecurityTest {

    private static final Path TEST_SOURCES = Path.of("src", "test", "java");

    @Test
    void WebMvcTest는_앱의_보안_설정을_물린다() throws IOException {
        try (Stream<Path> paths = Files.walk(TEST_SOURCES)) {
            List<String> offenders = paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> declares(p, "@WebMvcTest"))
                    .filter(p -> !read(p).contains("@Import(SecurityConfig.class)"))
                    .map(TEST_SOURCES::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();

            assertThat(offenders)
                    .as("@WebMvcTest에 '@Import(SecurityConfig.class)'가 없다 — "
                            + "그대로 두면 Boot 기본 보안이 붙어 이 클래스의 요청이 전부 403이 된다")
                    .isEmpty();
        }
    }

    /** 클래스 선언에 붙은 것만 본다 — 주석이나 문자열 안의 언급은 세지 않는다. */
    private static boolean declares(Path path, String annotation) {
        return read(path).lines().anyMatch(line -> line.stripLeading().startsWith(annotation));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("테스트 소스를 읽지 못했다: " + path, e);
        }
    }
}
