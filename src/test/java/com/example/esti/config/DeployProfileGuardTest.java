package com.example.esti.config;

import com.example.esti.controller.CrawlerAdminController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 배포 프로파일이 지켜야 할 것들을 검사로 만든다 (D-1·D-4·D-5·D-10).
 *
 * <p>여기 있는 규칙들은 전부 <b>「그렇게 하기로 했다」로만 유지되면 조용히 무너지는</b> 종류다.
 * 프로퍼티 한 줄이나 애너테이션 하나가 빠져도 앱은 멀쩡히 뜨고, 무너진 건 배포한 뒤에야 드러난다.
 *
 * <p>컨텍스트를 띄우지 않고 <b>선언을 읽는다</b> — 지켜야 할 규칙은 런타임 환경이 아니라
 * "파일에 무엇이 적혀 있는가"이기 때문이다({@code TestsDoNotTouchRealDbTest}와 같은 이유).
 */
class DeployProfileGuardTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @Test
    @DisplayName("데모 프로파일은 인메모리 DB만 연다 — 실 카탈로그 파일 DB를 열 경로가 없어야 한다")
    void 데모는_실DB를_열지_않는다() throws IOException {
        String url = demo().getProperty("spring.datasource.url");

        // 이 계획의 제1 원칙이다. 파일 DB에는 공급사 실단가와 전산코드가 들어 있어
        // 배포본이 그걸 열면 저장소에서 뺀 값을 웹으로 내보내는 셈이 된다.
        assertThat(url)
                .as("demo 프로파일의 datasource")
                .startsWith("jdbc:derby:memory:");
        assertThat(demo().getProperty("spring.jpa.hibernate.ddl-auto"))
                .as("재시작하면 초기 상태로 돌아가야 한다")
                .isEqualTo("create-drop");
    }

    @Test
    @DisplayName("데모 업로드 한도는 2MB 이하다")
    void 데모_업로드_한도() throws IOException {
        assertThat(demo().getProperty("spring.servlet.multipart.max-file-size")).isEqualTo("2MB");
        assertThat(demo().getProperty("spring.servlet.multipart.max-request-size")).isEqualTo("2MB");
    }

    @Test
    @DisplayName("데모 비동기 큐는 유한하다 — 연타하면 쌓이지 않고 거절된다")
    void 데모_비동기_큐는_유한하다() throws IOException {
        Properties demo = demo();
        assertThat(Integer.parseInt(demo.getProperty("spring.task.execution.pool.max-size"))).isEqualTo(1);
        assertThat(Integer.parseInt(demo.getProperty("spring.task.execution.pool.queue-capacity")))
                .isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("실사용 프로파일은 루프백에만 뜬다")
    void 실사용은_루프백_바인딩() throws IOException {
        assertThat(local().getProperty("server.address")).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("프로파일을 지정하지 않으면 실사용(local)이 뜬다")
    void 기본_프로파일은_local() throws IOException {
        assertThat(common().getProperty("spring.profiles.default")).isEqualTo("local");
        // 공통에는 datasource URL이 없어야 한다 — 있으면 프로파일을 안 줬을 때 어느 DB가 열리는지 흐려진다.
        assertThat(common().getProperty("spring.datasource.url")).isNull();
    }

    @Test
    @DisplayName("크롤러 컨트롤러는 데모에서 아예 뜨지 않는다")
    void 크롤러는_데모에서_비활성() {
        Profile profile = CrawlerAdminController.class.getAnnotation(Profile.class);

        // 인증으로 가리는 것과 빈을 만들지 않는 것은 다르다. 데모에서 외부 사이트로
        // 크롤 트래픽이 나가는 건 사고라, 경로 자체가 없어야 한다.
        assertThat(profile).as("@Profile 선언이 사라졌다").isNotNull();
        assertThat(profile.value()).containsExactly("!demo");
    }

    private Properties common() throws IOException { return read("application.properties"); }
    private Properties local() throws IOException { return read("application-local.properties"); }
    private Properties demo() throws IOException { return read("application-demo.properties"); }

    private Properties read(String name) throws IOException {
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(RESOURCES.resolve(name), StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        return props;
    }
}
