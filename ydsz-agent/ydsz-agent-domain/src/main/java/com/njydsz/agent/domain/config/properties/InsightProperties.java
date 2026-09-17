package com.njydsz.agent.domain.config.properties;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsightProperties {
  private static final int DEFAULT_REPORT_TIMEOUT_SECONDS = 120;
  private static final int DEFAULT_MAX_SECTIONS = 8;

  private boolean isEnabled = false;
  private String model = "gpt-4";
  private int reportTimeoutSeconds = DEFAULT_REPORT_TIMEOUT_SECONDS;
  private int maxSections = DEFAULT_MAX_SECTIONS;
  private List<String> supportedFormats = List.of("html", "markdown");
}
