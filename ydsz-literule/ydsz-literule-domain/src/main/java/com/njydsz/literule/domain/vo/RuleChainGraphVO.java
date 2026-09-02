package com.njydsz.literule.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 规则链图视图对象（VO）。
 *
 * <p>用于 Controller 层返回规则链编排图的完整信息。规则链图以有向无环图（DAG） 方式编排多条规则的执行顺序，支持条件分支和并行执行，适用于复杂决策场景。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class RuleChainGraphVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 链图唯一标识（主键） */
  private String id;

  /** 关联的规则编码 */
  private String ruleCode;

  /** 链图名称 */
  private String name;

  /** 链图描述 */
  private String description;

  /** 执行场景标识 */
  private String scenario;

  /** 图版本号 */
  private Integer graphVersion;

  /** 状态（DRAFT/PUBLISHED） */
  private String status;

  /** 图内容 JSON，包含节点和边定义 */
  private String contentJson;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
