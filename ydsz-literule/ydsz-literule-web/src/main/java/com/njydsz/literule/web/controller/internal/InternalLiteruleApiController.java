package com.njydsz.literule.web.controller.internal;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.literule.domain.RuleEngine;
import com.njydsz.literule.server.converter.LiteruleWebConverter;
import com.njydsz.literule.domain.vo.RuleContextVO;
import com.njydsz.literule.domain.vo.RuleResultVO;
import com.njydsz.literule.server.config.RuleAdminService;

/**
 * 内部 API Controller（服务间 Feign 调用）
 *
 * <p>为 <b>跨服务 Feign 调用</b> 提供统一 HTTP 入口。端点<b>仅用于服务间通信</b>，不应直接对外暴露。
 *
 * <p><b>接口路径：</b>{@code /api/internal/**}
 *
 * <p><b>安全要求：</b>
 *
 * <ul>
 *   <li>Gateway 应限制 {@code /api/internal/**} 仅允许<b>内部服务 IP</b>调用（白名单），对公网不可访问
 *   <li>事实数据（facts）通过 <b>POST body</b> 传输，<b>严禁</b>出现在 URL 中
 *   <li>所有接口启用 {@link RateLimit} 接口级限流（50 QPS），防止被恶意刷接口
 *   <li>dry-run 接口启用 {@link Idempotent} 幂等保护（5 秒），避免重试风暴
 * </ul>
 *
 * <p><b>响应契约：</b>所有端点统一返回 {@link YdszResponse} 包装，与 {@code ydsz-literule-api} 模块中
 * {@code LiteRuleClient} 的 Feign 声明严格对齐。
 *
 * <p><b>设计说明：</b>正式评估接口不启用 {@code @Idempotent}（避免批评估场景误拒），通过 {@code @RateLimit}
 * 提供接口级限流防护；租户与链路 ID 从网关注入的请求头解析。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.literule.api.client.LiteRuleClient Feign Client 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@AuthApiPermission(apiCodes = "literule:internal:api")
public class InternalLiteruleApiController {

  private final RuleAdminService ruleAdminService;
  private final RuleEngine ruleEngine;

  /**
   * 规则评估（dry-run 仿真模式，不记录统计、不发布事件、不触发动作分发）
   *
   * <p>对应 literule 模块：POST /api/internal/literule/rules/dry-run
   *
   * @param ruleCode 规则编码（可选，null 时评估全部规则）
   * @param facts 事实数据（如 metric / threshold / value）
   * @return 触发的规则结果列表（按严重度倒序），未触发任何规则时返回空列表
   */
  @RateLimit(resource = "literule.internalapi.dryRun", threshold = 50)
  @Idempotent(
      key = "'ydsz:literule:internal-api:dry-run:' + (#ruleCode ?: 'ALL')",
      ttlSeconds = 5)
  @PostMapping("/literule/rules/dry-run")
  public YdszResponse<List<RuleResultVO>> dryRun(
      @RequestParam(value = "ruleCode", required = false) String ruleCode,
      @RequestBody Map<String, Object> facts) {
    return YdszResponse.success(
        ruleAdminService.dryRun(ruleCode, facts).stream()
            .map(LiteruleWebConverter.INSTANCE::entityToVO)
            .toList());
  }

  /**
   * 规则评估（正式模式，记录统计、发布事件、触发动作分发）
   *
   * <p>与 dryRun 的区别：正式评估会记录执行统计（评估次数 / 触发次数 / 耗时），发布规则触发事件（供消息中心等下游消费），
   * 并触发动作分发（如发送通知、调用接口）。
   *
   * @param ruleCode 规则编码（可选，null 时评估全部规则）
   * @param scenario 场景标识（可选，用于规则过滤和统计分组，缺省 DEFAULT）
   * @param facts 事实数据
   * @return 触发的规则结果列表（按严重度倒序），未触发任何规则时返回空列表
   */
  @RateLimit(resource = "literule.internalapi.evaluate", threshold = 50)
  @PostMapping("/literule/rules/evaluate")
  public YdszResponse<List<RuleResultVO>> evaluate(
      @RequestParam(value = "ruleCode", required = false) String ruleCode,
      @RequestParam(value = "scenario", required = false) String scenario,
      @RequestBody Map<String, Object> facts) {
    String scen = scenario != null ? scenario : "DEFAULT";
    RuleContextVO context = RuleContextVO.of(facts, scen, "feign-internal");
    List<RuleResultVO> results = ruleEngine.evaluate(context);
    List<RuleResultVO> filtered =
        ruleCode == null
            ? results
            : results.stream().filter(r -> ruleCode.equals(r.getRuleCode())).toList();
    return YdszResponse.success(
        filtered.stream().map(LiteruleWebConverter.INSTANCE::entityToVO).toList());
  }
}
