package com.example.esti.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EMF→PNG 변환기 테스트.
 *
 * <p>실제 제품 EMF는 저장소에 없으므로(업로드 산출물) <b>합성 EMF</b>를 런타임에 만들어 쓴다
 * ({@code SyntheticBCatalogParseTest}와 같은 방침 — CI에서 항상 실행된다).
 *
 * <p>출력 픽셀 수를 상수로 못 박지 않고 <b>긴 변의 상·하한과 종횡비</b>로 검증한다.
 * 프레임 단위→포인트 환산은 POI 내부 구현이라 버전에 따라 달라질 수 있다.
 */
class VectorImageConverterTest {

    /** 긴 변 상한(px). {@code VectorImageConverter.MAX_LONG_EDGE}와 맞춘다. */
    private static final int MAX_LONG_EDGE = 1000;
    /** 긴 변 하한(px). {@code VectorImageConverter.MIN_LONG_EDGE}와 맞춘다. */
    private static final int MIN_LONG_EDGE = 64;

    @Test
    @DisplayName("isVectorFormat: emf/wmf만 true, 대소문자·점 접두 허용")
    void isVectorFormat_판정() {
        assertTrue(VectorImageConverter.isVectorFormat("emf"));
        assertTrue(VectorImageConverter.isVectorFormat("EMF"));
        assertTrue(VectorImageConverter.isVectorFormat(".emf"));
        assertTrue(VectorImageConverter.isVectorFormat("wmf"));

        assertFalse(VectorImageConverter.isVectorFormat("png"));
        assertFalse(VectorImageConverter.isVectorFormat("jpeg"));
        assertFalse(VectorImageConverter.isVectorFormat(null));
        assertFalse(VectorImageConverter.isVectorFormat("  "));
    }

    @Test
    @DisplayName("EMF를 PNG로 변환하고 종횡비를 유지한다")
    void toPng_변환_성공() throws Exception {
        // 가로:세로 = 2:1, 96dpi에서 384px → 288pt → 150dpi 렌더 시 600px
        byte[] png = VectorImageConverter.toPng(syntheticEmf(384, 192), "emf");

        assertNotNull(png, "변환 결과가 null이면 안 된다");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(image, "출력이 PNG로 읽혀야 한다");

        assertEquals(2.0, (double) image.getWidth() / image.getHeight(), 0.05, "종횡비 유지");
        int longEdge = Math.max(image.getWidth(), image.getHeight());
        assertTrue(longEdge >= MIN_LONG_EDGE && longEdge <= MAX_LONG_EDGE,
                "긴 변이 " + MIN_LONG_EDGE + "~" + MAX_LONG_EDGE + " 범위여야 한다: " + longEdge);
    }

    @Test
    @DisplayName("큰 원본은 긴 변 상한으로 축소한다 (용량 폭주 방지)")
    void toPng_상한_클램프() throws Exception {
        byte[] png = VectorImageConverter.toPng(syntheticEmf(4000, 2000), "emf");

        assertNotNull(png);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(MAX_LONG_EDGE, Math.max(image.getWidth(), image.getHeight()));
        assertEquals(2.0, (double) image.getWidth() / image.getHeight(), 0.05);
    }

    @Test
    @DisplayName("작은 원본은 긴 변 하한까지 키운다 (식별 가능한 최소치)")
    void toPng_하한_클램프() throws Exception {
        byte[] png = VectorImageConverter.toPng(syntheticEmf(40, 20), "emf");

        assertNotNull(png);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(MIN_LONG_EDGE, Math.max(image.getWidth(), image.getHeight()));
    }

    @Test
    @DisplayName("깨진 EMF·비벡터·빈 입력은 예외 없이 null (원본 유지 신호)")
    void toPng_실패는_null() {
        assertNull(VectorImageConverter.toPng("EMF가 아님".getBytes(StandardCharsets.UTF_8), "emf"),
                "깨진 EMF는 예외 대신 null");
        assertNull(VectorImageConverter.toPng(syntheticEmf(384, 192), "png"),
                "벡터 포맷이 아니면 변환하지 않는다");
        assertNull(VectorImageConverter.toPng(new byte[0], "emf"));
        assertNull(VectorImageConverter.toPng(null, "emf"));
    }

    /**
     * 최소 유효 EMF를 만든다 — {@code EMR_HEADER} + {@code EMR_EOF} 두 레코드뿐인 빈 그림.
     *
     * <p>POI의 {@code getSize()}는 {@code rclFrame}이 아니라 <b>{@code rclBounds}(장치 픽셀)</b>를
     * {@code szlDevice}/{@code szlMillimeters}가 정하는 DPI로 환산해 얻는다. 여기서는 장치 해상도를
     * 96dpi(1920px / 508mm)로 고정해 두었으므로 {@code 포인트 = boundsPx × 0.75}가 된다.
     *
     * @param boundsW rclBounds 가로(장치 px)
     * @param boundsH rclBounds 세로(장치 px)
     */
    private static byte[] syntheticEmf(int boundsW, int boundsH) {
        final int headerSize = 88;
        final int eofSize = 20;
        // rclFrame(.01mm)은 bounds와 앞뒤가 맞게 96dpi 기준으로 환산해 채운다.
        final int frameW = (int) Math.round(boundsW / 96.0 * 2540);
        final int frameH = (int) Math.round(boundsH / 96.0 * 2540);

        ByteBuffer b = ByteBuffer.allocate(headerSize + eofSize).order(ByteOrder.LITTLE_ENDIAN);

        // EMR_HEADER
        b.putInt(1);                    // iType
        b.putInt(headerSize);           // nSize
        b.putInt(0).putInt(0).putInt(boundsW).putInt(boundsH);   // rclBounds (device px)
        b.putInt(0).putInt(0).putInt(frameW).putInt(frameH);     // rclFrame (.01mm)
        b.putInt(0x464D4520);           // dSignature = " EMF"
        b.putInt(0x00010000);           // nVersion
        b.putInt(headerSize + eofSize); // nBytes
        b.putInt(2);                    // nRecords
        b.putShort((short) 0);          // nHandles
        b.putShort((short) 0);          // sReserved
        b.putInt(0);                    // nDescription
        b.putInt(0);                    // offDescription
        b.putInt(0);                    // nPalEntries
        b.putInt(1920).putInt(1080);    // szlDevice (px)
        b.putInt(508).putInt(286);      // szlMillimeters → 96dpi

        // EMR_EOF
        b.putInt(14);                   // iType
        b.putInt(eofSize);              // nSize
        b.putInt(0);                    // nPalEntries
        b.putInt(16);                   // offPalEntries
        b.putInt(eofSize);              // nSizeLast

        return b.array();
    }
}
