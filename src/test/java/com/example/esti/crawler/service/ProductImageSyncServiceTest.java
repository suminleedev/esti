package com.example.esti.crawler.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 6 — INUS 크롤러 비활성화 확인.
 *
 * <p>기존에는 INUS 동기화가 페이지만 fetch하고 아무것도 저장하지 않으면서 "완료" 응답을 냈다(거짓 신호).
 * 빈 등록을 빼면 {@code syncByMaker}의 미지원 분기가 작동해 명시적 오류가 된다.
 *
 * <p>크롤러 목록 필터에서 예외가 나므로 <b>네트워크에 접근하지 않는다.</b>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:inusDisabledTest;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.crawler.image-dir=target/test-product-images"
})
class ProductImageSyncServiceTest {

    @Autowired private ProductImageSyncService service;

    @Test
    @DisplayName("INUS는 미구현이므로 조용한 no-op이 아니라 명시적 오류로 거부된다")
    void INUS는_미구현이므로_명시적_오류로_거부된다() {
        assertThatThrownBy(() -> service.syncByMaker("INUS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 제조사");
    }
}
