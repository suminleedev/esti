package com.example.esti.crawler.astd;

import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ProductImageCrawler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AstdCrawler implements ProductImageCrawler {

    private static final Pattern PRODUCT_LINK_PATTERN =
            Pattern.compile(".*/main/product\\.do\\?proc_type=view&.*v_product=(\\d+).*");

    @Value("${app.crawler.astd.maker}")
    private String maker;

    @Value("${app.crawler.astd.vendor-code}")
    private String vendorCode;

    @Value("${app.crawler.astd.category-url}")
    private String categoryUrl;

    @Value("${app.crawler.user-agent}")
    private String userAgent;

    @Value("${app.crawler.timeout-ms}")
    private int timeoutMs;

    private final AstdParser parser = new AstdParser();

    @Override
    public String maker() {
        return maker;
    }

    @Override
    public String vendorCode() {
        return vendorCode;
    }

    @Override
    public List<String> collectProductUrls() throws Exception {
        Document doc = Jsoup.connect(categoryUrl)
                .userAgent(userAgent)
                .timeout(timeoutMs)
                .get();

        Set<String> urls = new LinkedHashSet<>();

        for (Element a : doc.select("a[href]")) {
            String absHref = a.absUrl("href");
            if (absHref == null || absHref.isBlank()) {
                continue;
            }

            Matcher m = PRODUCT_LINK_PATTERN.matcher(absHref);
            if (m.matches()) {
                urls.add(absHref);
            }
        }

        return new ArrayList<>(urls);
    }

    @Override
    public Optional<CrawledProduct> crawlProduct(String productUrl) throws Exception {
        Document doc = Jsoup.connect(productUrl)
                .userAgent(userAgent)
                .timeout(timeoutMs)
                .get();

        return parser.parse(productUrl, doc, maker, vendorCode);
    }
}