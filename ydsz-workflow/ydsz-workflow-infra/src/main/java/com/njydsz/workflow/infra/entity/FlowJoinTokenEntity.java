package com.njydsz.workflow.infra.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 并行网关 join token 实体（Infra 层）
 *
 * <p>对应数据库表 {@code ydsz_flow_join_token}，持久化并行网关的分支到达状态。
 *
 * <p><b>生命周期：</b>
 *
 * <ol>
 *   <li>并行网关 fork 时 INSERT 一条 PENDING 记录（total_branches = 出边数）</li>
 *   <li>每个分支到达时原子递增 arrived_count</li>
 *   <li>arrived_count >= required_branches 时标记 COMPLETED</li>
 *   <li>实例终止时标记 CANCELLED</li>
 * </ol>
 *
 * <p><b>状态机：</b>
 *
 * <pre>
 * INIT → PENDING → COMPLETED
 *              ↘ CANCELLED
 * </pre>
 *
 * @author ydsz-team
 * @since 26.10.08
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_join_token")
public class FlowJoinTokenEntity extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 流程实例 ID */
  private String instanceId;

  /** join 节点编码（并行/包容网关的 node_code） */
  private String joinNodeCode;

  /** 总分支数（应等于 join 节点的入边数） */
  private Integer totalBranches;

  /** 聚合所需到达数（0 表示全部分支，即语义同 totalBranches） */
  private Integer requiredBranches;

  /** 已到达分支计数（原子递增） */
  private Integer arrivedCount;

  /** 聚合状态：PENDING=聚合中 / COMPLETED=已完成 / CANCELLED=已取消 */
  private String joinStatus;

  /**
   * 判断 join 是否已完成聚合（到达数 >= 所需数）。
   *
   * <p>requiredBranches=0 时退化为全部分支到达语义（arrivedCount >= totalBranches）。
   *
   * @return true=已满足聚合条件
   */
  public boolean isComplete() {
    if (arrivedCount == null || totalBranches == null) {
      return false;
    }
    int required = (requiredBranches != null && requiredBranches > 0) ? requiredBranches : totalBranches;
    return arrivedCount >= required;
  }

  /**
   * 判断 join token 是否仍在聚合中。
   *
   * @return true=仍在聚合中（PENDING 状态且未满足聚合条件）
   */
  public boolean isPending() {
    return "PENDING".equals(joinStatus);
  }
}
