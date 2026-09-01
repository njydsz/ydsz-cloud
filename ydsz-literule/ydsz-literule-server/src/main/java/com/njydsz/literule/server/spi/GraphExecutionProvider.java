package com.njydsz.literule.server.spi;

import java.util.List;
import java.util.Map;

import com.njydsz.literule.domain.vo.RuleResultVO;

/**
 * 画布执行提供者 SPI
 *
 * <p>由消费方（如 project 模块）提供实现，将可视化画布转换为可执行的规则链并执行评估。 将原有 {@code GraphExecutionService} 的能力抽象为 SPI，避免
 * literule 模块直接依赖 project 模块。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public interface GraphExecutionProvider {

  /**
   * 对指定规则的画布执行 Dry-run 仿真
   *
   * @param ruleCode 规则编码（画布关联 key）
   * @param facts 事实数据
   * @return 评估结果列表（已触发的规则结果）；画布为空或转换失败返回空列表
   */
  List<RuleResultVO> dryRunGraph(String ruleCode, Map<String, Object> facts);

  /**
   * 收集画布中引用了但已失效（不存在/已禁用）的规则编码
   *
   * @param ruleCode 规则编码
   * @return 失效规则编码列表（无失效返回空列表）
   */
  List<String> collectInvalidReferences(String ruleCode);
}
