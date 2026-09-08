package com.njydsz.generator.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseAuditEntity;

/**
 * 代码生成器模板分组领域实体。
 *
 * <p>对应 ydsz_gen_template_group 表。模板按技术栈/风格分组，
 * 如 default（标准 DDD）、mybatis-plus、mongodb 等。
 * 同一时间只有一个分组处于激活状态。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_gen_template_group")
public class GenTemplateGroup extends MpBaseAuditEntity<Long> {

  /** 主键 ID（AUTO 自增，覆盖基类 ASSIGN_ID）。 */
  @TableId(type = IdType.AUTO)
  private Long id;
  /** 分组名（UNIQUE，如 default、mybatis-plus）。 */
  private String name;
  /** 分组描述。 */
  private String description;
  /** 是否为系统分组（系统分组不可删除）。 */
  private Boolean isSystem;
  /** 排序序号（升序）。 */
  private Integer sort;
  /** 是否激活为当前使用分组。 */
  private Boolean isActive;
}
