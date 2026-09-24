package com.njydsz.system.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.system.domain.dto.MonitorErrorBatchDTO;
import com.njydsz.system.domain.dto.MonitorWebVitalBatchDTO;
import com.njydsz.system.server.service.MonitorReportService;

/**
 * 前端监控上报接收控制器。
 *
 * <p>接收 ydsz-micro 中 {@code @ydsz/monitor} 上报的前端错误、Web Vitals 指标与
 * sourcemap 文件，是本系统「前端可观测性闭环」的服务端入口。此前前端已具备完整的
 * 采集与上报能力（错误捕获 / Core Web Vitals / sourcemap 上传脚本），但服务端
 * 没有对应接收端点，上报数据全部落空（见 ADR-006 与对标报告 P0-1）。
 *
 * <p><b>请求路径说明：</b>网关对 {@code /api/**} 路由执行 {@code StripPrefix=1}，
 * 故本控制器的映射路径不含 {@code /api} 前缀。sourcemap 上报路径中的 {@code v1}
 * 为前端脚本既定契约（{@code /api/v1/monitor/sourcemaps}），同时兼容无版本段的
 * {@code /monitor/sourcemaps}。
 *
 * <p><b>接口清单：</b>
 *
 * <ul>
 *   <li>{@code POST /monitor/error} — 前端错误批量上报（JSON，sendBeacon/keepalive fetch）
 *   <li>{@code POST /monitor/web-vitals} — Web Vitals 批量上报（JSON，可带 {@code ?alert=true}）
 *   <li>{@code POST /v1/monitor/sourcemaps} — sourcemap 上传（octet-stream 原始字节）
 * </ul>
 *
 * <p><b>鉴权说明：</b>前端上报通道（sendBeacon / 构建脚本 fetch）不携带 Token，
 * 需在网关 {@code AuthGlobalFilter} 白名单中放行对应前缀，并由网关限流过滤器
 * 承担防滥用职责。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
@ApiVersion("26.09.01")
@Slf4j
@RestController
@RequiredArgsConstructor
public class MonitorReportController {

  /** 单个 sourcemap 文件体积上限（字节）：20MB，防超大报文耗尽内存 */
  private static final int MAX_SOURCEMAP_BYTES = 20 * 1024 * 1024;

  /** 前端监控上报处理服务 */
  private final MonitorReportService monitorReportService;

  /**
   * 接收前端错误批量上报。
   *
   * <p>前端经 {@code navigator.sendBeacon}（降级 keepalive fetch）发送，
   * 请求体形如 {@code { "errors": [ ... ] }}。服务端按错误类型指标化，不落库。
   *
   * @param batch 错误批量上报体，条目数与非空由 {@code @Valid} 校验
   * @return 统一响应体，成功时 data 为空
   */
  @PostMapping("/monitor/error")
  public YdszResponse<Void> reportErrors(@Valid @RequestBody MonitorErrorBatchDTO batch) {
    monitorReportService.recordErrors(batch);
    return YdszResponse.success();
  }

  /**
   * 接收前端 Web Vitals 批量上报。
   *
   * <p>前端每 5 秒或缓冲满 6 条时批量发送，请求体形如 {@code { "vitals": [ ... ] }}；
   * 性能超阈值时附加 {@code ?alert=true}，服务端将同时发布性能告警。
   *
   * @param batch Web Vitals 批量上报体，条目数与非空由 {@code @Valid} 校验
   * @param alert 是否为性能阈值告警上报，默认 false
   * @return 统一响应体，成功时 data 为空
   */
  @PostMapping("/monitor/web-vitals")
  public YdszResponse<Void> reportWebVitals(
      @Valid @RequestBody MonitorWebVitalBatchDTO batch,
      @RequestParam(name = "alert", defaultValue = "false") boolean alert) {
    monitorReportService.recordWebVitals(batch, alert);
    return YdszResponse.success();
  }

  /**
   * 接收 sourcemap 文件上传。
   *
   * <p>由 {@code bash/upload-sourcemaps.mjs} 在生产构建后调用，请求体为 {@code .map}
   * 文件原始字节（{@code Content-Type: application/octet-stream}），版本与文件名经查询
   * 参数传入。文件落对象存储，供前端错误堆栈符号化使用。
   *
   * <p><b>路径中的 {@code v1} 说明：</b>前端构建脚本（{@code upload-sourcemaps.mjs}）
   * 硬编码 {@code /api/v1/monitor/sourcemaps} 端点，属于发布时既定契约，
   * 不随后端 API 版本演进而变化。同时兼容无版本段的 {@code /monitor/sourcemaps}。
   *
   * <!-- YDIZ-API-001 豁免：前端 sourcemap 上传硬编码契约，v1 与后端 API 版本无关 -->
   *
   * @param release 发布版本标识（commit hash / 版本号），必填
   * @param file 前端构建产物内的相对文件路径，必填
   * @param content sourcemap 文件字节内容
   * @return 统一响应体，成功时 data 为对象存储返回的可访问 URL
   */
  @PostMapping({"/v1/monitor/sourcemaps", "/monitor/sourcemaps"})
  public YdszResponse<String> uploadSourcemap(
      @RequestParam("release") String release,
      @RequestParam("file") String file,
      @RequestBody byte[] content) {
    if (content.length > MAX_SOURCEMAP_BYTES) {
      log.warn("[MonitorReport] sourcemap 超出体积上限, release={}, file={}, size={}B",
          release, file, content.length);
      return YdszResponse.error(YdszResultCode.BAD_REQUEST);
    }
    String url = monitorReportService.storeSourcemap(release, file, content);
    return YdszResponse.success(url);
  }
}
