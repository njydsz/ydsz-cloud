package com.njydsz.workflow.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 流程归档断点续传游标实体（ydsz_flow_archive_cursor）。
 *
 * <p>记录 FlowHistoryArchiveService 上次归档的最大 end_time，实现断点续传能力，
 * 避免每次从头扫描，也支持运维人员手动重置游标以重新归档。
 *
 * <p><b>游标语义：</b>
 *
 * <ul>
 *   <li>archive_type = INSTANCE：value = 上次归档的最大 end_time（ISO-8601）</li>
 *   <li>archive_type = PURGE：value = 上次清理的最大 id</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("ydsz_flow_archive_cursor")
public class FlowArchiveCursor {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 主键 ID（Snowflake） */
  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  /** 归档类型（INSTANCE / PURGE） */
  private String archiveType;

  /** 游标值（最大 end_time 或最大 id） */
  private String cursorValue;

  /** 附加数据 JSON */
  private String cursorData;

  /** 租户 ID */
  private String tenantId;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 最后更新时间 */
  private LocalDateTime updatedAt;
}
