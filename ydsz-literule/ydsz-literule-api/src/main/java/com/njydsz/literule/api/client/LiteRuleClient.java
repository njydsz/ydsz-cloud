package com.njydsz.literule.api.client;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.literule.domain.api.RuleResult;
import com.njydsz.literule.api.fallback.LiteRuleClientFallback;

/**
 * 规则引擎服务 Feign 客户端
 *
 * <p>供 {@code ydsz-project} / {@code ydsz-userinfo} / {@code ydsz-finance} 等业务服务通过 Feign
 * 远程调用规则引擎，执行规则评估（dry-run 仿真模式）。
 *
 * <h3>典型场景</h3>
 *
 * <ul>
 *   <li>项目立项前调用规则引擎进行风险评估 → 返回触发的告警规则列表
 *   <li>业务校验时调用规则引擎，返回是否触发预警
 *   <li>定时任务批量调用规则引擎评估 EVM 偏差 → 返回风险等级
 *   <li>消息中心收到事件后调用规则引擎评估是否需要通知
 * </ul>
 *
 * <p>使用 {@link LiteRuleClientFallback} 保证规则引擎服务不可用时 不影响调用方主流程（降级为返回空列表 + WARN 日志）。
 *
 * <h3>调用示例</h3>
 *
 * <pre>{@code
 * @Autowired
 * private LiteRuleClient liteRuleClient;
 *
 * public void checkProjectRisk(Map<String, Object> facts) {
 *     YdszResponse<List<RuleResult>> resp = liteRuleClient.dryRun(null, facts);
 *     if (resp.isSuccess() && !resp.getData().isEmpty()) {
 *         // 处理触发的规则
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(
    name = FeignClientConstants.LITERULE,
    contextId = "liteRuleClient",
    fallbackFactory = LiteRuleClientFallback.class)
public interface LiteRuleClient {

  /**
   * 规则评估（dry-run 仿真模式，不记录统计、不发布事件、不触发动作分发）
   *
   * <p>对应 literule 模块: POST /api/v1/literule/rules/dry-run
   *
   * @param ruleCode 规则编码（可选，null 时评估全部规则）
   * @param facts 事实数据（如 {@code metric}, {@code threshold}, {@code value}）
   * @return 触发的规则结果列表（按严重度倒序），未触发任何规则时返回空列表
   */
  @PostMapping(FeignClientConstants.LITERULE_PATH_DRY_RUN)
  YdszResponse<List<RuleResult>> dryRun(
      @RequestParam(value = "ruleCode", required = false) String ruleCode,
      @RequestBody Map<String, Object> facts);

  /**
   * 规则评估（正式模式，记录统计、发布事件、触发动作分发）
   *
   * <p>对应 literule 模块: POST /api/v1/literule/rules/evaluate
   *
   * <p>与 {@link #dryRun} 的区别：
   *
   * <ul>
   *   <li>正式评估会记录执行统计（评估次数/触发次数/耗时）
   *   <li>正式评估会发布规则触发事件（供消息中心等下游消费）
   *   <li>正式评估会触发动作分发（如发送通知、调用接口）
   * </ul>
   *
   * @param ruleCode 规则编码（可选，null 时评估全部规则）
   * @param scenario 场景标识（可选，用于规则过滤和统计分组）
   * @param facts 事实数据
   * @return 触发的规则结果列表（按严重度倒序），未触发任何规则时返回空列表
   * @since 1.0.0
   */
  @PostMapping(FeignClientConstants.LITERULE_PATH_EVALUATE)
  YdszResponse<List<RuleResult>> evaluate(
      @RequestParam(value = "ruleCode", required = false) String ruleCode,
      @RequestParam(value = "scenario", required = false) String scenario,
      @RequestBody Map<String, Object> facts);
}
