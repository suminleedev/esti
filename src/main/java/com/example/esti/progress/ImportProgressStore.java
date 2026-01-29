package com.example.esti.progress;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ImportProgressStore {

    private final Map<String, ImportProgress> store = new ConcurrentHashMap<>();

    public String createJob() {
        String jobId = UUID.randomUUID().toString();
        store.put(jobId, new ImportProgress(0, "대기 중", false, false));
        return jobId;
    }

    public void update(String jobId, int percent, String message) {
        store.put(jobId, new ImportProgress(percent, message, false, false));
    }

    public void done(String jobId, String message) {
        store.put(jobId, new ImportProgress(100, message, true, false));
    }

    public void fail(String jobId, String message) {
        store.put(jobId, new ImportProgress(100, message, true, true));
    }

    public ImportProgress get(String jobId) {
        return store.getOrDefault(jobId, new ImportProgress(0, "존재하지 않는 job", true, true));
    }
}
