package com.example.esti.crawler.common;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ImageDownloadService#download}의 저장 파일명 규칙.
 *
 * <p>외부 사이트를 타지 않는다 — JDK 내장 HTTP 서버를 루프백에 띄워 응답을 직접 만든다.
 *
 * <p>여기서 지키려는 것은 두 가지다. 하나는 확장자 없이 넘긴 이름이 응답 Content-Type을 따라간다는 것,
 * 다른 하나는 <b>ASTD 경로가 그대로라는 것</b>이다. ASTD 핸들러는 파일명에 {@code .jpg}를 직접
 * 붙여 넘기므로, 그런 이름은 Content-Type과 무관하게 건드리지 않아야 한다.
 */
class ImageDownloadServiceTest {

    /** 1x1 PNG. 내용이 중요한 게 아니라 응답 본문이 있으면 된다. */
    private static final byte[] PNG_BYTES = HexFormat.of().parseHex(
            "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4"
            + "890000000a49444154789c63000100000500010d0a2db40000000049454e44ae426082");

    private HttpServer server;
    private String baseUrl;

    @TempDir Path imageDir;

    private ImageDownloadService service;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            // 경로에 담긴 값을 그대로 Content-Type으로 돌려준다 (/type/image%2Fpng)
            String contentType = exchange.getRequestURI().getPath().substring("/type/".length());
            exchange.getResponseHeaders().add("Content-Type", java.net.URLDecoder.decode(
                    contentType.split("/img")[0], java.nio.charset.StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, PNG_BYTES.length);
            exchange.getResponseBody().write(PNG_BYTES);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        service = new ImageDownloadService(imageDir.toString());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String url(String contentType) {
        return baseUrl + "/type/" + contentType.replace("/", "%2F") + "/img";
    }

    @Test
    @DisplayName("확장자 없이 넘긴 이름은 Content-Type을 따라간다 — PNG를 .jpg로 저장하지 않는다")
    void usesContentTypeWhenNameHasNoExtension() throws Exception {
        ImageDownloadService.DownloadResult result = service.download(url("image/png"), "B_IC858RPG1");

        assertThat(result.relativePath()).endsWith("/B_IC858RPG1.png");
        assertThat(imageDir.resolve("B_IC858RPG1.png")).exists();
    }

    @Test
    @DisplayName("이미 확장자가 붙은 이름은 그대로 둔다 — ASTD 호출부는 .jpg를 직접 붙여 넘긴다")
    void keepsCallerProvidedExtension() throws Exception {
        // 응답이 PNG여도 파일명은 건드리지 않는다. 이 경로의 동작이 바뀌면 ASTD가 회귀한다.
        ImageDownloadService.DownloadResult result = service.download(url("image/png"), "A_AC8100.jpg");

        assertThat(result.relativePath()).endsWith("/A_AC8100.jpg");
        assertThat(imageDir.resolve("A_AC8100.jpg")).exists();
    }

    @Test
    @DisplayName("경로에 쓸 수 없는 문자는 걷어낸다 — 괄호가 든 품번이 있다")
    void sanitizesFileName() throws Exception {
        ImageDownloadService.DownloadResult result = service.download(url("image/jpeg"), "B_UB-FH6510(G)");

        assertThat(result.relativePath()).endsWith("/B_UB-FH6510_G_.jpg");
    }

    @Test
    @DisplayName("같은 이름으로 다시 받으면 덮어쓴다 — 재크롤링이 파일을 늘리지 않는다")
    void overwritesOnRecrawl() throws Exception {
        service.download(url("image/png"), "B_IC858RPG1");
        service.download(url("image/png"), "B_IC858RPG1");

        assertThat(imageDir.toFile().listFiles()).hasSize(1);
    }
}
