package com.njydsz.agent.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * Prompt 模板持久化对象（映射 ydsz_agt_prompt_template 表）。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_prompt_template")
public class PromptTemplatePO extends MpBaseEntity<String> {

  private static final long serialVersionUID = 1L;

  /** 模板唯一编码（业务标识，创建后不可变） */
  private String templateCode;

  /** 模板名称（展示用） */
  private String templateName;

  /** 模板内容，支持 #{var} 占位符 */
  private String content;

  /** 模板描述 */
  private String description;

  /** 分类（用于分组检索） */
  private String category;

  /** 当前版本号 */
  private Integer currentVersion;

  /** 是否启用 A/B 灰度测试 */
  private Boolean isAbTestEnabled;

  /** A/B 灰度目标版本 */
  private Integer abTargetVersion;

  /** A/B 灰度流量百分比（1-100） */
  private Integer abTrafficPercent;
}
