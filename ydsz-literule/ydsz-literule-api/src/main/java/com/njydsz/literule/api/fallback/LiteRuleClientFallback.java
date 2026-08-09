package com.njydsz.literule.api.fallback;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.literule.api.RuleResult;
import com.njydsz.literule.api.client.LiteRuleClient;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link LiteRuleClient} 的 FallbackFactory
 *
 * <p>当规则引擎服务不可用时降级返回空列表，仅记录 WARN 日志，
 * 保证调用方主流程不受影响（规则评估是辅助决策，不应阻断业务）。
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
            public BaseResponse<List<RuleResult>> dryRun(String ruleCode, Map<String, Object> facts) {
                log.warn("[LiteRuleClient] dryRun 降级: ruleCode={}, reason=规则引擎服务不可用", ruleCode);
                return BaseResponse.success(Collections.emptyList());
            }

            @Override
            public BaseResponse<List<RuleResult>> evaluate(String ruleCode, String scenario,
                                                             Map<String, Object> facts) {
                log.warn("[LiteRuleClient] evaluate 降级: ruleCode={}, scenario={}, reason=规则引擎服务不可用",
                        ruleCode, scenario);
                return BaseResponse.success(Collections.emptyList());
            }
        };
    }
}
