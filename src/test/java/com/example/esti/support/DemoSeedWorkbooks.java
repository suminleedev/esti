package com.example.esti.support;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 데모 배포본이 쓰는 <b>합성</b> 단가표를 만든다 (D-2).
 *
 * <p><b>왜 지어내는가</b> — 실단가표는 공급사의 영업 정보라 저장소에 둘 수 없고, 그래서 배포본에도
 * 올릴 수 없다({@code CLAUDE.md}). 그런데 데모의 핵심 흐름이 「업로드 → 제안서 → 엑셀 출력」이라
 * 카탈로그가 비면 보여줄 게 없다. 그래서 <b>파서가 통과하는 모양</b>만 실제와 맞추고
 * 값은 전부 지어낸 파일을 만든다.
 *
 * <ul>
 *   <li><b>단가·품번·전산코드 전부 합성</b>이다. 실제 값과 겹치지 않게 접두어를 따로 쓴다.</li>
 *   <li><b>시트 구조는 실제와 같아야</b> 한다. 여기가 어긋나면 파서가 0건을 내고 데모가 빈다.</li>
 *   <li>분류·제품명은 일반 명칭만 쓴다 — 어느 공급사 것인지 드러나지 않게.</li>
 * </ul>
 *
 * <p>생성물은 {@code src/main/resources/static/samples/}에 커밋돼 있다. 이 클래스는 그 파일을
 * <b>다시 만들 수 있게</b> 남겨 두는 것이고, 커밋된 파일이 실제로 파싱되는지는
 * {@code DemoSeedParseTest}가 지킨다.
 *
 * <p>값은 인덱스로부터 계산해 <b>매번 같게</b> 나온다. 난수를 쓰면 다시 만들 때마다 카탈로그가
 * 달라져 「무엇이 바뀌었나」를 볼 수 없다.
 */
public final class DemoSeedWorkbooks {

    private DemoSeedWorkbooks() {}

    /** 시리즈(세트)명 — 데모 카탈로그에서 시리즈 검색·표시를 보여주는 자리다. */
    private static final String[] SERIES = {
            "노바", "루체", "미니멀", "베이직", "센트로", "아뜰리에", "에코", "클래식", "프리모", "하모니"
    };

    // ============================================================
    // A사 양식 — 단일 시트. C=소분류/세트명 D=제품명 E=구품번 F=신품번 G=단가.
    //   · 합계행(G만 있는 행)이 세트 경계이자 세트가다.
    //   · 대분류는 B열 라벨에서 오는데, 라벨이 구간 시작보다 일정 행 아래 얹혀 있다.
    //     파서가 그 오프셋을 파일에서 학습하므로 여기서도 «빈 행 → 7행 뒤 라벨»을 일관되게 지킨다.
    // ============================================================

    /**
     * A사 대분류 한 구간.
     *
     * <p>{@code mains}는 소분류와 <b>자리를 맞춘다</b> — {@code mains[i]}가 {@code smalls[i]} 구간의
     * 제품명 후보다. 섞어 쓰면 «원피스양변기» 소분류 안에 투피스가 앉는 카탈로그가 나온다.
     */
    private record ASection(String label, String codePrefix, int basePrice,
                            String[] smalls, String[][] mains, String[] parts, int setsPerSmall) {}

