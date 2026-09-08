package com.njydsz.generator.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseIdEntity;

/**
 * 代码生成任务文件明细领域实体。
 *
 * <p>对应 ydsz_gen_history_file 表，记录某次任务中每个生成文件的操作结果。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_gen_history_file")
public class GenHistoryFile extends MpBaseIdEntity<Long> {

  /** 主键 ID（AUTO 自增，覆盖基类 ASSIGN_ID）。 */
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
