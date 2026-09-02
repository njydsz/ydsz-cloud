package com.njydsz.literule.server.spi;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * 规则冲突检测提供者 SPI
 *
 * <p>由消费方（如 project 模块）提供实现，通过分析条件表达式中的变量引用， 检测多条规则之间是否存在重叠。将原有 {@code RuleConflictDetector} 的能力抽象为
 * SPI， 避免 literule 模块直接依赖 project 模块。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public interface RuleConflictDetectorProvider {

  /**
   * 检测所有启用规则之间的冲突
   *
   * @return 冲突规则对列表
   */
  List<RuleConflictInfo> detectConflicts();

  /** 冲突信息 DTO */
  @Data
  @Builder
  class RuleConflictInfo {
    private String ruleA;
    private String ruleAName;
    private String ruleB;
    private String ruleBName;
    private List<String> overlapFields;
    private String severity;
  }
}
