package com.example.esti.util;

import org.apache.poi.hemf.usermodel.HemfPicture;
import org.apache.poi.hwmf.usermodel.HwmfPicture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;

/**
 * EMF/WMF 벡터 이미지를 PNG 래스터로 변환한다.
 *
 * <p>엑셀 임베디드 이미지에는 EMF가 섞여 들어오는데, 브라우저는 {@code <img>}로 렌더링하지 못하고
 * POI로 엑셀에 다시 넣어도 뷰어에 따라 깨진다. 적재 시점에 PNG로 바꿔 두면 화면·엑셀 양쪽이 함께 해결된다.
 *
 * <p>렌더링은 POI {@code poi-scratchpad}의 HEMF/HWMF 구현을 쓴다. 엑셀 파싱에 이미 쓰는 POI와
 * 같은 버전이라 의존성이 늘지 않는다.
 *
 * <p>출력 크기는 원본의 <b>포인트 크기</b>를 {@value #TARGET_DPI} DPI로 환산해 정한다.
 * 고정 크기로 뽑으면 작은 원본(예: 50pt짜리 샤워헤드)이 9배 확대돼 화질은 그대로인 채 용량만 커진다.
 */
public final class VectorImageConverter {

    private static final Logger logger = LoggerFactory.getLogger(VectorImageConverter.class);

    /** 렌더링 목표 해상도. 엑셀 인쇄(보통 150~200dpi) 기준으로 충분하다. */
    private static final int TARGET_DPI = 150;
    private static final double POINTS_PER_INCH = 72.0;

    /** 긴 변 기준 하한/상한(px). 상한은 용량 폭주 방지, 하한은 육안 식별 가능한 최소치. */
    private static final int MIN_LONG_EDGE = 64;
    private static final int MAX_LONG_EDGE = 1000;

    private VectorImageConverter() {
    }

    /** 확장자가 EMF/WMF인가. null·공백은 false. */
    public static boolean isVectorFormat(String ext) {
        if (ext == null || ext.isBlank()) return false;
        String normalized = ext.toLowerCase(Locale.ROOT).replace(".", "").trim();
        return "emf".equals(normalized) || "wmf".equals(normalized);
    }

    /**
     * EMF/WMF 바이트를 PNG 바이트로 변환한다.
     *
     * @return 변환된 PNG 바이트. 벡터 포맷이 아니거나 변환에 실패하면 {@code null}
     *         (호출부가 원본을 그대로 저장하도록 두기 위해 예외를 던지지 않는다)
     */
    public static byte[] toPng(byte[] data, String ext) {
        if (data == null || data.length == 0 || !isVectorFormat(ext)) return null;

        boolean emf = ext.toLowerCase(Locale.ROOT).replace(".", "").trim().equals("emf");
        try (InputStream in = new ByteArrayInputStream(data)) {
            Dimension2D sizeInPoints;
            Renderer renderer;

            if (emf) {
                HemfPicture pic = new HemfPicture(in);
                sizeInPoints = pic.getSize();
                renderer = pic::draw;
            } else {
                HwmfPicture pic = new HwmfPicture(in);
                sizeInPoints = pic.getSize();
                renderer = pic::draw;
            }

            int[] wh = targetSize(sizeInPoints);
            BufferedImage image = new BufferedImage(wh[0], wh[1], BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                // 제품 이미지 배경은 흰색이 기본이다. 투명 채널 없이 흰 바탕을 깔아 엑셀에서도 동일하게 보이게 한다.
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, wh[0], wh[1]);
                renderer.draw(g, new Rectangle2D.Double(0, 0, wh[0], wh[1]));
            } finally {
                g.dispose();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) {
                logger.warn("[벡터변환] PNG 인코더 없음 → 원본 유지");
                return null;
            }
            return out.toByteArray();
        } catch (Throwable e) {
            // 변환 실패가 카탈로그 적재를 막으면 안 된다. 원본을 그대로 두고 진행한다.
            logger.warn("[벡터변환] {} 변환 실패(원본 유지): {}", ext, e.toString());
            return null;
        }
    }

    /** 포인트 크기 → 렌더링 픽셀 크기. 긴 변을 {@link #MIN_LONG_EDGE}~{@link #MAX_LONG_EDGE}로 가둔다. */
    private static int[] targetSize(Dimension2D sizeInPoints) {
        double wPt = sizeInPoints == null ? 0 : sizeInPoints.getWidth();
        double hPt = sizeInPoints == null ? 0 : sizeInPoints.getHeight();
        if (wPt <= 0 || hPt <= 0) {
            // 크기를 못 읽는 원본은 정사각 기본값으로 그린다(빈 이미지보다 낫다).
            return new int[]{MAX_LONG_EDGE / 2, MAX_LONG_EDGE / 2};
        }

        double scale = TARGET_DPI / POINTS_PER_INCH;
        double w = wPt * scale;
        double h = hPt * scale;

        double longEdge = Math.max(w, h);
        if (longEdge > MAX_LONG_EDGE) {
            double k = MAX_LONG_EDGE / longEdge;
            w *= k;
            h *= k;
        } else if (longEdge < MIN_LONG_EDGE) {
            double k = MIN_LONG_EDGE / longEdge;
            w *= k;
            h *= k;
        }
        return new int[]{Math.max(1, (int) Math.round(w)), Math.max(1, (int) Math.round(h))};
    }

    /** HEMF/HWMF의 draw 시그니처가 같아 하나로 묶는다. */
    @FunctionalInterface
    private interface Renderer {
        void draw(Graphics2D g, Rectangle2D bounds);
    }
}
