package com.njydsz.literule.domain.vo;

import java.util.List;

import lombok.Data;

/**
 * 规则版本差异视图对象（VO）。
 *
 * <p>用于前端展示单条规则两个版本之间的差异，既可整体呈现（版本号、差异条目列表、 摘要），也可逐字段呈现（类型、字段名、前后值）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class RuleVersionDiffVO {

  /** 对比的源版本号（旧版本） */
  private int oldVersion;

  /** 对比的目标版本号（新版本） */
  private int newVersion;

  /** 规则编码 */
  private String ruleCode;

  /** 差异条目列表（每项为一个字段级差异对象） */
  private List<Object> entries;

  /** 差异整体摘要（如"修改了 3 个字段"） */
  private String summary;

  /** 单条差异类型（如 MODIFY/ADD/REMOVE） */
  private String type;

  /** 差异字段名（英文标识） */
  private String field;

  /** 差异字段中文标签（展示用） */
  private String fieldLabel;

  /** 变更前的值 */
  private String oldValue;

  /** 变更后的值 */
  private String newValue;
}
