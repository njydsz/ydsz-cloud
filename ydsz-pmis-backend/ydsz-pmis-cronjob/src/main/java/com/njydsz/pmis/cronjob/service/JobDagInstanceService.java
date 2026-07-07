package com.njydsz.pmis.cronjob.service;

import com.njydsz.pmis.cronjob.entity.JobDagInstanceDO;
import com.njydsz.pmis.cronjob.entity.JobDagNodeInstanceDO;

import java.util.List;

/**
 * DAG 工作流实例服务接口（P2 DAG 增强）。
 *
 * <p>负责 DAG 实例的查询、状态流转（暂停/恢复/取消）及上下文管理。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface JobDagInstanceService {

    /**
     * 查询 DAG 实例详情。
     *
     * @param instanceId 实例 ID
     * @return DAG 实例
     * @throws com.njydsz.pmis.common.exception.BizException 当实例不存在时抛出
     */
    JobDagInstanceDO getInstanceById(String instanceId);

    /**
     * 查询指定 DAG 的实例列表（按创建时间倒序）。
     *
     * @param dagId DAG 定义 ID
     * @param limit 最多返回条数
     * @return DAG 实例列表
     */
    List<JobDagInstanceDO> listByDagId(String dagId, int limit);

    /**
     * 按状态查询 DAG 实例。
     *
     * @param status 实例状态
     * @return DAG 实例列表
     */
    List<JobDagInstanceDO> listByStatus(String status);

    /**
     * 查询 DAG 实例的节点列表。
     *
     * @param dagInstanceId DAG 实例 ID
     * @return 节点实例列表
     */
    List<JobDagNodeInstanceDO> listNodes(String dagInstanceId);

    /**
     * 暂停 DAG 实例（RUNNING → PAUSED）。
     *
     * @param instanceId 实例 ID
     * @throws com.njydsz.pmis.common.exception.BizException 当实例不存在或状态非 RUNNING 时抛出
     */
    void pauseInstance(String instanceId);

    /**
     * 恢复 DAG 实例（PAUSED → RUNNING）。
     *
     * @param instanceId 实例 ID
     * @throws com.njydsz.pmis.common.exception.BizException 当实例不存在或状态非 PAUSED 时抛出
     */
    void resumeInstance(String instanceId);

    /**
     * 取消 DAG 实例（RUNNING/PAUSED → CANCELED）。
     *
     * @param instanceId 实例 ID
     * @throws com.njydsz.pmis.common.exception.BizException 当实例不存在或已为终态时抛出
     */
    void cancelInstance(String instanceId);

    /**
     * 更新 DAG 实例上下文 JSON（跨节点传参用）。
     *
     * @param instanceId  实例 ID
     * @param contextJson 上下文 JSON
     * @throws com.njydsz.pmis.common.exception.BizException 当实例不存在时抛出
     */
    void updateContext(String instanceId, String contextJson);
}
