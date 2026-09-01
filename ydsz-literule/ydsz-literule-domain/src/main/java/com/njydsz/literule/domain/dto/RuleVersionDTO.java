package com.njydsz.literule.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 规则版本 DTO（统一新增/修改）。
 *
 * <p>创建时 {@code id} 字段不传，更新时传入 {@code id}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleVersionDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 版本记录 ID（主键，更新时传入） */
  private Long id;

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
