package com.example.esti.crawler.astd;

import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ProductImageCrawler;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AstdCrawler implements ProductImageCrawler {

    private static final String AJAX_LIST_PATH = "/main/product/ajaxList.do";

    @Value("${app.crawler.astd.maker}")
    private String maker;

    @Value("${app.crawler.astd.vendor-code}")
    private String vendorCode;

    @Value("${app.crawler.astd.category-url}")
    private String categoryUrl;

    @Value("${app.crawler.astd.category-ids}")
    private String categoryIds;

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
        Map<String, CrawledProduct> unique = new LinkedHashMap<>();

        for (Integer cate1 : parseCategoryIds()) {
            String currentCategoryUrl = buildCategoryUrl(cate1);

            Document firstPage = Jsoup.connect(currentCategoryUrl)
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .get();

            collectFromPage(unique, firstPage);

            int totalPages = extractTotalPages(firstPage);

            for (int page = 2; page <= totalPages; page++) {
                Document pageDoc = fetchPageByAjax(currentCategoryUrl, cate1, page);
                collectFromPage(unique, pageDoc);
            }
        }

        return new ArrayList<>(unique.values());
    }

    private void collectFromPage(Map<String, CrawledProduct> unique, Document doc) {
        for (Element item : doc.select("ul.list_wrap > li")) {
            parser.parseFromListItem(item, maker, vendorCode)
                    .ifPresent(product -> unique.putIfAbsent(buildUniqueKey(product), product));
        }
    }

    private String buildUniqueKey(CrawledProduct product) {
        String siteId = product.getSiteProductId() != null
                ? String.valueOf(product.getSiteProductId())
                : "NO_ID";

        String code = product.getProductCode() != null
                ? product.getProductCode()
                : "NO_CODE";

        return siteId + "|" + code;
    }

    private int extractTotalPages(Document doc) {
        int maxPage = 1;

        for (Element a : doc.select(".board_paginate a[data-page]")) {
            String pageText = a.attr("data-page");
            try {
                int page = Integer.parseInt(pageText);
                if (page > maxPage) {
                    maxPage = page;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return maxPage;
    }

    private Document fetchPageByAjax(String refererUrl, int cate1, int page) throws Exception {
        Connection.Response response = Jsoup.connect(extractBaseUrl() + AJAX_LIST_PATH)
                .userAgent(userAgent)
                .timeout(timeoutMs)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", refererUrl)
                .data("v_page", String.valueOf(page))
                .data("v_cate1", String.valueOf(cate1))
                .data("v_order", "1")
                .data("v_pagesize", "12")
                .method(Connection.Method.POST)
                .execute();

        return response.parse();
    }

    private List<Integer> parseCategoryIds() {
        List<Integer> ids = new ArrayList<>();

        for (String token : categoryIds.split(",")) {
            String value = token.trim();
            if (value.isBlank()) {
                continue;
            }

            try {
                ids.add(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("잘못된 ASTD category id: " + value, e);
            }
        }

        if (ids.isEmpty()) {
            throw new IllegalArgumentException("ASTD category ids가 비어 있습니다.");
        }

        return ids;
    }

    private String buildCategoryUrl(int cate1) {
        return extractBaseUrl() + "/main/product.do?v_cate1=" + cate1;
    }

    private String extractBaseUrl() {
        int idx = categoryUrl.indexOf("/main/");
        if (idx > -1) {
            return categoryUrl.substring(0, idx);
        }
        return categoryUrl;
    }
}