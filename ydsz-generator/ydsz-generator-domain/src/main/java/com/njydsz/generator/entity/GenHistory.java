package com.njydsz.generator.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseIdEntity;

/**
 * 代码生成任务/历史领域实体。
 *
 * <p>对应 ydsz_gen_history 表，记录一次完整代码生成任务的元信息。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_gen_history")
public class GenHistory extends MpBaseIdEntity<Long> {

  /** 主键 ID（AUTO 自增，覆盖基类 ASSIGN_ID）。 */
  @TableId(type = IdType.AUTO)
  private Long id;
  /** 模块名称。 */
  private String moduleName;
  /** 使用的数据源 ID。 */
  private Long datasourceId;
  /** 使用的模板分组 ID。 */
  private Long templateGroupId;
  /** 涉及表数量。 */
  private Integer tableCount;
  /** 生成文件总数。 */
  private Integer fileCount;
  /** 任务执行状态码（RUNNING/SUCCESS/PARTIAL/FAILED）。 */
  private String status;
  /** 触发人。 */
  private String triggeredBy;
  /** 开始时间。 */
  private LocalDateTime startedAt;
  /** 完成时间。 */
  private LocalDateTime finishedAt;
  /** 错误信息（失败时记录）。 */
  private String errorMessage;
  /** 生成参数 JSON 快照。 */
  private String genParams;
}
