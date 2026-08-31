package com.example.esti.crawler.common;

import com.example.esti.util.VectorImageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Component
public class ImageDownloadService {

    private final Path rootDir;

    public ImageDownloadService(@Value("${app.crawler.image-dir}") String rootDir) {
        this.rootDir = Path.of(rootDir).toAbsolutePath().normalize();
    }

    /**
     * 이미지를 내려받아 저장한다.
     *
     * <p><b>확장자를 잃으면 엑셀 출력에서 이미지가 깨진다.</b> 제안서 출력은 파일 확장자로
     * POI 그림 타입을 정하므로({@code ProposalCardExcelWriter#pictureTypeOf}), PNG를 {@code .jpg}로
     * 저장하면 PNG 바이트를 JPEG라고 선언해 워크북에 넣게 된다. 화면은 브라우저가 내용으로
     * 판별해 멀쩡히 보이므로 <b>엑셀을 열어보기 전에는 드러나지 않는다.</b>
     *
     * @param preferredFileName 파일명 힌트. <b>이미 이미지 확장자가 붙어 있으면 그대로 쓴다</b> —
     *                          기존 호출부(ASTD)가 {@code ".jpg"}를 직접 붙여 넘기므로 동작이 바뀌지 않는다.
     *                          확장자가 없으면 응답 {@code Content-Type}으로 정한다
     */
    public DownloadResult download(String sourceUrl, String preferredFileName) throws Exception {
        Files.createDirectories(rootDir);

        String fileName = sanitize(preferredFileName);

        /* 이미지 재크롤링 시 기존 파일 덮어쓰기 방식으로 변경 */

        HttpURLConnection conn = (HttpURLConnection) new URL(sourceUrl).openConnection();
        conn.setConnectTimeout(5_000);
        conn.setReadTimeout(30_000);

        try (InputStream in = conn.getInputStream()) {
            if (!hasImageExtension(fileName)) {
                fileName += "." + resolveExtension(conn.getContentType(), sourceUrl);
            }

            Path target = rootDir.resolve(fileName);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);

            return new DownloadResult(
                    target.toAbsolutePath().toString(),
                    "/uploads/product-images/" + fileName
            );
        }
    }

    /**
     * 저장할 확장자를 정한다 — 응답 {@code Content-Type}이 1순위, URL 경로가 2순위, 없으면 {@code jpg}.
     *
     * <p><b>왜 URL이 아니라 Content-Type이 먼저인가</b> — 이누스는 URL에 확장자가 있지만
     * ASTD는 {@code img.do?v_product=333}처럼 아예 없다. Content-Type이 두 사이트의 공통 분모다.
     */
    static String resolveExtension(String contentType, String sourceUrl) {
        String fromType = extensionOfContentType(contentType);
        if (fromType != null) {
            return fromType;
        }

        String fromUrl = extensionOfUrl(sourceUrl);
        if (fromUrl != null) {
            return fromUrl;
        }

        return "jpg";
    }

    private static String extensionOfContentType(String contentType) {
        if (contentType == null) {
            return null;
        }

        // "image/jpeg; charset=..." 처럼 파라미터가 붙어 온다
        String type = contentType.split(";")[0].trim().toLowerCase();

        return switch (type) {
            case "image/png" -> "png";
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> null;
        };
    }

    private static String extensionOfUrl(String sourceUrl) {
        if (sourceUrl == null) {
            return null;
        }

        // 쿼리·프래그먼트를 떼고 마지막 경로 조각만 본다
        String path = sourceUrl.split("[?#]")[0];
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot < path.lastIndexOf('/')) {
            return null;
        }

        String ext = path.substring(dot + 1).toLowerCase();
        return KNOWN_EXTENSIONS.contains(ext) ? normalizeExtension(ext) : null;
    }

    private static String normalizeExtension(String ext) {
        return "jpeg".equals(ext) ? "jpg" : ext;
    }

    /**
     * 메모리상의 이미지 바이트를 저장한다(엑셀 임베디드 이미지용). 동일 파일명은 덮어쓰기 → 재업로드 멱등.
     *
     * <p>EMF/WMF는 브라우저가 렌더링하지 못하므로 저장 전에 PNG로 변환한다(P0). 변환에 실패하면
     * 원본 확장자 그대로 저장한다 — 이미지 한 건 때문에 적재가 막히지 않게 하기 위함이다.
     *
     * @param preferredFileName 확장자 없는 파일명 힌트(예: 품번)
     * @param ext               확장자(jpeg/png 등, null이면 jpg)
     */
    public DownloadResult saveBytes(byte[] data, String preferredFileName, String ext) throws Exception {
        Files.createDirectories(rootDir);

        if (VectorImageConverter.isVectorFormat(ext)) {
            byte[] png = VectorImageConverter.toPng(data, ext);
            if (png != null) {
                data = png;
                ext = "png";
            }
        }

        String fileName = sanitize(preferredFileName);
        if (!hasImageExtension(fileName)) {
            fileName += "." + (ext == null || ext.isBlank() ? "jpg" : ext);
        }

        Path target = rootDir.resolve(fileName);
        Files.write(target, data);

        return new DownloadResult(
                target.toAbsolutePath().toString(),
                "/uploads/product-images/" + fileName
        );
    }

    private static final Set<String> KNOWN_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "webp", "gif");

    private boolean hasImageExtension(String fileName) {
        String lower = fileName.toLowerCase();
        int dot = lower.lastIndexOf('.');
        return dot >= 0 && KNOWN_EXTENSIONS.contains(lower.substring(dot + 1));
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return value.replaceAll("[^a-zA-Z0-9가-힣._-]", "_");
    }

    public record DownloadResult(String absolutePath, String relativePath) {
    }
}