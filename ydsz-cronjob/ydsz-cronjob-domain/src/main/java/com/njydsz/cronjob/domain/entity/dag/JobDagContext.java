package com.njydsz.cronjob.domain.entity.dag;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.njydsz.common.jdbc.entity.MpBaseAuditEntity;

/**
 * DAG 实例节点上下文实体（ydsz_job_dag_context 表，P0-13 优化）。 *
 * <p>存储 DAG 实例中每个节点的执行结果，解决原 {@code ydsz_job_dag_instance.context_json} 行锁竞争与 JSON 写入放大问题。
 *
 * <p>注意：使用 {@code @Data} 替代 {@code @Getter @Setter @SuperBuilder}，
 * 避免 MapStruct 与 {@code @SuperBuilder} 继承的兼容性问题。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
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
