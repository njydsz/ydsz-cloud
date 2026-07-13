package com.njydsz.pmis.cronjob.server.service.job;

import java.util.List;

import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.cronjob.domain.entity.job.JobRelationDO;

/**
 * 任务依赖关系服务接口（P4 DAG 工作流）。
 *
 * @deprecated P3-2-merge: 推荐使用 DAG 定义服务 ({@code JobDagService}) 管理任务依赖。
 * 本接口保留向后兼容，新功能应使用 DAG 体系。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Deprecated
public interface JobRelationService {

    /**
     * 添加依赖关系（含环检测）。
     *
     * @param parentJobId  前置任务 ID
     * @param childJobId   后继任务 ID
     * @param failStrategy 失败传播策略（null 默认 FAIL_FAST）
     * @return 新建的依赖关系 ID
     * @throws SysException 当任务不存在、自依赖或形成环时抛出
     */
    String addRelation(String parentJobId, String childJobId, String failStrategy);

    /**
     * 删除依赖关系。
     *
     * @param relationId 依赖关系 ID
     * @throws SysException 当依赖关系不存在时抛出
     */
    void removeRelation(String relationId);

    /**
     * 查询指定任务的后继依赖列表。
     *
     * @param parentJobId 前置任务 ID
     * @return 后继依赖边列表
     */
    List<JobRelationDO> getChildren(String parentJobId);

    /**
     * 查询指定任务的前置依赖列表。
     *
     * @param childJobId 后继任务 ID
     * @return 前置依赖边列表
     */
    List<JobRelationDO> getParents(String childJobId);

    /**
     * 查询所有依赖关系（DAG 全图）。
     *
     * @return 所有依赖边列表
     */
    List<JobRelationDO> getAllRelations();
}
