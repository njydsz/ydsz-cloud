package com.njydsz.generator.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成任务文件明细领域实体。
 *
 * <p>对应 ydsz_gen_history_file 表，记录某次任务中每个生成文件的操作结果。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ydsz_gen_history_file")
public class GenHistoryFile {

  /** 记录 ID。 */
  @TableId(type = IdType.AUTO)
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
