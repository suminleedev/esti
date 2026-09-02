package com.example.esti.output;

import com.example.esti.entity.Proposal;
import com.example.esti.entity.ProposalLine;
import com.example.esti.support.TestSamples;
import org.apache.poi.ss.usermodel.PictureData;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 제안서 엑셀에 실제로 들어간 그림의 <b>선언 타입과 내용이 맞는지</b> 본다.
 *
 * <p>출력은 파일 확장자로 POI 그림 타입을 정한다({@code pictureTypeOf}). 확장자가 내용과
 * 어긋나면 PNG 바이트를 JPEG라고 선언해 워크북에 넣게 되고, <b>엑셀을 열어보기 전에는
 * 드러나지 않는다.</b> 화면은 브라우저가 내용으로 판별해 멀쩡히 보이기 때문이다.
 *
 * <p>실제로 그런 일이 있었다 — 사이트가 {@code Content-Type: image/jpeg}를 보내면서
 * PNG를 줘서 내려받은 238장 중 225장이 {@code .jpg}로 저장됐다. 다운로더는 고쳤고,
 * 이 테스트는 <b>출력 쪽에서 같은 어긋남을 잡는다.</b>
 */
class ProposalCardImageTypeTest {

    private static final Path IMAGE_DIR = Path.of("uploads/product-images");

    private ProposalLine lineWithImage(String imageUrl) {
        ProposalLine line = new ProposalLine();
        line.setProductName("검증용 품목");
        line.setVendorCode("A");
        line.setImageUrl(imageUrl);
        line.setArea("욕실1");
        line.setCategory("양변기");
        line.setOptional(false);
        line.setQty(1);
        line.setUnitPrice(BigDecimal.ONE);
        line.setAmount(BigDecimal.ONE);
        return line;
    }

    private Proposal proposal() {
        Proposal p = new Proposal();
        p.setProjectName("이미지 타입 검증");
        return p;
    }

    /**
     * 워크북에 담긴 그림을 "선언 타입 → 실제 매직 넘버"로 옮겨 적는다.
     *
     * <p>워크북을 닫으면 그림 바이트를 못 읽으므로 판정까지 이 안에서 끝낸다.
     */
    private List<String> embeddedPictureTypes(byte[] xlsx) throws Exception {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            return wb.getAllPictures().stream().map(this::describe).toList();
        }
    }

    private String describe(PictureData picture) {
        String declared = switch (picture.getPictureType()) {
            case Workbook.PICTURE_TYPE_PNG -> "png";
            case Workbook.PICTURE_TYPE_JPEG -> "jpg";
            default -> "other";
        };
        return declared + "/" + magicOf(picture.getData());
    }

    private String magicOf(byte[] data) {
        if (data.length >= 4 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "png";
        }
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            return "jpg";
        }
        return "other";
    }

    @Test
    @DisplayName("내려받은 A사 이미지를 넣은 제안서에서 선언 타입과 실제 바이트가 일치한다")
    void embeddedPicturesDeclareTheirRealType() throws Exception {
        TestSamples.requireSample(IMAGE_DIR);

        List<Path> images;
        try (Stream<Path> files = Files.list(IMAGE_DIR)) {
            images = files.filter(p -> p.getFileName().toString().startsWith("A_"))
                    .sorted()
                    .limit(20)
                    .toList();
        }
        if (images.isEmpty()) {
            TestSamples.requireSample(IMAGE_DIR.resolve("A_없음"));  // 로컬 스킵 / CI fail
        }

        List<ProposalLine> lines = images.stream()
                .map(p -> lineWithImage("/uploads/product-images/" + p.getFileName()))
                .toList();

        byte[] xlsx = ProposalCardExcelWriter.write(proposal(), lines);
        List<String> types = embeddedPictureTypes(xlsx);

        // 그림이 하나도 안 들어갔으면 아래 검사가 공허해진다. 먼저 들어갔는지부터 본다.
        assertThat(types)
                .as("제안서에 이미지가 실제로 담겨야 한다 — 비어 있으면 아래 검사가 무의미하다")
                .hasSize(images.size());

        // "선언/실제"가 같은 값끼리 짝을 이뤄야 한다. png/jpg면 엑셀에서 깨진 그림이 된다.
        assertThat(types)
                .as("선언 타입과 내용이 어긋난 그림이 있으면 엑셀에서 깨진다")
                .allSatisfy(t -> assertThat(t.split("/")[0]).isEqualTo(t.split("/")[1]));
    }
}
