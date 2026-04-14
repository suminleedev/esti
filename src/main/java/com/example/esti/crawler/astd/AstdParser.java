package com.example.esti.crawler.astd;

import com.example.esti.crawler.common.CrawledProduct;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AstdParser {

    private static final Pattern IMAGE_DISPLAY_PATTERN =
            Pattern.compile(".*/main/product/img\\.do\\?.*v_product=(\\d+)&v_product_img=(\\d+).*");

    private static final Pattern IMAGE_DOWNLOAD_PATTERN =
            Pattern.compile(".*/main/product/down\\.do\\?file_type=detail_img&.*v_product=(\\d+)&v_product_img=(\\d+).*");

    public Optional<CrawledProduct> parse(
            String detailUrl,
            Document doc,
            String maker,
            String vendorCode
    ) {
        Long siteProductId = extractQueryLong(detailUrl, "v_product").orElse(null);
        String productCode = extractProductCode(doc).orElse(null);
        String productName = extractProductName(doc).orElse(null);

        String imageUrl = null;
        String downloadUrl = null;

        for (Element a : doc.select("a[href]")) {
            String absHref = a.absUrl("href");
            if (absHref == null || absHref.isBlank()) {
                continue;
            }

            if (imageUrl == null && IMAGE_DISPLAY_PATTERN.matcher(absHref).matches()) {
                imageUrl = absHref;
            }
            if (downloadUrl == null && IMAGE_DOWNLOAD_PATTERN.matcher(absHref).matches()) {
                downloadUrl = absHref;
            }
        }

        if (siteProductId == null && productCode == null && productName == null) {
            return Optional.empty();
        }

        return Optional.of(CrawledProduct.builder()
                .maker(maker)
                .vendorCode(vendorCode)
                .siteProductId(siteProductId)
                .productCode(productCode)
                .productName(productName)
                .productUrl(detailUrl)
                .imageUrl(imageUrl)
                .downloadUrl(downloadUrl)
                .build());
    }

    private Optional<String> extractProductCode(Document doc) {
        String text = doc.text();
        Pattern p = Pattern.compile("품번\\s*[:：]?\\s*([A-Z0-9\\-]+)");
        Matcher m = p.matcher(text);
        return m.find() ? Optional.of(m.group(1).trim()) : Optional.empty();
    }

    private Optional<String> extractProductName(Document doc) {
        if (doc.title() != null && !doc.title().isBlank()) {
            String[] split = doc.title().split("\\|");
            if (split.length > 0 && !split[0].isBlank()) {
                return Optional.of(split[0].trim());
            }
        }

        for (Element h : doc.select("h1, h2, h3, h4")) {
            String text = h.text().trim();
            if (!text.isBlank()) {
                return Optional.of(text);
            }
        }

        return Optional.empty();
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