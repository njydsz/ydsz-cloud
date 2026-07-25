package com.njydsz.common.file.virus;

import java.io.InputStream;

import lombok.extern.slf4j.Slf4j;

/**
 * NoOp virus scanner (default fallback).
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class NoOpVirusScanner implements VirusScanner {

    @Override
    public ScanResult scan(InputStream inputStream, String fileName) {
        log.debug("VirusScan NoOp skipping: {}", fileName);
        return ScanResult.CLEAN;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
