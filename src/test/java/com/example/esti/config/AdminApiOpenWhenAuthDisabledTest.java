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
 * 잠금을 끄면 관리자 경로가 자격 없이 열린다 — 실사용({@code local})의 동작이다.
 *
 * <p>크롤러 실행은 사람이 손으로 돌리는 명령인데, 실사용은 루프백에만 열린 단일 사용자 실행이라
 * 자물쇠가 막는 상대가 자기 자신뿐이다. 그래서 {@code local}만 끈다.
 *
 * <p>이 검사가 있어야 하는 이유는 <b>반대쪽 회귀</b>다 — 잠금을 손보다가 «끈 상태»가 동작을
 * 멈추면, 크롤러를 돌리려는 순간에야 401로 알게 된다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:adminopen;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.crawler.image-dir=target/test-product-images",
        "app.admin.auth-enabled=false"
})
class AdminApiOpenWhenAuthDisabledTest {

    @Autowired private TestRestTemplate rest;

    @Test
    @DisplayName("잠금을 끄면 관리자 API가 자격 없이 열린다")
    void 관리자_API가_열린다() {
        ResponseEntity<String> response = rest.getForEntity("/api/admin/crawler/ASTD/status", String.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("잠금을 꺼도 나머지 API는 그대로다")
    void 일반_API도_그대로() {
        assertThat(rest.getForEntity("/api/vendor-catalog/vendors", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
