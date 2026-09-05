package com.njydsz.generator.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成任务文件明细实体。
 *
 * <p>记录某次任务中每个生成文件的操作结果。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenHistoryFile {

  /** 记录 ID。 */
  private Long id;
  /** 所属任务 ID。 */
  private Long historyId;
  /** 生成文件路径。 */
  private String filePath;
  /** 原文件备份路径（用于回滚）。 */
  private String originalBackupPath;
  /** 文件内容 MD5 哈希。 */
  private String fileHash;
  /** 文件操作类型（CREATED/UPDATED/UNCHANGED）。 */
  private String action;
}
