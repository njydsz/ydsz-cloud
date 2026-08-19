package com.njydsz.message.web.controller;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.server.service.retry.RetryPreset;
import com.njydsz.message.server.service.retry.RetryPreviewService;

/**
 * 重试策略预览 Controller（P3-2: 交互式预览）。
 *
 * <p>提供重试预设档位的可视化预览 API，帮助使用者在配置前直观理解不同档位的重试行为。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/retry/**}
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@Tag(name = "重试策略预览", description = "重试预设档位可视化预览")
@RestController
@RequestMapping("/api/v1/message/retry")
@RequiredArgsConstructor
public class RetryPreviewController {

  private final RetryPreviewService retryPreviewService;

  /**
   * 预览指定预设档位的重试时间线。
   *
   * <p>返回每次重试的详细信息：重序号、退避间隔、预计触发时刻、累计等待时间。
   *
   * @param preset 预设档位标识（none / fast / standard / relaxed），不传时返回 standard
   * @return 重试时间线预览
   */
  @Operation(summary = "预览指定预设的重试时间线")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_LOG_VIEW)
  @GetMapping("/preview")
  public YdszResponse<Map<String, Object>> previewRetrySchedule(
      @Parameter(description = "预设档位: none/fast/standard/relaxed")
          @RequestParam(value = "preset", required = false, defaultValue = "standard")
          String preset) {
    return YdszResponse.success(retryPreviewService.previewRetrySchedule(preset));
  }

  /**
   * 对比所有预设档位的重试策略。
   *
   * <p>返回所有预设档位的完整信息（参数 + 时间线），便于前端展示对比表格。
   *
   * @return 所有预设的对比视图
   */
  @Operation(summary = "对比所有预设档位的重试策略")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_LOG_VIEW)
  @GetMapping("/preview/all")
  public YdszResponse<Map<String, Map<String, Object>>> previewAllPresets() {
    return YdszResponse.success(retryPreviewService.previewAllPresets());
  }

  /**
   * 获取所有可用的预设档位列表。
   *
   * <p>返回每个预设的元信息（code / displayName / 参数），供前端下拉选择。
   *
   * @return 预设档位列表
   */
  @Operation(summary = "获取所有可用预设档位")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_LOG_VIEW)
  @GetMapping("/presets")
  public YdszResponse<Map<String, Object>> listPresets() {
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    for (RetryPreset preset : RetryPreset.values()) {
      Map<String, Object> info = new java.util.LinkedHashMap<>();
      info.put("code", preset.getCode());
      info.put("displayName", preset.getDisplayName());
      info.put("maxRetryCount", preset.getMaxRetryCount());
      info.put("baseBackoffMs", preset.getBaseBackoffMs());
      info.put("backoffMultiplier", preset.getBackoffMultiplier());
      info.put("maxBackoffMs", preset.getMaxBackoffMs());
      result.put(preset.getCode(), info);
    }
    return YdszResponse.success(result);
  }
}
