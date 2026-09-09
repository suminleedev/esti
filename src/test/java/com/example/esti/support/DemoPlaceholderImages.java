package com.example.esti.support;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 데모 카탈로그가 쓰는 <b>대분류별 플레이스홀더 이미지</b>를 그린다 (G-3).
 *
 * <p>실제 제품 사진은 제조사 사이트에서 수집한 것이라 배포본에 얹지 않는다. 그렇다고 이미지를
 * 비워 두면 카탈로그도, <b>제안서 카드 출력도</b> 빈 칸이 된다 — 카드 출력이 이 프로젝트가
 * 보여줄 것 중 하나라 그 자리가 비면 곤란하다.
 *
 * <p><b>PNG인 이유.</b> SVG는 화면에서는 잘 나오지만 엑셀에 넣을 수 없다
 * ({@code ProposalCardExcelWriter}가 PNG·JPEG만 받는다). 화면과 출력에 같은 파일을 쓰려면
 * PNG여야 한다.
 *
 * <p>그림을 코드로 그리는 이유는 시드 엑셀과 같다 — <b>다시 만들 수 있어야</b> 하고,
 * 외부 도구(SVG 변환기 같은 것)에 기대지 않아야 한다.
 *
 * <pre>./mvnw test -Dtest=DemoSeedParseTest -Ddemo.seed.write=true</pre>
 */
public final class DemoPlaceholderImages {

    private DemoPlaceholderImages() {}

    private static final int SIZE = 240;

    private static final Color BACKGROUND = new Color(0xEE, 0xF2, 0xF7);
    private static final Color DARK = new Color(0x5B, 0x72, 0x90);
    private static final Color LIGHT = new Color(0x8F, 0xB3, 0xD9);

    /** 파일명(확장자 제외) → 그리는 법. {@code DemoSeedRunner}의 대분류 매핑과 이름이 맞아야 한다. */
    private static final Map<String, BiConsumer<Graphics2D, Integer>> ICONS = new LinkedHashMap<>();

    static {
        ICONS.put("toilet", DemoPlaceholderImages::toilet);
        ICONS.put("bidet", DemoPlaceholderImages::bidet);
        ICONS.put("basin", DemoPlaceholderImages::basin);
        ICONS.put("bath", DemoPlaceholderImages::bath);
        ICONS.put("faucet", DemoPlaceholderImages::faucet);
        ICONS.put("shower", DemoPlaceholderImages::shower);
        ICONS.put("accessory", DemoPlaceholderImages::accessory);
        ICONS.put("fitting", DemoPlaceholderImages::fitting);
        ICONS.put("product", DemoPlaceholderImages::product);
    }

