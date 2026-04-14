package com.example.esti.crawler.common;

public class CrawlException extends RuntimeException {
    public CrawlException(String message) {
        super(message);
    }

    public CrawlException(String message, Throwable cause) {
        super(message, cause);
    }
}
