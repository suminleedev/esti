package com.example.esti.crawler.astd;

import com.example.esti.crawler.common.CrawledProduct;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AstdParser {

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

        return Optional.empty();
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