    /** 아이콘 전부를 {@code <dir>/<이름>.png}로 쓴다. */
    public static void writeAll(Path dir) throws IOException {
        Files.createDirectories(dir);
        for (Map.Entry<String, BiConsumer<Graphics2D, Integer>> icon : ICONS.entrySet()) {
            BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, SIZE, SIZE);
            icon.getValue().accept(g, SIZE);
            g.dispose();
            ImageIO.write(image, "png", dir.resolve(icon.getKey() + ".png").toFile());
        }
    }

    /** 아이콘 이름들 — 테스트가 «빠진 파일이 없는지» 확인할 때 쓴다. */
    public static java.util.Set<String> names() {
        return ICONS.keySet();
    }

    // ============================================================
    // 아이콘 — 50px 썸네일에서도 알아볼 수 있게 굵은 실루엣만 쓴다.
    // ============================================================

    private static void toilet(Graphics2D g, int s) {
        g.setColor(LIGHT);
        g.fill(new RoundRectangle2D.Double(u(s, 52), u(s, 38), u(s, 68), u(s, 62), u(s, 10), u(s, 10)));
        g.setColor(DARK);
        g.fill(bowl(s));
        g.setColor(BACKGROUND);
        g.fill(new Ellipse2D.Double(u(s, 62), u(s, 116), u(s, 76), u(s, 40)));
        g.setColor(DARK);
        g.fill(new RoundRectangle2D.Double(u(s, 72), u(s, 190), u(s, 84), u(s, 16), u(s, 8), u(s, 8)));
    }

    private static void bidet(Graphics2D g, int s) {
        g.setColor(LIGHT);
        g.fill(new RoundRectangle2D.Double(u(s, 46), u(s, 42), u(s, 66), u(s, 56), u(s, 10), u(s, 10)));
        g.setColor(DARK);
        g.fill(new RoundRectangle2D.Double(u(s, 122), u(s, 48), u(s, 46), u(s, 46), u(s, 10), u(s, 10)));
        g.setColor(BACKGROUND);
        g.fill(new RoundRectangle2D.Double(u(s, 132), u(s, 60), u(s, 26), u(s, 7), u(s, 4), u(s, 4)));
        g.fill(new RoundRectangle2D.Double(u(s, 132), u(s, 76), u(s, 26), u(s, 7), u(s, 4), u(s, 4)));
        g.setColor(DARK);
        g.fill(bowl(s));
        g.setColor(BACKGROUND);
        g.fill(new Ellipse2D.Double(u(s, 62), u(s, 116), u(s, 76), u(s, 40)));
    }

    /** 양변기·비데가 함께 쓰는 몸통. */
    private static Path2D bowl(int s) {
        Path2D p = new Path2D.Double();
        p.moveTo(u(s, 44), u(s, 104));
        p.lineTo(u(s, 156), u(s, 104));
        p.curveTo(u(s, 156), u(s, 160), u(s, 128), u(s, 186), u(s, 96), u(s, 190));
        p.lineTo(u(s, 88), u(s, 206));
        p.lineTo(u(s, 74), u(s, 206));
        p.lineTo(u(s, 80), u(s, 186));
        p.curveTo(u(s, 56), u(s, 178), u(s, 44), u(s, 146), u(s, 44), u(s, 104));
        p.closePath();
        return p;
    }

    private static void basin(Graphics2D g, int s) {
        g.setColor(LIGHT);
        g.setStroke(stroke(s, 11));
        Path2D spout = new Path2D.Double();
        spout.moveTo(u(s, 104), u(s, 88));
        spout.lineTo(u(s, 104), u(s, 52));
        spout.curveTo(u(s, 104), u(s, 38), u(s, 128), u(s, 38), u(s, 128), u(s, 52));
        g.draw(spout);
        g.fill(new RoundRectangle2D.Double(u(s, 92), u(s, 84), u(s, 26), u(s, 16), u(s, 6), u(s, 6)));

        g.setColor(DARK);
        Path2D basin = new Path2D.Double();
        basin.moveTo(u(s, 36), u(s, 104));
        basin.lineTo(u(s, 204), u(s, 104));
        basin.lineTo(u(s, 204), u(s, 122));
        basin.curveTo(u(s, 204), u(s, 162), u(s, 168), u(s, 178), u(s, 120), u(s, 178));
        basin.curveTo(u(s, 72), u(s, 178), u(s, 36), u(s, 162), u(s, 36), u(s, 122));
        basin.closePath();
        g.fill(basin);
        g.setColor(BACKGROUND);
        g.fill(new Ellipse2D.Double(u(s, 46), u(s, 92), u(s, 148), u(s, 30)));
        g.setColor(DARK);
        g.fill(new RoundRectangle2D.Double(u(s, 106), u(s, 172), u(s, 28), u(s, 34), u(s, 6), u(s, 6)));
    }

    private static void bath(Graphics2D g, int s) {
        g.setColor(LIGHT);
        g.setStroke(stroke(s, 11));
        Path2D spout = new Path2D.Double();
        spout.moveTo(u(s, 58), u(s, 88));
        spout.lineTo(u(s, 58), u(s, 54));
        spout.curveTo(u(s, 58), u(s, 40), u(s, 82), u(s, 40), u(s, 82), u(s, 54));
        g.draw(spout);
        g.fill(new RoundRectangle2D.Double(u(s, 46), u(s, 84), u(s, 26), u(s, 16), u(s, 6), u(s, 6)));

        g.setColor(DARK);
        Path2D tub = new Path2D.Double();
        tub.moveTo(u(s, 28), u(s, 110));
        tub.lineTo(u(s, 212), u(s, 110));
        tub.lineTo(u(s, 212), u(s, 140));
        tub.curveTo(u(s, 212), u(s, 172), u(s, 190), u(s, 188), u(s, 158), u(s, 188));
        tub.lineTo(u(s, 82), u(s, 188));
        tub.curveTo(u(s, 50), u(s, 188), u(s, 28), u(s, 172), u(s, 28), u(s, 140));
        tub.closePath();
        g.fill(tub);
        g.setColor(BACKGROUND);
        g.fill(new RoundRectangle2D.Double(u(s, 40), u(s, 110), u(s, 160), u(s, 14), u(s, 7), u(s, 7)));
        g.setColor(DARK);
        g.fill(new RoundRectangle2D.Double(u(s, 54), u(s, 186), u(s, 16), u(s, 24), u(s, 6), u(s, 6)));
        g.fill(new RoundRectangle2D.Double(u(s, 170), u(s, 186), u(s, 16), u(s, 24), u(s, 6), u(s, 6)));
    }

    private static void faucet(Graphics2D g, int s) {
        g.setColor(DARK);
        g.setStroke(stroke(s, 22));
        Path2D body = new Path2D.Double();
        body.moveTo(u(s, 78), u(s, 176));
        body.lineTo(u(s, 78), u(s, 106));
        body.curveTo(u(s, 78), u(s, 62), u(s, 112), u(s, 54), u(s, 148), u(s, 54));
        body.lineTo(u(s, 166), u(s, 54));
        g.draw(body);
        g.fill(new RoundRectangle2D.Double(u(s, 50), u(s, 170), u(s, 56), u(s, 22), u(s, 11), u(s, 11)));

        g.setColor(LIGHT);
        g.fill(new RoundRectangle2D.Double(u(s, 150), u(s, 40), u(s, 26), u(s, 28), u(s, 9), u(s, 9)));
        g.fill(new Ellipse2D.Double(u(s, 146), u(s, 84), u(s, 34), u(s, 34)));
        g.setStroke(stroke(s, 11));
        g.draw(new java.awt.geom.Line2D.Double(u(s, 163), u(s, 196), u(s, 163), u(s, 210)));
        g.draw(new java.awt.geom.Line2D.Double(u(s, 144), u(s, 200), u(s, 144), u(s, 210)));
        g.draw(new java.awt.geom.Line2D.Double(u(s, 182), u(s, 200), u(s, 182), u(s, 210)));
    }

    private static void shower(Graphics2D g, int s) {
        g.setColor(DARK);
        g.setStroke(stroke(s, 18));
        Path2D arm = new Path2D.Double();
        arm.moveTo(u(s, 58), u(s, 38));
        arm.lineTo(u(s, 58), u(s, 76));
        arm.curveTo(u(s, 58), u(s, 100), u(s, 82), u(s, 108), u(s, 108), u(s, 108));
        g.draw(arm);
        g.fill(new Ellipse2D.Double(u(s, 104), u(s, 88), u(s, 88), u(s, 42)));

        g.setColor(LIGHT);
        g.setStroke(stroke(s, 12));
        int[] xs = {118, 140, 162, 184};
        int[] tops = {142, 148, 148, 142};
        int[] bottoms = {166, 178, 178, 166};
        for (int i = 0; i < xs.length; i++) {
            g.draw(new java.awt.geom.Line2D.Double(u(s, xs[i]), u(s, tops[i]), u(s, xs[i]), u(s, bottoms[i])));
        }
        g.draw(new java.awt.geom.Line2D.Double(u(s, 129), u(s, 186), u(s, 129), u(s, 198)));
        g.draw(new java.awt.geom.Line2D.Double(u(s, 173), u(s, 186), u(s, 173), u(s, 198)));
    }

    private static void accessory(Graphics2D g, int s) {
        g.setColor(DARK);
        g.fill(new RoundRectangle2D.Double(u(s, 36), u(s, 62), u(s, 168), u(s, 18), u(s, 9), u(s, 9)));
        g.fill(new RoundRectangle2D.Double(u(s, 36), u(s, 48), u(s, 18), u(s, 48), u(s, 9), u(s, 9)));
        g.fill(new RoundRectangle2D.Double(u(s, 186), u(s, 48), u(s, 18), u(s, 48), u(s, 9), u(s, 9)));

        g.setColor(LIGHT);
        Path2D towel = new Path2D.Double();
        towel.moveTo(u(s, 76), u(s, 80));
        towel.lineTo(u(s, 122), u(s, 80));
        towel.lineTo(u(s, 122), u(s, 176));
        towel.curveTo(u(s, 122), u(s, 188), u(s, 76), u(s, 188), u(s, 76), u(s, 176));
        towel.closePath();
        g.fill(towel);

        g.setColor(new Color(LIGHT.getRed(), LIGHT.getGreen(), LIGHT.getBlue(), 170));
        Path2D towel2 = new Path2D.Double();
        towel2.moveTo(u(s, 140), u(s, 80));
        towel2.lineTo(u(s, 174), u(s, 80));
        towel2.lineTo(u(s, 174), u(s, 148));
        towel2.curveTo(u(s, 174), u(s, 158), u(s, 140), u(s, 158), u(s, 140), u(s, 148));
        towel2.closePath();
        g.fill(towel2);
    }

    private static void fitting(Graphics2D g, int s) {
        g.setColor(DARK);
        g.setStroke(new BasicStroke((float) u(s, 28), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        Path2D pipe = new Path2D.Double();
        pipe.moveTo(u(s, 56), u(s, 188));
        pipe.lineTo(u(s, 56), u(s, 116));
        pipe.curveTo(u(s, 56), u(s, 78), u(s, 86), u(s, 56), u(s, 124), u(s, 56));
        pipe.lineTo(u(s, 186), u(s, 56));
        g.draw(pipe);

        g.setColor(LIGHT);
        g.fill(new RoundRectangle2D.Double(u(s, 34), u(s, 172), u(s, 44), u(s, 36), u(s, 6), u(s, 6)));
        g.fill(new RoundRectangle2D.Double(u(s, 168), u(s, 34), u(s, 36), u(s, 44), u(s, 6), u(s, 6)));
    }

    private static void product(Graphics2D g, int s) {
        double cx = u(s, 120);
        g.setColor(DARK);
        Path2D box = new Path2D.Double();
        box.moveTo(cx, u(s, 44));
        box.lineTo(u(s, 196), u(s, 82));
        box.lineTo(u(s, 196), u(s, 166));
        box.lineTo(cx, u(s, 204));
        box.lineTo(u(s, 44), u(s, 166));
        box.lineTo(u(s, 44), u(s, 82));
        box.closePath();
        g.fill(box);

        g.setColor(LIGHT);
        Path2D lid = new Path2D.Double();
        lid.moveTo(cx, u(s, 44));
        lid.lineTo(u(s, 196), u(s, 82));
        lid.lineTo(cx, u(s, 120));
        lid.lineTo(u(s, 44), u(s, 82));
        lid.closePath();
        g.fill(lid);

        g.setColor(BACKGROUND);
        g.setStroke(stroke(s, 8));
        g.draw(new java.awt.geom.Line2D.Double(cx, u(s, 120), cx, u(s, 204)));
    }

    /** 240 기준으로 그린 좌표를 실제 크기로 옮긴다. */
    private static double u(int size, double at240) {
        return at240 * size / 240.0;
    }

    private static BasicStroke stroke(int size, double width240) {
        return new BasicStroke((float) u(size, width240), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }
}
