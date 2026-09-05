package com.njydsz.generator.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 生成历史文件明细持久化对象。
 *
 * <p>对应 gen_history_file 表。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@TableName("ydsz_gen_history_file")
public class GenHistoryFilePO {

  /** 主键 ID。 */
  @TableId(type = IdType.AUTO)
  private Long id;
  /** 任务 ID。 */
  private Long historyId;
  /** 文件路径。 */
  private String filePath;
  /** 原文件备份路径。 */
  private String originalBackupPath;
  /** 文件哈希。 */
  private String fileHash;
  /** 操作类型。 */
  private String action;
}
