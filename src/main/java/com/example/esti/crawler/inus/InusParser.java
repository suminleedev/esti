package com.example.esti.crawler.inus;

import com.example.esti.crawler.common.CrawledProduct;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 이누스 리스트 페이지 파서.
 *
 * <p>이 사이트에는 <b>상세 페이지가 없다.</b> 제품 링크가 전부 {@code href="javascript:void(0);"}라
 * 클릭해도 갈 곳이 없고, 필요한 값(품번·이미지·품목명)이 리스트 HTML에 이미 전부 들어 있다.
 * 그래서 ASTD처럼 "목록 → 상세 순회"가 아니라 <b>리스트 문서 한 장에서 여러 건</b>을 뽑는다.
 */
public class InusParser {

    /** 제품 목록 컨테이너. 페이지당 정확히 1개다. */
    private static final String ITEM_SELECTOR = "div.gList li:has(dt)";

    /** 별칭 품번 표시 기호. {@code IBL1681 ㉿ L610}처럼 구형 품번을 병기한다. */
    private static final char ALIAS_MARK = '㉿';

    /**
     * 품번으로 볼 수 있는 토큰.
     *
     * <p>아스키 영숫자로 시작하고 하이픈·괄호·슬래시까지 허용한다. 앞의 한글 마케팅 문구
     * ({@code 초절수 1등급})는 아스키가 아니라 자연히 걸러진다.
     *
     * <p><b>괄호·슬래시를 넣은 이유</b> — 사이트에 {@code IW-700(IC700DE)} ·
     * {@code UB-FH6510(G)} · {@code IS-7000AF/7100AF} · {@code HD501G／HD501P}(전각 슬래시)
     * 같은 표기가 5건 있다. 영숫자와 하이픈만 받으면 이 5건이 통째로 빠져 402건이 397건이 된다.
     * <b>사이트 표기를 그대로 담고</b>, 어디까지가 품번인지는 정규화하는 매칭 쪽에서 정한다 —
     * 파서가 임의로 괄호를 떼면 원본에 없던 판단을 넣는 셈이다.
     */
    private static final Pattern CODE_TOKEN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9\\-()/\uFF0F]*$");

    /**
     * 리스트 문서 한 장에서 제품을 전부 뽑는다.
     *
     * @param doc       리스트 페이지 문서
     * @param listUrl   그 리스트의 URL. 상세 페이지가 없어 {@code productUrl}로도 쓰고,
     *                  이미지 상대 경로를 절대 URL로 만드는 기준으로도 쓴다
     */
    public List<CrawledProduct> parseList(
            Document doc,
            String listUrl,
            String maker,
            String vendorCode
    ) {
        List<CrawledProduct> products = new ArrayList<>();

        for (Element item : doc.select(ITEM_SELECTOR)) {
            parseItem(item, listUrl, maker, vendorCode).ifPresent(products::add);
        }

        return products;
    }

    private Optional<CrawledProduct> parseItem(
            Element item,
            String listUrl,
            String maker,
            String vendorCode
    ) {
        Element dt = item.selectFirst("dt");
        if (dt == null) {
            return Optional.empty();
        }

        // ownText()로 자식 <span>(별칭)을 떼고 본문만 본다.
        String productCode = extractProductCode(dt.ownText());
        if (productCode == null) {
            return Optional.empty();
        }

        return Optional.of(CrawledProduct.builder()
                .maker(maker)
                .vendorCode(vendorCode)
                .siteProductId(null)          // 상세 페이지가 없어 사이트 내부 ID를 얻을 길이 없다
                .productCode(productCode)
                .productName(textOrNull(item.selectFirst("dd")))
                .collectionName(null)
                .rawTagText(extractAliasCode(dt))
                .productUrl(listUrl)          // 상세 페이지가 없으므로 리스트 URL을 넣는다
                .imageUrl(extractImageUrl(item, listUrl))
                .downloadUrl(null)
                .build());
    }

    /**
     * {@code <dt>} 본문에서 품번만 뽑는다.
     *
     * <p>앞에 마케팅 문구가 붙는다 — {@code 초절수 1등급 IC858RPG1}. 품번은 늘 마지막에 오므로
     * <b>아스키 영숫자로 된 마지막 토큰</b>을 고른다. 한글이 섞인 토큰({@code 1등급})은 자연히 빠진다.
     */
    private String extractProductCode(String dtOwnText) {
        if (dtOwnText == null) {
            return null;
        }

        String found = null;
        for (String token : normalizeSpaces(dtOwnText).split(" ")) {
            if (CODE_TOKEN.matcher(token).matches()) {
                found = token;
            }
        }

        return found;
    }

    /**
     * 별칭 품번을 뽑는다 — {@code <span>㉿ C2020CR1</span>}의 기호 뒤 코드.
     *
     * <p>별칭이 있는 건 402건 중 81건뿐이고, <b>없는 쪽이 정상 경로다.</b> 예외로 다루지 않는다.
     * 매칭 키로 쓰이도록 {@code rawTagText}에 담아 넘긴다.
     */
    private String extractAliasCode(Element dt) {
        for (Element span : dt.select("span")) {
            String text = normalizeSpaces(span.text());
            int mark = text.indexOf(ALIAS_MARK);
            if (mark < 0) {
                continue;
            }

            String code = extractProductCode(text.substring(mark + 1));
            if (code != null) {
                return code;
            }
        }

        return null;
    }

    /**
     * 제품 이미지를 절대 URL로 만든다.
     *
     * <p>주의할 게 둘 있다:
     * <ul>
     *   <li>{@code div.img} 안에는 제품 사진 말고 {@code div.icon}의 배지 이미지(New·Good Design)도 있다.
     *       배지를 제품 사진으로 잘못 집으면 전 품목이 같은 그림을 달게 된다</li>
     *   <li>{@code <a>} 위치가 리스트마다 다르다 — {@code bList}는 {@code div.img} <b>안</b>,
     *       나머지는 {@code li} 전체를 감싼다. 그래서 직계 자식({@code div.img > img})으로 좁힐 수 없다</li>
     * </ul>
     */
    private String extractImageUrl(Element item, String listUrl) {
        Elements candidates = item.select("div.img img[src]");

        for (Element img : candidates) {
            if (img.closest("div.icon") != null) {
                continue;   // 배지 아이콘
            }

            String src = img.attr("src");
            if (!src.isBlank()) {
                return toAbsoluteUrl(listUrl, src);
            }
        }

        return null;
    }

    /**
     * 문서 baseUri에 기대지 않고 리스트 URL 기준으로 직접 해석한다.
     * 픽스처를 문자열에서 파싱해도 같은 결과가 나오게 하려는 것이다.
     */
    private String toAbsoluteUrl(String listUrl, String src) {
        try {
            return URI.create(listUrl).resolve(src).toString();
        } catch (IllegalArgumentException e) {
            return src;
        }
    }

    /** {@code &nbsp;}와 연속 공백을 단일 공백으로 접는다. */
    private String normalizeSpaces(String text) {
        return text.replace('\u00A0', ' ').trim().replaceAll("\\s+", " ");
    }

    private String textOrNull(Element el) {
        if (el == null) {
            return null;
        }
        String text = normalizeSpaces(el.text());
        return text.isBlank() ? null : text;
    }
}
