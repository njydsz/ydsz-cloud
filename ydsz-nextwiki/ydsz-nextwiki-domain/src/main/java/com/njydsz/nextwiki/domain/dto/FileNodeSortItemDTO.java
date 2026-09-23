package com.njydsz.nextwiki.domain.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件节点排序更新条目 DTO（P0-3：拖拽排序批量 SQL 参数载体）。
 *
 * <p>用于 {@code FileNodeMapper.batchUpdateSort} 的 CASE WHEN 批量更新，携带每条节点的 ID、新排序值、更新人、更新时间。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileNodeSortItemDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 文件节点 ID */
  private String id;

  /** 新排序值 */
  private Integer sort;

  /** 更新人 ID */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
