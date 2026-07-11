package com.njydsz.pmis.cronjob.domain.entity.job;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 任务依赖关系实体（pmis_job_relation 表，P4 DAG 工作流）。
 *
 * <p>表示 {@code parent_job → child_job} 的依赖边。当 parent_job 执行成功后，
 * 根据 {@link #failStrategy} 决定是否触发 child_job。
 *
 * <p>多条边组成 DAG（有向无环图），由 {@code DagParser} 负责解析与环检测。
 *
 * @deprecated P3-2-merge: 推荐使用 {@code pmis_job_dag} 表的 DAG 定义（JSON 格式）
 * 管理任务间依赖关系。DAG 定义支持更丰富的工作流特性。
 * 本实体保留向后兼容，新功能应使用 DAG 体系。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Deprecated
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_job_relation")
public class JobRelationDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 前置任务 ID（执行成功后触发后继） */
    private String parentJobId;

    /** 后继任务 ID（被前置任务触发） */
    private String childJobId;

    /**
     * 失败传播策略（P4-3）：FAIL_FAST / CONTINUE_ON_FAIL。
     *
     * <p>FAIL_FAST：前置任务失败时不触发后继任务（默认）。
     * <p>CONTINUE_ON_FAIL：前置任务失败时仍触发后继任务（适用于通知类后继）。
     */
    private String failStrategy;
}
