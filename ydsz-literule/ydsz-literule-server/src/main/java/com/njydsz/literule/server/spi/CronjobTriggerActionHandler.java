package com.njydsz.literule.server.spi;

import com.njydsz.cronjob.api.client.CronjobServiceClient;
import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleResult;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * 定时任务触发动作处理器
 *
 * <p>规则触发后自动触发关联的 cronjob 定时任务，实现规则与定时任务联动。 依赖 {@code ydsz-cronjob-api} 模块提供的 {@link
 * CronjobServiceClient}。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class CronjobTriggerActionHandler implements RuleActionHandler {

  private final CronjobServiceClient cronjobClient;

  public CronjobTriggerActionHandler(CronjobServiceClient cronjobClient) {
    this.cronjobClient = cronjobClient;
  }

  @Override
  public void handle(List<RuleResult> triggered, RuleContext context) {
    for (RuleResult result : triggered) {
      try {
        String jobKey = result.getRuleCode();
        cronjobClient.trigger(jobKey);
        log.debug("[LiteRule-Action] 定时任务已触发: jobKey={}", jobKey);
      } catch (Exception e) {
        log.warn(
            "[LiteRule-Action] 定时任务触发失败: ruleCode={}, error={}",
            result.getRuleCode(),
            e.getMessage());
      }
    }
  }
}
