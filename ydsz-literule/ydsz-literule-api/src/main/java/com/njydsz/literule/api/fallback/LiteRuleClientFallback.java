package com.njydsz.literule.api.fallback;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.literule.api.RuleResult;
import com.njydsz.literule.api.client.LiteRuleClient;

/**
 * {@link LiteRuleClient} 的 FallbackFactory
 *
 * <p>当规则引擎服务不可用时降级返回统一错误码 ({@link FeignClientConstants#FEIGN_SERVICE_UNAVAILABLE})， 仅记录 WARN
 * 日志，保证调用方主流程不受影响。
 *
 * <p>注意：必须返回 error 而非 success(emptyList)， 否则调用方通过 {@code isSuccess()} 检查会误判为评估成功（无规则触发）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class LiteRuleClientFallback implements FallbackFactory<LiteRuleClient> {

  @Override
  public LiteRuleClient create(Throwable cause) {
    log.warn("[LiteRuleClient] 降级触发: {}", cause.getMessage());
    return new LiteRuleClient() {
      @Override
      public YdszResponse<List<RuleResult>> dryRun(String ruleCode, Map<String, Object> facts) {
        log.warn("[LiteRuleClient] dryRun 降级: ruleCode={}, reason=规则引擎服务不可用", ruleCode);
        return YdszResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "规则引擎服务不可用");
      }

      @Override
      public YdszResponse<List<RuleResult>> evaluate(
          String ruleCode, String scenario, Map<String, Object> facts) {
        log.warn(
            "[LiteRuleClient] evaluate 降级: ruleCode={}, scenario={}, reason=规则引擎服务不可用",
            ruleCode,
            scenario);
        return YdszResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "规则引擎服务不可用");
      }
    };
  }
}
