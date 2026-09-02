package com.example.esti.crawler.common;

import com.example.esti.util.VectorImageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
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
    private final String userAgent;

    public ImageDownloadService(@Value("${app.crawler.image-dir}") String rootDir,
                                @Value("${app.crawler.user-agent}") String userAgent) {
        this.rootDir = Path.of(rootDir).toAbsolutePath().normalize();
        this.userAgent = userAgent;
    }

    /**
     * 이미지를 내려받아 저장한다.
     *
     * <p><b>확장자를 잃으면 엑셀 출력에서 이미지가 깨진다.</b> 제안서 출력은 파일 확장자로
     * POI 그림 타입을 정하므로({@code ProposalCardExcelWriter#pictureTypeOf}), PNG를 {@code .jpg}로
     * 저장하면 PNG 바이트를 JPEG라고 선언해 워크북에 넣게 된다. 화면은 브라우저가 내용으로
     * 판별해 멀쩡히 보이므로 <b>엑셀을 열어보기 전에는 드러나지 않는다.</b>
     *
     * @param preferredFileName 파일명 힌트. <b>이미 이미지 확장자가 붙어 있으면 그대로 쓴다.</b>
     *                          확장자가 없으면 응답 {@code Content-Type}으로 정한다 — 크롤러 호출부는
     *                          모두 확장자를 떼고 넘겨 이 판정을 타게 한다(C-5)
     */
    public DownloadResult download(String sourceUrl, String preferredFileName) throws Exception {
        Files.createDirectories(rootDir);

        String fileName = sanitize(preferredFileName);

        HttpURLConnection conn = (HttpURLConnection) new URL(sourceUrl).openConnection();
        conn.setConnectTimeout(5_000);
        conn.setReadTimeout(30_000);

        // HTML은 크롤러가 UA를 실어 보내는데 이미지는 기본 UA로 나가고 있었다. 지금은 양쪽 다 200이라
        // 무해하지만, 한쪽만 신원이 다른 상태라 hotlink 차단이 켜지면 이미지만 조용히 깨진다.
        if (userAgent != null && !userAgent.isBlank()) {
            conn.setRequestProperty("User-Agent", userAgent);
        }

        try (InputStream raw = conn.getInputStream();
             BufferedInputStream in = new BufferedInputStream(raw)) {

            // 응답 앞부분을 미리 읽어 실제 형식을 본다. 되돌려 놓으므로 저장에는 영향이 없다.
            in.mark(SNIFF_BYTES);
            byte[] head = in.readNBytes(SNIFF_BYTES);
            in.reset();

            if (!hasImageExtension(fileName)) {
                fileName += "." + resolveExtension(head, conn.getContentType(), sourceUrl);
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
     * 저장할 확장자를 정한다 — <b>실제 바이트가 1순위</b>, 응답 {@code Content-Type}이 2순위,
     * URL 경로가 3순위, 그래도 모르면 {@code jpg}.
     *
     * <p><b>왜 바이트가 Content-Type보다 먼저인가</b> — 서버가 틀리게 말하기 때문이다.
     * ASTD는 {@code Content-Type: image/jpeg}를 보내면서 PNG 바이트를 준다. 실측한 238장 중
     * <b>225장이 PNG였는데 전부 {@code .jpg}로 저장됐다.</b> 확장자가 내용과 어긋나면 제안서
     * 엑셀 출력이 깨진다 — 출력이 확장자로 POI 그림 타입을 정하므로 PNG 바이트를 JPEG라고
     * 선언해 워크북에 넣게 된다. 화면은 브라우저가 내용으로 판별해 멀쩡히 보이므로
     * <b>엑셀을 열어보기 전에는 드러나지 않는다.</b>
     *
     * <p>매직 넘버는 서버가 뭐라고 하든 파일 자신이 말하는 것이라 가장 믿을 만하다.
     */
    static String resolveExtension(byte[] head, String contentType, String sourceUrl) {
        String sniffed = extensionOfMagic(head);
        if (sniffed != null) {
            return sniffed;
        }

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

    /** 파일 앞머리의 매직 넘버로 형식을 판별한다. 모르는 형식이면 null. */
    static String extensionOfMagic(byte[] head) {
        if (head == null || head.length < 4) {
            return null;
        }

        if (startsWith(head, 0x89, 'P', 'N', 'G')) return "png";
        if (startsWith(head, 0xFF, 0xD8, 0xFF))    return "jpg";
        if (startsWith(head, 'G', 'I', 'F', '8'))  return "gif";

        // WEBP: "RIFF" + 파일크기 4바이트 + "WEBP"
        if (head.length >= 12
                && startsWith(head, 'R', 'I', 'F', 'F')
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "webp";
        }

        return null;
    }

    private static boolean startsWith(byte[] head, int... expected) {
        if (head.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((head[i] & 0xFF) != (expected[i] & 0xFF)) {
                return false;
            }
        }
        return true;
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

    /** 형식 판별에 필요한 앞머리 길이. WEBP가 12바이트를 본다. */
    private static final int SNIFF_BYTES = 16;

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