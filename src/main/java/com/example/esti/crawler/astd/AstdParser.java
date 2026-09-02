package com.example.esti.crawler.astd;

import com.example.esti.crawler.common.CrawledProduct;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AstdParser {

    /** 품번 모양: 대문자로 시작 → 숫자 포함 → (하이픈 + 상세품번). 최소 5자. */
    private static final Pattern CODE_LIKE =
            Pattern.compile("\\b[A-Z]{1,5}[0-9][A-Z0-9]{3,}(?:-[A-Z0-9]+)?\\b");

    public Optional<CrawledProduct> parseFromListItem(
            Element item,
            String maker,
            String vendorCode
    ) {
        Element linkEl = item.selectFirst("a[href*='proc_type=view']");
        if (linkEl == null) {
            return Optional.empty();
        }

        String detailUrl = linkEl.absUrl("href");
        if (detailUrl == null || detailUrl.isBlank()) {
            return Optional.empty();
        }

        Long siteProductId = extractQueryLong(detailUrl, "v_product").orElse(null);

        Element imgEl = item.selectFirst("div.img img");
        String imageUrl = imgEl != null ? imgEl.absUrl("src") : null;

        String productName = textOrNull(item.selectFirst("p.tit"));
        String collectionName = textOrNull(item.selectFirst("p.cate"));

        Element tagEl = item.selectFirst("p.tag");
        String rawTagText = tagEl != null ? normalizeTagText(tagEl.html()) : null;

        String productCode = extractMasterProductCode(rawTagText).orElse(null);

        if (siteProductId == null && productCode == null && productName == null) {
            return Optional.empty();
        }

        return Optional.of(CrawledProduct.builder()
                .maker(maker)
                .vendorCode(vendorCode)
                .siteProductId(siteProductId)
                .productCode(productCode)
                .productName(productName)
                .collectionName(collectionName)
                .rawTagText(rawTagText)
                .productUrl(detailUrl)
                .imageUrl(imageUrl)
                .downloadUrl(null)
                .build());
    }

    private Optional<String> extractMasterProductCode(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        String code = extractByLabel(text, "품번");
        if (code != null) return Optional.of(normalizeCode(code));

        code = extractByLabel(text, "비데(도기포함)");
        if (code != null) return Optional.of(normalizeCode(code));

        code = extractByLabel(text, "비데");
        if (code != null) return Optional.of(normalizeCode(code));

        code = extractByLabel(text, "도기");
        if (code != null) return Optional.of(normalizeCode(code));

        code = extractByLabel(text, "하부");
        if (code != null) return Optional.of(normalizeCode(code));

        // 라벨을 못 찾으면 텍스트에서 품번 모양을 직접 뽑는다.
        //
        // 라벨이 아예 없는 항목이 실패의 최다 유형이고, 라벨 오타("풉번")도 같은 모양으로 나타난다.
        // 오타마다 별칭을 등록하는 대신 이 폴백 하나로 함께 흡수한다 — 다음 오타에 또 지지 않으려면
        // 사전을 늘리는 쪽이 아니라 라벨에 기대지 않는 쪽이 맞다.
        code = extractFirstCodeLike(text);
        if (code != null) return Optional.of(normalizeCode(code));

        return Optional.empty();
    }

    /**
     * 품번처럼 생긴 첫 토큰. 영문 대문자로 시작해 숫자를 포함하고, 뒤에 하이픈부 상세품번이 붙을 수 있다.
     *
     * <p><b>라벨 매칭이 전부 실패했을 때만 쓴다.</b> 라벨이 있는 항목에서 이걸 먼저 돌리면
     * 문장 속 다른 토큰을 품번으로 오인할 여지가 생긴다. 그리고 품번이 아닌 것을 품번이라고 하면
     * 엉뚱한 제품에 사진이 붙으므로, <b>못 뽑는 것보다 잘못 뽑는 쪽이 나쁘다</b>는 전제로 좁게 잡았다 —
     * 숫자가 없는 낱말({@code L-TYPE}), 숫자로 시작하는 치수({@code 840파이프})는 걸리지 않는다.
     */
    private String extractFirstCodeLike(String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = CODE_LIKE.matcher(text);
        while (m.find()) {
            found.add(m.group());
        }

        if (found.isEmpty()) {
            return null;
        }

        // 품번이 여럿이면 뽑지 않는다. 액세서리 세트처럼 한 항목이 부속 여러 개를 나열한 경우인데,
        // 그중 첫 번째를 골라 대표로 삼을 근거가 없다. CrawledProduct가 품번을 하나만 들고 있어
        // 제대로 다루려면 구조를 바꿔야 하고, 그건 별건으로 남겨 뒀다.
        // 여기서 첫 품번을 집어 가면 "범위 밖"이라고 정해 둔 일을 슬그머니 반쯤 해버리는 셈이다.
        if (found.size() > 1) {
            return null;
        }

        return found.iterator().next();
    }

    private String extractByLabel(String text, String label) {
        Pattern p = Pattern.compile(Pattern.quote(label) + "\\s*:\\s*([A-Za-z0-9\\-]+)");
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private String normalizeCode(String code) {
        if (code == null) return null;
        return code.trim()
                .toUpperCase()
                .replaceAll("[^A-Z0-9\\-]", "");
    }

    private String normalizeTagText(String html) {
        String withLineBreak = html
                .replace("<br>", "\n")
                .replace("<br />", "\n")
                .replace("<br/>", "\n");

        return Jsoup.parse(withLineBreak)
                .text()
                .replace("\u00A0", " ")
                .trim();
    }

    private String textOrNull(Element el) {
        if (el == null) return null;
        String text = el.text();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private Optional<Long> extractQueryLong(String url, String key) {
        try {
            URI uri = URI.create(url);
            String query = uri.getRawQuery();
            if (query == null) {
                return Optional.empty();
            }

            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && key.equals(kv[0])) {
                    return Optional.of(Long.parseLong(
                            URLDecoder.decode(kv[1], StandardCharsets.UTF_8)
                    ));
                }
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }
}