    private static final ASection[] A_SECTIONS = {
            new ASection("양변기", "DA1", 290_000,
                    new String[]{"원피스양변기", "투피스양변기"},
                    new String[][]{{"원피스 양변기", "절수형 원피스 양변기"},
                                   {"투피스 양변기", "절수형 투피스 양변기"}},
                    new String[]{"양변기 시트", "급수 호스", "고정 볼트"}, 5),
            new ASection("전자비데", "DA2", 430_000,
                    new String[]{"전자비데"},
                    new String[][]{{"전자비데(일반형)", "전자비데(리모컨형)", "전자비데(슬림형)"}},
                    new String[]{"비데 필터", "급수 분기밸브"}, 6),
            new ASection("세면기", "DA3", 180_000,
                    new String[]{"카운터세면기", "벽걸이세면기"},
                    new String[][]{{"카운터 세면기", "언더카운터 세면기"},
                                   {"벽걸이 세면기", "반다리 세면기"}},
                    new String[]{"세면기 다리", "앙카 볼트", "배수 트랩"}, 5),
            new ASection("스탠딩욕조", "DA4", 620_000,
                    new String[]{"스탠딩욕조", "매립욕조"},
                    new String[][]{{"스탠딩 욕조", "코너 스탠딩 욕조"},
                                   {"매립형 욕조", "매립형 코너 욕조"}},
                    new String[]{"욕조 배수구", "오버플로우 커버"}, 4),
            // 이 구간만 소분류를 «제품명»에서 뽑는다(A사 원본이 시리즈 단위로 묶여 있어서다).
            // 그래서 이름에 세면/욕조·데크/매립이 들어가야 소분류가 갈린다.
            new ASection("수전", "DA5", 150_000,
                    new String[]{"세면수전", "욕조수전"},
                    new String[][]{{"세면 수전", "매립형 세면 수전"},
                                   {"욕조 수전", "데크형 욕조 수전"}},
                    new String[]{"급수 호스", "팝업 배수구"}, 6),
            new ASection("샤워", "DA6", 210_000,
                    new String[]{"샤워수전"},
                    new String[][]{{"샤워 수전", "해바라기 샤워 수전", "매립형 샤워 수전"}},
                    new String[]{"샤워 헤드", "샤워 호스", "슬라이드 바"}, 6),
            new ASection("주방수전", "DA7", 130_000,
                    new String[]{"주방수전"},
                    new String[][]{{"주방 수전", "인출식 주방 수전", "벽붙이 주방 수전"}},
                    new String[]{"급수 호스", "정수 어댑터"}, 6),
            new ASection("액세서리", "DA8", 45_000,
                    new String[]{"욕실선반", "걸이류"},
                    new String[][]{{"코너 선반", "2단 선반"},
                                   {"수건 걸이", "휴지 걸이", "컵 대"}},
                    new String[]{"고정 볼트", "실리콘 패킹"}, 5),
            new ASection("발코니수전", "DA9", 78_000,
                    new String[]{"발코니수전"},
                    new String[][]{{"발코니 수전", "세탁기 수전"}},
                    new String[]{"급수 호스", "벽 고정대"}, 5),
            new ASection("상업용제품", "DB1", 260_000,
                    new String[]{"소변기", "상업용세면기"},
                    new String[][]{{"벽걸이 소변기", "센서형 소변기"},
                                   {"상업용 세면기", "센서형 상업용 세면기"}},
                    new String[]{"소변기 트랩", "고정 브래킷"}, 4),
            // 부속 구간은 이름에 «완제품»이 보이면 파서가 제 대분류로 돌려보낸다.
            // 트랩·밸브 같은 부속 이름만 써서 그 경로를 건드리지 않는다.
            new ASection("부속", "DB2", 24_000,
                    new String[]{"배수부속", "급수부속"},
                    new String[][]{{"S 트랩", "P 트랩"},
                                   {"앵글 밸브", "팝업 밸브"}},
                    new String[]{"실리콘 패킹", "고정 너트"}, 6),
    };

    /** 빈 행에서 B열 대분류 라벨까지의 거리. 실파일이 그렇듯 라벨을 구간 시작보다 아래에 얹는다. */
    private static final int A_LABEL_DISTANCE = 7;

