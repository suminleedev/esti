package com.example.esti.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 API만 잠긴다 (D-7).
 *
 * <p>필터 체인은 «어디를 열고 어디를 잠갔나»가 전부라, 설정을 읽는 대신 <b>실제로 요청해서</b>
 * 확인한다. 여기서 조용히 뒤집히면 관리자 경로가 열리거나 반대로 화면 전체가 401이 된다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:adminsecurity;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.crawler.image-dir=target/test-product-images",
        "app.admin.username=admin",
        "app.admin.password=test-secret"
})
class AdminApiSecurityTest {

    @Autowired private TestRestTemplate rest;

    @Test
    @DisplayName("관리자 API는 자격 없이 부를 수 없다")
    void 관리자_API는_401() {
        ResponseEntity<String> response = rest.getForEntity("/api/admin/crawler/ASTD/status", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("자격이 맞으면 통과한다 — 401이 아닌 응답이 온다")
    void 자격이_맞으면_통과() {
        ResponseEntity<String> response = rest.withBasicAuth("admin", "test-secret")
                .getForEntity("/api/admin/crawler/ASTD/status", String.class);

        // 크롤러가 실제로 무엇을 답하든(200이든 오류든) 인증 단계는 통과했다는 것이 요점이다.
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("나머지 API는 잠기지 않는다 — 전면 로그인 화면이 아니다")
    void 일반_API는_그대로_열려_있다() {
        ResponseEntity<String> response = rest.getForEntity("/api/vendor-catalog/vendors", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("쓰기 요청이 CSRF에 막히지 않는다")
    void API_쓰기는_CSRF에_막히지_않는다() {
        // 브라우저 폼이 아니라 API 클라이언트가 부르는 자리다. 켜 두면 프론트의 저장이 전부 403이 된다.
        ResponseEntity<String> response = rest.postForEntity("/api/proposals/drafts",
                java.util.Map.of("projectName", "보안 검사"), String.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }
}
