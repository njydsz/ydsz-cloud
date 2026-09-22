package com.njydsz.system.server.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.file.domain.FileStorage;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.sentry.SentryObservation;
import com.njydsz.common.sentry.domain.AlertEvent;
import com.njydsz.common.sentry.domain.AlertSeverity;
import com.njydsz.system.domain.dto.MonitorErrorBatchDTO;
import com.njydsz.system.domain.dto.MonitorErrorDTO;
import com.njydsz.system.domain.dto.MonitorWebVitalBatchDTO;
import com.njydsz.system.domain.dto.MonitorWebVitalDTO;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.common.file.storage.AdaptiveMultipartFile;

/**
 * 前端监控上报处理服务。
 *
 * <p>接收 ydsz-micro 中 {@code @ydsz/monitor} 上报的前端错误与 Web Vitals 数据，
 * 按云顶编码规范 §28「可观测性统一接入」的要求，统一经 {@link SentryObservation}
 * 上报为平台指标（由 Prometheus 抓取、Grafana 呈现），sourcemap 经 {@link IFileStorage}
 * 落对象存储。本服务禁止直接注入 {@code MeterRegistry} 注册指标。
 *
 * <p><b>为何不落库：</b>错误明细与性能采样属「高写入、低保留价值」数据，落库会引入
 * 存储与清理成本，且平台已有 Prometheus 指标链路与 ELK 日志链路可用。故本服务只做
 * 「接收 → 指标化 → 转发」，不新建表（规范 §35 业务模块过度设计防范）。
 *
 * <p><b>标签基数防护：</b>前端可上报任意字符串，直接作为标签值会造成 Prometheus 时间
 * 序列爆炸。所有自由取值字段均经白名单归一化，非枚举取值归入 {@code other} / {@code unknown}，
 * 见 {@link #ALLOWED_ERROR_TYPES}、{@link #ALLOWED_VITAL_NAMES}、{@link #ALLOWED_VITAL_RATINGS}。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorReportService {

  // ==================== 指标名（三段式：业务域.实体.动作） ====================

  /** 前端错误计数指标名 */
  private static final String METRIC_ERROR_TOTAL = "frontend.error.total";

  /** 前端 Web Vitals 数值指标名 */
  private static final String METRIC_VITAL_VALUE = "frontend.web_vital.value";

  /** 前端 Web Vitals 阈值告警计数指标名 */
  private static final String METRIC_VITAL_ALERT_TOTAL = "frontend.web_vital.alert.total";

  /** sourcemap 上传计数指标名 */
  private static final String METRIC_SOURCEMAP_TOTAL = "frontend.sourcemap.upload.total";

  // ==================== 标签白名单（防标签爆炸） ====================

  /** 允许的错误类型标签值，与前端 ErrorType 契约一致 */
  private static final Set<String> ALLOWED_ERROR_TYPES =
      Set.of("vue", "window", "promise", "resource");

  /** 允许的 Web Vitals 指标名标签值，与前端 WebVitalName 契约一致 */
  private static final Set<String> ALLOWED_VITAL_NAMES =
      Set.of("LCP", "FID", "CLS", "INP", "FCP", "TTFB", "LT", "RT");

  /** 允许的 Web Vitals 评级标签值，遵循 Google Web Vitals 标准 */
  private static final Set<String> ALLOWED_VITAL_RATINGS =
      Set.of("good", "needs-improvement", "poor");

  /** 白名单外取值的统一归并值（错误类型维度） */
  private static final String TAG_OTHER = "other";

  /** 白名单外取值的统一归并值（指标名前缀，保持可读性） */
  private static final String TAG_UNKNOWN = "unknown";

  /** sourcemap 对象存储路径前缀 */
  private static final String SOURCEMAP_OBJECT_PREFIX = "sourcemaps/";

  /** 告警分类：业务类告警 */
  private static final String ALERT_CATEGORY_BUSINESS = "business";

  /** 告警名称：前端性能阈值告警 */
  private static final String ALERT_NAME_VITAL = "frontend-web-vital-alert";

  /** 对象存储访问入口（可选：未配置对象存储时 getIfAvailable 返回 null，sourcemap 上传明确失败） */
  private final ObjectProvider<IFileStorage> fileStorageProvider;

  /**
   * 记录一批前端错误指标。
   *
   * <p>按错误类型维度计数，不逐条告警——单条前端错误不足以上告警，阈值告警由
   * Prometheus 告警规则基于本指标计算后触发，避免告警风暴。
   *
   * @param batch 错误批量上报体，条目非空（已由 {@code @Valid} 校验）
   */
  public void recordErrors(MonitorErrorBatchDTO batch) {
    List<MonitorErrorDTO> errors = batch.getErrors();
    for (MonitorErrorDTO error : errors) {
      String type = normalize(error.getType(), ALLOWED_ERROR_TYPES, TAG_OTHER);
      SentryObservation.count(
          METRIC_ERROR_TOTAL, "前端错误上报总数", Map.of("type", type));
    }
    log.debug("[MonitorReport] 前端错误已指标化, count={}", errors.size());
  }

  /**
   * 记录一批 Web Vitals 指标，并在告警标记为真时发布性能告警。
   *
   * @param batch Web Vitals 批量上报体，条目非空（已由 {@code @Valid} 校验）
   * @param alert 是否为阈值告警上报（前端附加 {@code ?alert=true}）
   */
  public void recordWebVitals(MonitorWebVitalBatchDTO batch, boolean alert) {
    List<MonitorWebVitalDTO> vitals = batch.getVitals();
    for (MonitorWebVitalDTO vital : vitals) {
      String name = normalize(vital.getName(), ALLOWED_VITAL_NAMES, TAG_UNKNOWN);
      String rating = normalize(vital.getRating(), ALLOWED_VITAL_RATINGS, TAG_UNKNOWN);
      Map<String, String> tags = Map.of("name", name, "rating", rating);
      double value = vital.getValue() == null ? 0.0D : vital.getValue();

      SentryObservation.gauge(METRIC_VITAL_VALUE, "前端 Web Vitals 指标值", tags, value);
      if (alert) {
        SentryObservation.count(METRIC_VITAL_ALERT_TOTAL, "前端性能阈值告警次数", tags);
        publishVitalAlert(name, rating, value);
      }
    }
    log.debug("[MonitorReport] 前端 Web Vitals 已指标化, count={}, alert={}", vitals.size(), alert);
  }

  /**
   * 存储一个前端 sourcemap 文件到对象存储。
   *
   * <p>对象名形如 {@code sourcemaps/{release}/{文件名}}，仅保留文件名部分以阻断
   * {@code ../} 路径穿越（上报参数来自构建脚本，仍按不可信输入处理）。
   *
   * @param release 发布版本标识（commit hash / 版本号）
   * @param file 前端上报的相对文件路径（仅取最后一段作为对象名）
   * @param content sourcemap 文件字节内容
   * @return 对象存储返回的可访问 URL
   * @throws BusinessException 对象存储未配置（{@code MONITOR_SOURCE_STORE_FAILED}）或上传失败
   */
  public String storeSourcemap(String release, String file, byte[] content) {
    IFileStorage storage = fileStorageProvider.getIfAvailable();
    if (storage == null) {
      SentryObservation.count(
          METRIC_SOURCEMAP_TOTAL, "前端 sourcemap 上传次数", Map.of("status", "fail"));
      log.warn("[MonitorReport] 对象存储未配置，sourcemap 上传被拒绝, release={}, file={}", release, file);
      throw BusinessException.of(SystemExceptionCode.MONITOR_SOURCE_STORE_FAILED);
    }

    String objectName = buildSourcemapObjectName(release, file);
    try {
      String contentType = file.endsWith(".map") ? "application/json" : "application/octet-stream";
      FileStorage stored = storage.upload(null, objectName, new AdaptiveMultipartFile(file, contentType, content));
      SentryObservation.count(
          METRIC_SOURCEMAP_TOTAL, "前端 sourcemap 上传次数", Map.of("status", "success"));
      log.info("[MonitorReport] sourcemap 已存储, release={}, object={}, size={}B", release, objectName, content.length);
      return stored.getUrl();
    } catch (RuntimeException ex) {
      SentryObservation.count(
          METRIC_SOURCEMAP_TOTAL, "前端 sourcemap 上传次数", Map.of("status", "fail"));
      log.error("[MonitorReport] sourcemap 存储失败, release={}, object={}", release, objectName, ex);
      throw BusinessException.of(SystemExceptionCode.MONITOR_SOURCE_STORE_FAILED);
    }
  }

  /**
   * 将前端上报的自由取值归一化到白名单内的标签值。
   *
   * @param raw 前端上报的原始取值，可为 {@code null}
   * @param allowed 允许的标签值集合
   * @param fallback 白名单外的归并值
   * @return 白名单内取值；原始值为空或不在白名单时返回 {@code fallback}
   */
  private String normalize(String raw, Set<String> allowed, String fallback) {
    if (raw == null || !allowed.contains(raw)) {
      return fallback;
    }
    return raw;
  }

  /**
   * 发布一条前端性能阈值告警。
   *
   * <p>按规范 §28.4，告警必须经 {@code SentryObservation.alert} 由 AlertConverger 收敛后发布，
   * 禁止以 {@code log.warn} 代替告警通道。
   *
   * @param name 指标名（已归一化）
   * @param rating 评级（已归一化）
   * @param value 指标实测值
   */
  private void publishVitalAlert(String name, String rating, double value) {
    SentryObservation.alert(
        AlertEvent.builder()
            .name(ALERT_NAME_VITAL)
            .severity(AlertSeverity.P2)
            .summary("前端性能指标超出阈值: " + name)
            .description("Web Vital " + name + " 评级为 " + rating + ", 实测值 " + value)
            .category(ALERT_CATEGORY_BUSINESS)
            .labels(Map.of("metric", name, "rating", rating))
            .value(BigDecimal.valueOf(value))
            .build());
  }

  /**
   * 构造 sourcemap 的对象存储名称。
   *
   * @param release 发布版本标识
   * @param file 前端上报的相对文件路径（可能含子目录与反斜杠）
   * @return 形如 {@code sourcemaps/{release}/{文件名}} 的对象名
   */
  private String buildSourcemapObjectName(String release, String file) {
    String normalized = file == null ? "" : file.replace('\\', '/');
    int lastSlash = normalized.lastIndexOf('/');
    String fileName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    return SOURCEMAP_OBJECT_PREFIX + release + "/" + fileName;
  }
}
