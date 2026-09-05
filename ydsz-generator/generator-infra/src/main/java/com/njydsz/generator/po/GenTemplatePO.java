package com.njydsz.generator.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模板持久化对象。
 *
 * <p>对应 gen_template 表。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@TableName("gen_template")
public class GenTemplatePO {

  /** 主键 ID。 */
  @TableId(type = IdType.AUTO)
  private Long id;
  /** 所属分组 ID。 */
  private Long groupId;
  /** 文件名。 */
  private String fileName;
  /** 描述。 */
  private String description;
  /** 模板内容。 */
  private String content;
  /** 是否虚拟文件夹。 */
  private Boolean isFolder;
  /** 父路径。 */
  private String parentPath;
  /** 版本号。 */
  private Integer version;
  /** 内容哈希。 */
  private String hash;
  /** 是否启用。 */
  private Boolean isActive;
  /** 模板类型。 */
  private String fileType;
  /** 创建时间。 */
  private LocalDateTime createdAt;
  /** 更新时间。 */
  private LocalDateTime updatedAt;
}
