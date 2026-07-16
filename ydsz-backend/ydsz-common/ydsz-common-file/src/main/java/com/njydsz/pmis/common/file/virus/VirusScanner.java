package com.njydsz.common.file.virus;

import java.io.InputStream;

public interface VirusScanner {
    ScanResult scan(InputStream inputStream, String fileName);
    boolean isAvailable();
    enum ScanResult { CLEAN, INFECTED, ERROR }
}