    public static void writeVendorA(Path out) throws IOException {
        Files.createDirectories(out.getParent());
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream os = Files.newOutputStream(out)) {
            Sheet s = wb.createSheet("단가표");

            // 헤더 — E/F의 '구품번'·'신품번'으로 파서가 헤더행을 걸러낸다.
            text(s, 0, 2, "분류");
            text(s, 0, 3, "제품명");
            text(s, 0, 4, "구품번");
            text(s, 0, 5, "신품번");
            text(s, 0, 6, "단가");

            int r = 1;              // r은 «구분선(빈 행)» 자리. 구간은 그 다음 행부터다.
            int setIndex = 0;
            for (ASection section : A_SECTIONS) {
                int blankRow = r;
                int row = blankRow + 1;
                int labelRow = blankRow + A_LABEL_DISTANCE;

                for (int smallIdx = 0; smallIdx < section.smalls().length; smallIdx++) {
                    text(s, row++, 2, section.smalls()[smallIdx]);   // C 라벨 전용행 = 소분류
                    String[] mains = section.mains()[smallIdx];

                    for (int i = 0; i < section.setsPerSmall(); i++, setIndex++) {
                        String series = SERIES[setIndex % SERIES.length];
                        String mainName = mains[i % mains.length];
                        int mainPrice = section.basePrice() + spread(setIndex, 12) * 5_000;

                        text(s, row, 2, series);                    // 세트 시작행: C=시리즈명 + 데이터
                        item(s, row++, mainName, section.codePrefix(), setIndex, 0, mainPrice);

                        int total = mainPrice;
                        int partCount = 1 + (setIndex % 3);
                        for (int p = 0; p < partCount; p++) {
                            int price = 6_000 + spread(setIndex * 7 + p, 9) * 1_500;
                            String partName = section.parts()[(setIndex + p) % section.parts().length];
                            item(s, row++, partName, section.codePrefix(), setIndex, p + 1, price);
                            total += price;
                        }
                        number(s, row++, 6, total);                 // 합계행 = 세트가(G열만)
                    }
                }

                // 합계행 없이 끝나는 단품 하나 — 파서가 «독립 제품»으로 내보내는 경로를 데모에 남긴다.
                item(s, row++, section.mains()[0][0] + " (단품)", section.codePrefix(), setIndex++, 9,
                        section.basePrice() / 2 + spread(setIndex, 8) * 3_000);

                if (row <= labelRow) {
                    throw new IllegalStateException(
                            "구간이 너무 짧아 대분류 라벨이 다음 구간으로 넘어간다: " + section.label());
                }
                text(s, labelRow, 1, section.label());              // B열 대분류 라벨

                r = row;                                            // 다음 구간의 구분선(빈 행)
            }
            wb.write(os);
        }
    }

    /** A사 품목 한 행 — D=제품명 E=구품번 F=신품번 G=단가. */
    private static void item(Sheet s, int row, String name, String prefix, int setIndex, int seq, int price) {
        text(s, row, 3, name);
        text(s, row, 4, prefix + "-" + (700 + setIndex) + (seq == 0 ? "" : "-" + seq));
        text(s, row, 5, prefix + (1000 + setIndex * 4 + seq));
        number(s, row, 6, price);
    }

    // ============================================================
    // B사 양식 — 멀티 시트. 시트마다 양식이 다르고 시트명으로 갈린다.
    //   여기서는 성격이 다른 네 가지를 담는다:
    //     양변기   슬롯 2행형 + 計(세트가 = 부속합)
    //     세면기   슬롯 2행형, 計 없음 → 기본 구성만 세트가 (나머지는 대체옵션)
    //     악세사리 대표행(규격=SET) + 부속행 + 단품 혼재
    //     수전금구 1행 = 1제품 (부속 없음)
    // ============================================================

    private static final String[] B_TOILET_KINDS = {"원피스", "투피스", "비데일체형"};
    private static final String[] B_BASIN_KINDS = {"카운터", "언더카운터", "벽걸이"};

    public static void writeVendorB(Path out) throws IOException {
        Files.createDirectories(out.getParent());
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream os = Files.newOutputStream(out)) {
            writeToiletSheet(wb.createSheet("양변기"));
            writeWashbasinSheet(wb.createSheet("세면기"));
            writeAccessorySheet(wb.createSheet("악세사리"));
            writeFaucetSheet(wb.createSheet("수전금구"));
            wb.write(os);
        }
    }

    /** 양변기 — 제품코드행 + 대리점가행 한 쌍. 計 = 부속 단가의 합이다. */
    private static void writeToiletSheet(Sheet s) {
        slotHeader(s, 0, new String[]{"도기", "시트", "양부속"}, true);

        int row = 1;
        for (int i = 0; i < 30; i++) {
            String kind = B_TOILET_KINDS[i / 10];       // 품종은 구간 첫 행에만 적고 이어쓰게 둔다
            boolean firstOfKind = (i % 10 == 0);
            String code = "DBC" + (2100 + i * 3);

            int dogi = 180_000 + spread(i, 14) * 6_000;
            int seat = 40_000 + spread(i * 3, 8) * 3_000;
            int fitting = 22_000 + spread(i * 5, 6) * 2_000;

            text(s, row, 0, "상품");
            if (firstOfKind) text(s, row, 1, kind);
            text(s, row, 2, code);
            text(s, row, 4, "KS-" + (400 + i));
            text(s, row, 5, "제품코드");
            text(s, row, 6, "DG" + (5100 + i));
            text(s, row, 7, "ST" + (5100 + i));
            text(s, row, 8, "YB" + (5100 + i));
            if (i % 4 == 0) text(s, row, 10, "탱크뚜껑 포함");
            row++;

            text(s, row, 5, "대리점가");
            number(s, row, 6, dogi);
            number(s, row, 7, seat);
            number(s, row, 8, fitting);
            number(s, row, 9, dogi + seat + fitting);   // 計
            row++;
        }
    }

    /**
     * 세면기 — 計가 없다. 기본 구성(원홀 도기 + 반다리)만 세트가에 들어가고
     * 나머지 도기·다리는 «대체옵션»으로 붙는다. 데모에서 선택형 세트를 보여주는 자리다.
     */
    private static void writeWashbasinSheet(Sheet s) {
        slotHeader(s, 0, new String[]{"도기(원홀)", "도기(4인치)", "다리(반다리)", "다리(긴다리)", "하프고리"}, false);

        int row = 1;
        for (int i = 0; i < 24; i++) {
            String kind = B_BASIN_KINDS[i / 8];
            boolean firstOfKind = (i % 8 == 0);
            String code = "DBL" + (3100 + i * 3);

            text(s, row, 0, "상품");
            if (firstOfKind) text(s, row, 1, kind);
            text(s, row, 2, code);
            text(s, row, 4, "KS-" + (600 + i));
            text(s, row, 5, "제품코드");
            text(s, row, 6, "LO" + (6100 + i));
            text(s, row, 7, "LF" + (6100 + i));
            text(s, row, 8, "LH" + (6100 + i));
            text(s, row, 9, "LL" + (6100 + i));
            text(s, row, 10, "LG" + (6100 + i));
            if (i % 5 == 0) text(s, row, 11, "언더카운터 시공 기준");
            row++;

            text(s, row, 5, "대리점가");
            number(s, row, 6, 90_000 + spread(i, 12) * 4_000);       // 원홀 도기(기본)
            number(s, row, 7, 96_000 + spread(i * 3, 12) * 4_000);   // 4인치 도기(대체옵션)
            number(s, row, 8, 38_000 + spread(i * 7, 6) * 2_000);    // 반다리(기본)
            number(s, row, 9, 46_000 + spread(i * 9, 6) * 2_000);    // 긴다리(대체옵션)
            number(s, row, 10, 9_000 + spread(i * 11, 4) * 1_000);   // 하프고리(필수 부속)
            row++;
        }
    }

    /** 슬롯 2행형 헤더 — A=구분 B=품종 C=품번, G열부터 슬롯 라벨, 그 뒤 (計) 비고. */
    private static void slotHeader(Sheet s, int r, String[] slots, boolean withTotal) {
        text(s, r, 0, "구분");
        text(s, r, 1, "품종");
        text(s, r, 2, "품번");
        text(s, r, 3, "이미지");
        text(s, r, 4, "KS품번");
        int col = 6;
        for (String slot : slots) text(s, r, col++, slot);
        if (withTotal) text(s, r, col++, "計");
        text(s, r, col, "비고");
    }

    /** 악세사리 — 대표행(G=SET) 아래 부속행이 붙고, 사이사이 단품이 섞인다. */
    private static void writeAccessorySheet(Sheet s) {
        text(s, 0, 0, "대분류");
        text(s, 0, 1, "세부분류");
        text(s, 0, 3, "품번");
        text(s, 0, 4, "전산코드");
        text(s, 0, 5, "품명");
        text(s, 0, 6, "규격");
        text(s, 0, 7, "대리점가");
        text(s, 0, 9, "비고");

        String[][] groups = {
                {"욕실 액세서리", "선반류"},
                {"욕실 액세서리", "걸이류"},
                {"욕실 액세서리", "거울류"},
        };
        String[] setNames = {"3종 세트", "4종 세트", "5종 세트"};
        String[] partNames = {"수건 걸이", "휴지 걸이", "비누 대", "컵 대", "코너 선반"};

        int row = 1;
        int seq = 0;
        for (String[] group : groups) {
            for (int i = 0; i < 4; i++, seq++) {
                // 세트 대표행
                int setPrice = 120_000 + spread(seq, 10) * 8_000;
                text(s, row, 0, i == 0 ? group[0] : null);
                text(s, row, 1, i == 0 ? group[1] : null);
                text(s, row, 3, "DBA" + (4100 + seq * 5));
                text(s, row, 4, "AX" + (70_000 + seq * 13));
                text(s, row, 5, group[1] + " " + setNames[seq % setNames.length]);
                text(s, row, 6, "SET");
                number(s, row, 7, setPrice);
                row++;

                int partCount = 2 + (seq % 3);
                for (int p = 0; p < partCount; p++) {
                    text(s, row, 3, "DBA" + (4100 + seq * 5 + p + 1));
                    text(s, row, 5, partNames[(seq + p) % partNames.length]);
                    number(s, row, 7, 18_000 + spread(seq * 3 + p, 8) * 2_000);
                    row++;
                }
            }

            // 같은 구간의 단품 두 건 — 세트가 아닌 낱개 품목도 카탈로그에 선다.
            for (int p = 0; p < 2; p++, seq++) {
                text(s, row, 1, group[1]);
                text(s, row, 3, "DBA" + (4900 + seq));
                text(s, row, 5, partNames[seq % partNames.length]);
                text(s, row, 6, "600mm");
                number(s, row, 7, 26_000 + spread(seq, 9) * 2_500);
                if (seq % 6 == 0) text(s, row, 9, "단종 예정");
                row++;
            }
        }
    }

    /** 수전금구 — 1행 1제품. 시리즈(구분)는 병합셀이라 첫 행에만 적고 이어쓰게 둔다. */
    private static void writeFaucetSheet(Sheet s) {
        text(s, 0, 1, "구분");
        text(s, 0, 2, "품목");
        text(s, 0, 4, "품번");
        text(s, 0, 6, "대리점가");
        text(s, 0, 9, "비고");

        String[] items = {"세면 수전", "싱크 수전", "샤워 수전", "욕조 수전", "세탁기 수전"};
        int row = 1;
        for (int g = 0; g < 5; g++) {
            for (int i = 0; i < 5; i++) {
                int seq = g * 5 + i;
                if (i == 0) text(s, row, 1, SERIES[g] + " 시리즈");
                text(s, row, 2, items[i]);
                text(s, row, 4, "DBF" + (5200 + seq * 2));
                number(s, row, 6, 70_000 + spread(seq, 16) * 5_000);
                if (seq % 7 == 0) text(s, row, 9, "벽붙이 기준");
                row++;
            }
        }
    }

    // ============================================================
    // 유틸
    // ============================================================

    /** 인덱스를 {@code 0..mod-1}로 흩는다 — 값이 계단처럼 늘지 않게 하되 매번 같게 나오도록. */
    private static int spread(int index, int mod) {
        return (int) Math.floorMod(index * 2_654_435_761L, mod);
    }

    private static Row row(Sheet s, int r) {
        Row row = s.getRow(r);
        return row != null ? row : s.createRow(r);
    }

    private static void text(Sheet s, int r, int c, String v) {
        if (v == null) return;
        Cell cell = row(s, r).createCell(c);
        cell.setCellValue(v);
    }

    private static void number(Sheet s, int r, int c, double v) {
        row(s, r).createCell(c).setCellValue(v);
    }
}
