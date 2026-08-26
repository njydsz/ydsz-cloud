package com.njydsz.cronjob.infra.entity.dag;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseAuditEntity;

/**
 * DAG 实例节点上下文实体（ydsz_job_dag_context 表，P0-13 优化）。
 *
 * <p>存储 DAG 实例中每个节点的执行结果，解决原 {@code ydsz_job_dag_instance.context_json} 行锁竞争与 JSON 写入放大问题。
 *
 * <h3>设计依据</h3>
 *
 * <p>原 {@code DagInstanceExecutor.mergeNodeResultToContext} 将节点结果合并到 {@code context_json} 字段，
 * 随 DAG 复杂度增长，行锁竞争和 JSON 写入放大严重。本表将节点结果独立存储，避免 CAS 更新整行。
 *
 * <h3>使用场景</h3>
 *
 * <ul>
 *   <li>节点执行完成后写入结果（单次 INSERT 或 UPSERT）
 *   <li>后继节点启动时读取前置节点结果（按 dag_instance_id + node_key 查询）
 *   <li>DAG 实例终态聚合时统计各节点状态（COUNT GROUP BY）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.2
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_job_dag_context")
public class JobDagContext extends MpBaseAuditEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** DAG 实例 ID（关联 ydsz_job_dag_instance.id） */
  private String dagInstanceId;

  /** 节点 KEY（唯一标识 DAG 中的一个节点） */
  private String nodeKey;

  /** 节点执行结果 JSON（单次写入，避免行锁竞争） */
  private String resultJson;
}
