package com.njydsz.agent.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * Prompt 模板（domain 层持久化实体，YDIZ-DDD-007 单包模式）
 *
 * <p>存储 Prompt 模板的当前版本信息，包含编码、名称、内容、分类等。
 *
 * <p><b>YDIZ-DDD-007</b>：domain Entity 直接携带 MyBatis-Plus ORM 注解，
 * infra 层通过依赖 domain 模块引用本类，禁止自建 PO/DO 副本。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_prompt_template")
public class PromptTemplate extends MpBaseEntity<String> {

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

  /** 当前版本号，自 1 起每次更新递增 */
  private Integer currentVersion;

  /** 是否启用 A/B 灰度测试 */
  private Boolean isAbTestEnabled;

  /** A/B 灰度目标版本（canary 版本号） */
  private Integer abTargetVersion;

  /** A/B 灰度流量百分比（1-100） */
  private Integer abTrafficPercent;
}
