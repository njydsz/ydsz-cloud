package com.njydsz.agent.domain.config.properties;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OcrProperties {
  private static final int DEFAULT_TIMEOUT_SECONDS = 60;
  private static final int DEFAULT_PDF_DPI = 200;

  private boolean isEnabled = false;
  private String provider = "llm-vision";
  private String visionModel = "qwen-vl-max";
  private int pdfDpi = DEFAULT_PDF_DPI;
  private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
  private boolean isDegradedOnFailure = true;
}
