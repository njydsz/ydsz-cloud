package com.njydsz.nextwiki.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 文件-标签关联实体（多对多）
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName("nw_file_tag")
public class FileTag extends MpBaseEntity<String> implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 文件节点ID */
  private String fileNodeId;

  /** 标签ID */
  private String tagId;
}
