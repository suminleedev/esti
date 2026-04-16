package com.example.esti.crawler.astd;

import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ProductImageCrawler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class AstdCrawler implements ProductImageCrawler {

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
    public List<String> collectProductUrls() {
        return Collections.emptyList();
    }

    @Override
    public Optional<CrawledProduct> crawlProduct(String productUrl) {
        return Optional.empty();
    }

    @Override
    public List<CrawledProduct> crawlAllProducts() throws Exception {
        Document doc = Jsoup.connect(categoryUrl)
                .userAgent(userAgent)
                .timeout(timeoutMs)
                .get();

        List<CrawledProduct> results = new ArrayList<>();

        for (Element item : doc.select("ul.list_wrap > li")) {
            parser.parseFromListItem(item, maker, vendorCode, categoryUrl)
                    .ifPresent(results::add);
        }

        return results;
    }
}