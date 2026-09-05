package com.njydsz.generator.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模板分组持久化对象。
 *
 * <p>对应 gen_template_group 表。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@TableName("gen_template_group")
public class GenTemplateGroupPO {

  /** 主键 ID。 */
  @TableId(type = IdType.AUTO)
  private Long id;
  /** 分组名。 */
  private String name;
  /** 描述。 */
  private String description;
  /** 是否系统分组。 */
  private Boolean isSystem;
  /** 排序序号。 */
  private Integer sortOrder;
  /** 是否激活。 */
  private Boolean isActive;
  /** 创建时间。 */
  private LocalDateTime createdAt;
  /** 更新时间。 */
  private LocalDateTime updatedAt;
}
