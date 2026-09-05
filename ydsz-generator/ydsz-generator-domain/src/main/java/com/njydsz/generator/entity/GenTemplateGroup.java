package com.njydsz.generator.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ydsz_gen_template_group")
public class GenTemplateGroup {

  /** 分组 ID。 */
  @TableId(type = IdType.AUTO)
  private Long id;
  /** 分组名（UNIQUE，如 default、mybatis-plus）。 */
  private String name;
  /** 分组描述。 */
  private String description;
  /** 是否为系统分组（系统分组不可删除）。 */
  private Boolean system;
  /** 排序序号（升序）。 */
  private Integer sortOrder;
  /** 是否激活为当前使用分组。 */
  private Boolean active;
  /** 创建时间。 */
  private LocalDateTime createdAt;
  /** 更新时间。 */
  private LocalDateTime updatedAt;
}
