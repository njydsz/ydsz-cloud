package com.njydsz.generator.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代码生成任务历史持久化对象。
 *
 * <p>对应 gen_history 表。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@TableName("gen_history")
public class GenHistoryPO {

  /** 主键 ID。 */
  @TableId(type = IdType.AUTO)
  private Long id;
  /** 模块名称。 */
  private String moduleName;
  /** 数据源 ID。 */
  private Long datasourceId;
  /** 模板分组 ID。 */
  private Long templateGroupId;
  /** 涉及表数量。 */
  private Integer tableCount;
  /** 生成文件总数。 */
  private Integer fileCount;
  /** 执行状态。 */
  private String status;
  /** 触发人。 */
  private String triggeredBy;
  /** 开始时间。 */
  private LocalDateTime startedAt;
  /** 完成时间。 */
  private LocalDateTime finishedAt;
  /** 错误信息。 */
  private String errorMessage;
  /** 生成参数 JSON。 */
  private String genParams;
}
