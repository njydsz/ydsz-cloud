package com.njydsz.message.server.service.retry;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 重试策略预览服务（P3-2: 交互式预览）。
 *
 * <p>提供重试计划的可视化预览能力：给定预设档位，生成完整的时间线（每次重试的预计触发时刻与退避时长），
 * 帮助用户在配置前直观理解重试行为。
 *
 * <p>使用方式：
 *
 * <ul>
 *   <li>API: {@code GET /api/v1/message/retry-preview?preset=standard} 获取指定预设的重试时间表
 *   <li>API: {@code GET /api/v1/message/retry-preview/all} 获取所有预设的对比视图
 * </ul>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@Service
public class RetryPreviewService {

  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

  /**
   * 生成指定预设的重试时间线预览。
   *
   * <p>返回每次重试的详细信息：重试序号、距离上次失败的间隔、预计触发时刻、累计等待时间。
   *
   * @param presetCode 预设档位标识
   * @return 预览结果（含预设元信息 + 时间线条目）
   */
  public Map<String, Object> previewRetrySchedule(String presetCode) {
    RetryPreset preset = RetryPreset.fromCode(presetCode);
    return previewRetrySchedule(preset);
  }

  /**
   * 生成所有预设档位的对比预览。
   *
   * @return 每个预设对应的时间线
   */
  public Map<String, Map<String, Object>> previewAllPresets() {
    Map<String, Map<String, Object>> result = new HashMap<>();
    for (RetryPreset preset : RetryPreset.values()) {
      result.put(preset.getCode(), previewRetrySchedule(preset));
    }
    return result;
  }

  private Map<String, Object> previewRetrySchedule(RetryPreset preset) {
    Map<String, Object> preview = new HashMap<>();
    preview.put("preset", preset.getCode());
    preview.put("displayName", preset.getDisplayName());
    preview.put("maxRetryCount", preset.getMaxRetryCount());
    preview.put("baseBackoffMs", preset.getBaseBackoffMs());
    preview.put("backoffMultiplier", preset.getBackoffMultiplier());
    preview.put("maxBackoffMs", preset.getMaxBackoffMs());

    List<Map<String, Object>> timeline = new ArrayList<>();
    long cumulativeMs = 0L;
    LocalDateTime baseTime = LocalDateTime.now();

    for (int retry = 0; retry < preset.getMaxRetryCount(); retry++) {
      long backoffMs = calcBackoffMs(retry, preset);
      cumulativeMs += backoffMs;
      LocalDateTime triggerAt = baseTime.plusNanos(backoffMs * 1_000_000L);

      Map<String, Object> entry = new HashMap<>();
      entry.put("retryIndex", retry + 1); // 第 N 次重试（从 1 开始）
      entry.put("backoffMs", backoffMs);
      entry.put("backoffSeconds", String.format("%.1f", backoffMs / 1000.0));
      entry.put("cumulativeMs", cumulativeMs);
      entry.put("cumulativeSeconds", String.format("%.1f", cumulativeMs / 1000.0));
      entry.put("triggerAt", triggerAt.format(TIME_FMT));
      timeline.add(entry);
    }

    preview.put("timeline", timeline);
    preview.put("totalRetries", preset.getMaxRetryCount());
    preview.put("totalDurationMs", cumulativeMs);
    preview.put("totalDurationSeconds", String.format("%.1f", cumulativeMs / 1000.0));
    preview.put("generatedAt", baseTime.format(TIME_FMT));
    return preview;
  }

  /**
   * 计算第 N 次重试的退避时间（毫秒）。
   *
   * <p>公式：{@code min(baseBackoffMs * backoffMultiplier^retryIndex, maxBackoffMs)}
   */
  private long calcBackoffMs(int retryIndex, RetryPreset preset) {
    if (preset.getMaxRetryCount() == 0) {
      return 0L;
    }
    double raw = preset.getBaseBackoffMs() * Math.pow(preset.getBackoffMultiplier(), retryIndex);
    return Math.min((long) raw, preset.getMaxBackoffMs());
  }
}
