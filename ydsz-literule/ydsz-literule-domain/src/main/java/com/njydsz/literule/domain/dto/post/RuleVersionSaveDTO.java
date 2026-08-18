package com.njydsz.literule.domain.dto.post;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 规则版本保存 DTO。
 *
 * <p>用于 {@code RuleVersionRepository.saveVersion()} 参数，封装规则版本快照的完整信息。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleVersionSaveDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 规则编码 */
  private String ruleCode;

  /** 规则名称 */
  private String ruleName;

  /** 版本号 */
  private Integer version;

  /** 规则定义 JSON 快照 */
  private String definitionJson;

  /** 变更说明 */
  private String changeDesc;

  /** 操作人 */
  private String operator;
}
