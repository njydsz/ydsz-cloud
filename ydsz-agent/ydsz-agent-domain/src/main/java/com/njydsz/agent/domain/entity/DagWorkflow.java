package com.njydsz.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * DAG 工作流持久化实体（存储 YAML DSL 供后续加载/编辑/执行）。
 *
 * <p>支持工作流模板的 CRUD 生命周期，用户可通过可视化编辑器编排后保存为命名工作流，
 * 并在需要时加载、修改、重新执行。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Data
@TableName("ydsz_agt_dag_workflow")
public class DagWorkflow extends MpBaseEntity<String> {

  /** 工作流编码（业务唯一 key） */
  private String workflowCode;

  /** 工作流名称 */
  private String workflowName;

  /** 工作流描述 */
  private String description;

  /** YAML DSL 内容 */
  private String dslContent;

  /** 可视化布局 JSON（节点坐标等前端状态） */
  private String layoutJson;

  /** 分类标签 */
  private String category;

  /** 是否发布 */
  private Boolean isPublished;
}
