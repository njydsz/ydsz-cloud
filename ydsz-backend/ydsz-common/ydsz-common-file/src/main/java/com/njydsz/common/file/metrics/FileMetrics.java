package com.njydsz.common.file.metrics;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * File storage Micrometer metrics collector.
 *
 * @author ydsz-team
 * @since 1.1.0
 */
public class FileMetrics {

    private static final String PREFIX = "file.";
    private final MeterRegistry registry;
    private final Counter uploadCounter;
    private final Counter downloadCounter;
    private final Counter deleteCounter;
    private final Counter dedupHitCounter;
    private final Counter dedupMissCounter;
    private final Counter virusDetectedCounter;
    private final Timer uploadTimer;
    private final Timer downloadTimer;

    public FileMetrics(MeterRegistry registry) {
        this.registry = registry;
        if (registry != null) {
            this.uploadCounter = Counter.builder(PREFIX + "upload.count").description("Total file upload count").register(registry);
            this.downloadCounter = Counter.builder(PREFIX + "download.count").description("Total file download count").register(registry);
            this.deleteCounter = Counter.builder(PREFIX + "delete.count").description("Total file delete count").register(registry);
            this.dedupHitCounter = Counter.builder(PREFIX + "dedup.hit").description("File dedup hit count").register(registry);
            this.dedupMissCounter = Counter.builder(PREFIX + "dedup.miss").description("File dedup miss count").register(registry);
            this.virusDetectedCounter = Counter.builder(PREFIX + "virus.detected").description("Virus detected count").register(registry);
            this.uploadTimer = Timer.builder(PREFIX + "upload.duration").description("File upload duration").register(registry);
            this.downloadTimer = Timer.builder(PREFIX + "download.duration").description("File download duration").register(registry);
        } else {
            this.uploadCounter = null;
            this.downloadCounter = null;
            this.deleteCounter = null;
            this.dedupHitCounter = null;
            this.dedupMissCounter = null;
            this.virusDetectedCounter = null;
            this.uploadTimer = null;
            this.downloadTimer = null;
        }
    }

    public void recordUpload(long durationNanos) {
        if (uploadCounter != null) uploadCounter.increment();
        if (uploadTimer != null) uploadTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordDownload(long durationNanos) {
        if (downloadCounter != null) downloadCounter.increment();
        if (downloadTimer != null) downloadTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordDelete() {
        if (deleteCounter != null) deleteCounter.increment();
    }

    public void recordDedupHit() {
        if (dedupHitCounter != null) dedupHitCounter.increment();
    }

    public void recordDedupMiss() {
        if (dedupMissCounter != null) dedupMissCounter.increment();
    }

    public void recordVirusDetected() {
        if (virusDetectedCounter != null) virusDetectedCounter.increment();
    }

    public void recordUploadError(String errorCode) {
        if (registry != null) {
            Counter.builder(PREFIX + "upload.errors").tag("code", errorCode != null ? errorCode : "unknown").description("File upload error count").register(registry).increment();
        }
    }

    public void recordDownloadError(String errorCode) {
        if (registry != null) {
            Counter.builder(PREFIX + "download.errors").tag("code", errorCode != null ? errorCode : "unknown").description("File download error count").register(registry).increment();
        }
    }

    public boolean isAvailable() {
        return registry != null;
    }
}
