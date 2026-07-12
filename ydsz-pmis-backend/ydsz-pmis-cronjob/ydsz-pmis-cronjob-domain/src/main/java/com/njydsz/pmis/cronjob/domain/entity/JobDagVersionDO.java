package com.njydsz.pmis.cronjob.domain.entity.dag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * DAG 工作流版本历史实体（P1-8 工作流版本管理）。
 *
 * <p>对应 {@code pmis_job_dag_version} 表，存储 DAG 定义的每次变更快照。
 * 创建 DAG 时保存 V1，每次更新 DAG 时保存新版本快照。
 * 支持查看版本历史、对比差异、回滚到指定版本。
 *
 * <h3>版本策略</h3>
 * <ul>
 *   <li>创建 DAG → 保存 V1 快照</li>
 *   <li>更新 DAG → 保存新版本快照（version 递增）</li>
 *   <li>回滚到 V_N → 将 V_N 的 dagDefinition 复制到当前 DAG，并创建 V_{N+1} 快照</li>
 *   <li>版本号全局递增，回滚不会重用旧版本号</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_job_dag_version")
public class JobDagVersionDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** DAG ID（关联 pmis_job_dag.id） */
    private String dagId;

    /** DAG KEY（冗余字段，便于查询） */
    private String dagKey;

    /** 版本号（从 1 递增） */
    private Integer version;

    /** DAG 定义 JSON 快照 */
    private String dagDefinition;

    /** DAG 名称快照 */
    private String dagName;

    /** 触发类型快照 */
    private String triggerType;

    /** Cron 表达式快照 */
    private String cronExpression;

    /** 失败策略快照 */
    private String failStrategy;

    /** 版本备注（如"新增节点A"、"修改条件分支"） */
    private String remark;

    /** 变更操作人 */
    private String changedBy;
}
