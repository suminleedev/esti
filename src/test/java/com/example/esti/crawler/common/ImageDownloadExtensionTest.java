package com.example.esti.crawler.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 저장 확장자 결정 규칙 ({@code plan-inus-crawler.md} I-6).
 *
 * <p>확장자를 잘못 붙이면 <b>엑셀 출력에서만</b> 드러난다 — 제안서 출력이 파일 확장자로
 * POI 그림 타입을 정하기 때문이다. 화면은 브라우저가 내용으로 판별해 멀쩡히 보인다.
 */
class ImageDownloadExtensionTest {

    @ParameterizedTest(name = "{0} → .{2}")
    @CsvSource({
            // Content-Type이 1순위다
            "image/png,             http://x/a.jpg,     png",
            "image/jpeg,            http://x/a.png,     jpg",
            "image/webp,            http://x/a.png,     webp",
            "image/gif,            http://x/a.png,     gif",
            // 파라미터가 붙어 와도 벗겨 낸다
            "'image/png; charset=binary', http://x/a,   png"
    })
    @DisplayName("Content-Type이 URL 확장자보다 우선한다")
    void prefersContentType(String contentType, String url, String expected) {
        assertThat(ImageDownloadService.resolveExtension(contentType, url)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → .{1}")
    @CsvSource({
            "http://x/upload/a.png,             png",
            "http://x/upload/a.JPG,             jpg",
            "http://x/upload/a.jpeg,            jpg",
            "http://x/upload/a.png?v=3,         png",
            "http://x/upload/a.png#frag,        png"
    })
    @DisplayName("Content-Type이 쓸모없으면 URL 경로의 확장자를 본다")
    void fallsBackToUrl(String url, String expected) {
        assertThat(ImageDownloadService.resolveExtension(null, url)).isEqualTo(expected);
        assertThat(ImageDownloadService.resolveExtension("application/octet-stream", url)).isEqualTo(expected);
    }

    @Test
    @DisplayName("둘 다 없으면 jpg로 둔다 — ASTD는 URL에 확장자가 아예 없다")
    void fallsBackToJpg() {
        // ASTD의 이미지 URL 모양. 확장자가 없고 Content-Type만으로 판별된다.
        assertThat(ImageDownloadService.resolveExtension(null, "http://x/img.do?v_product=333"))
                .isEqualTo("jpg");
        assertThat(ImageDownloadService.resolveExtension("image/jpeg", "http://x/img.do?v_product=333"))
                .isEqualTo("jpg");
        assertThat(ImageDownloadService.resolveExtension(null, null)).isEqualTo("jpg");
    }

    @Test
    @DisplayName("경로 없는 점(도메인의 점)을 확장자로 오인하지 않는다")
    void ignoresDotsOutsideTheLastSegment() {
        assertThat(ImageDownloadService.resolveExtension(null, "http://a.b.com/image")).isEqualTo("jpg");
    }
}
