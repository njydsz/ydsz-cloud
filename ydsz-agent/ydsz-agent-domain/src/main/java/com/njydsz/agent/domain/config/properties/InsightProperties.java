package com.njydsz.agent.domain.config.properties;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BI 洞察报告配置属性。
 *
 * <p>绑定配置前缀 {@code ydzs.agent.insight}，控制洞察报告生成能力的启用状态、LLM 模型选择、
 * 报告生成超时时间、最大章节数与输出格式。默认不开启（isEnabled=false），
 * 使用 gpt-4 模型，超时 120 秒，最多 8 个章节，支持 html 与 markdown 格式。
 *
 * @author ydsz
 * @since 26.09.24
 */
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
