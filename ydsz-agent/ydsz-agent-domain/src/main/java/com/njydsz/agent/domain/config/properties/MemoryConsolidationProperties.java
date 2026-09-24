package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 记忆整合配置属性。
 *
 * <p>绑定配置前缀 {@code ydzs.agent.memory.consolidation}，控制 Agent 后台记忆批量整合任务的
 * 启用状态、批次大小、定时执行 Cron 表达式以及 Dreaming 联想增强模式开关。
 * 默认不开启（isEnabled=false）且 Dreaming 关闭，批次大小 50，Cron 默认每天 02:30 执行。
 *
 * @author ydsz
 * @since 26.09.24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryConsolidationProperties {
  private static final int DEFAULT_BATCH_SIZE = 50;

  private boolean isEnabled = false;
  private boolean isDreamingEnabled = false;
  private int batchSize = DEFAULT_BATCH_SIZE;
  private String cron = "0 30 2 * * ?";
}
