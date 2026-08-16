package com.njydsz.message.web.controller.canary;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.domain.dto.canary.CanaryReportVO;
import com.njydsz.message.server.service.canary.CanaryReportService;

/**
 * 灰度 A/B 报表（Canary Report）Controller。
 *
 * <p>提供<b>灰度实验的命中/转化对比</b>数据端点，是 P1-6「灰度可观测性」的核心入口。 通过对比实验组（命中灰度）与对照组（未命中）的发送成功率 / 送达率 / 阅读率 / 点击率，
 * 运营可量化评估新模板 / 新渠道的效果，决定是否全量发布。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/canary/report/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>实验对比报表</b>：{@code GET /} — 给定 (canaryKey, start, end) 返回实验组 vs 对照组的指标对比
 * </ul>
 *
 * <p><b>报表指标：</b>{@code CanaryReportVO} 包含：
 *
 * <ul>
 *   <li><b>实验组（treatment）</b>：命中灰度的消息，统计发送数 / 成功数 / 送达数 / 阅读数 / 点击数
 *   <li><b>对照组（control）</b>：未命中灰度的消息，统计同上
 *   <li><b>对比</b>：各指标实验组 vs 对照组的差异（绝对值 / 提升百分比）
 *   <li><b>置信度</b>：基于样本量的统计显著性（z-test / 卡方检验）
 * </ul>
 *
 * <p><b>典型场景：</b>
 *
 * <ul>
 *   <li>新短信模板灰度：实验组阅读率 +15% → 决策全量上线
 *   <li>新渠道灰度：实验组送达率 -2% → 决策回退
 *   <li>时机优化灰度：实验组点击率 +8% 但投诉率 +20% → 决策谨慎放量
 * </ul>
 *
 * <p><b>与 CanaryController 的关系：</b>{@code CanaryController} 管理灰度配置（CRUD）， 本 Controller
 * 提供实验效果对比报表；二者通过 {@code canaryKey} 关联。
 *
 * <p><b>时间范围：</b>{@code start / end} 不指定时默认最近 7 天，最长支持 90 天。 大范围查询建议指定 {@code start / end}
 * 缩小扫描范围，提升性能。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>只读接口，仅启用 {@code @AuthApiPermission} 权限校验
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#MESSAGE_CANARY_REPORT} 权限码
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.canary.CanaryReportService 灰度报表服务
 * @see com.njydsz.message.domain.dto.canary.CanaryReportVO 报表 VO
 */
@Slf4j
@Tag(name = "灰度A/B报表", description = "灰度实验命中/转化对比统计")
@RestController
@RequestMapping("/api/v1/message/canary/report")
@RequiredArgsConstructor
public class CanaryReportController {

  /** 灰度报表服务 */
  private final CanaryReportService canaryReportService;

  /**
   * 获取灰度 A/B 实验报表。
   *
   * @param canaryKey 灰度键（原始模板编码），必填
   * @param start 起始时间（ISO 格式 yyyy-MM-dd'T'HH:mm:ss，可选，默认最近 7 天）
   * @param end 结束时间（ISO 格式，可选，默认当前时间）
   * @return A/B 报表（含对照组与实验组统计）
   */
  @Operation(summary = "获取灰度A/B实验报表")
  @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_CANARY_REPORT)
  @GetMapping
  public BaseResponse<CanaryReportVO> getReport(
      @Parameter(description = "灰度键(原始模板编码)", required = true) @RequestParam String canaryKey,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime start,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime end) {
    return BaseResponse.success(canaryReportService.getReport(canaryKey, start, end));
  }
}
