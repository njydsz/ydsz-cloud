package com.njydsz.pmis.cronjob.server.service.dag;

import com.njydsz.pmis.cronjob.domain.dto.dag.JobDagSaveDTO;
import com.njydsz.pmis.cronjob.domain.entity.dag.JobDagDO;
import com.njydsz.pmis.cronjob.domain.entity.dag.JobDagVersionDO;

import java.util.List;

/**
 * DAG 工作流定义服务接口（P2 DAG 增强）。
 *
 * <p>负责 DAG 定义的增删改查、状态流转（启用/禁用）以及手动触发。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface JobDagService {

    /**
     * 创建 DAG 定义。
     *
     * @param dto DAG 表单
     * @return 新建的 DAG ID
     * @throws com.njydsz.pmis.common.exception.BizException 当 dagKey 已存在或 dagDefinition 非法时抛出
     */
    String createDag(JobDagSaveDTO dto);

    /**
     * 更新 DAG 定义。
     *
     * @param dagId DAG ID
     * @param dto   DAG 表单
     * @throws com.njydsz.pmis.common.exception.BizException 当 DAG 不存在或 dagDefinition 非法时抛出
     */
    void updateDag(String dagId, JobDagSaveDTO dto);

    /**
     * 删除 DAG 定义（逻辑删除）。
     *
     * @param dagId DAG ID
     * @throws com.njydsz.pmis.common.exception.BizException 当 DAG 不存在时抛出
     */
    void deleteDag(String dagId);

    /**
     * 启用 DAG（DRAFT/DISABLED → ENABLED）。
     *
     * @param dagId DAG ID
     * @throws com.njydsz.pmis.common.exception.BizException 当 DAG 不存在或状态非法时抛出
     */
    void enableDag(String dagId);

    /**
     * 禁用 DAG（ENABLED → DISABLED）。
     *
     * @param dagId DAG ID
     * @throws com.njydsz.pmis.common.exception.BizException 当 DAG 不存在或状态非法时抛出
     */
    void disableDag(String dagId);

    /**
     * 查询 DAG 定义。
     *
     * @param dagId DAG ID
     * @return DAG 定义
     * @throws com.njydsz.pmis.common.exception.BizException 当 DAG 不存在时抛出
     */
    JobDagDO getDagById(String dagId);

    /**
     * 根据 KEY 查询 DAG 定义。
     *
     * @param dagKey DAG KEY
     * @return DAG 定义
     * @throws com.njydsz.pmis.common.exception.BizException 当 DAG 不存在时抛出
     */
    JobDagDO getDagByKey(String dagKey);

    /**
     * 查询所有启用的 DAG。
     *
     * @return 启用状态的 DAG 列表
     */
    List<JobDagDO> listEnabledDags();

    /**
     * 查询所有 CRON 触发的启用 DAG（调度器扫描用）。
     *
     * @return CRON 启用状态的 DAG 列表
     */
    List<JobDagDO> listCronEnabledDags();

    /**
     * 手动触发 DAG 执行。
     *
     * @param dagKey    DAG KEY
     * @param triggerBy 触发人（用户 ID，可空）
     * @return DAG 实例 ID
     * @throws com.njydsz.pmis.common.exception.BizException 当 DAG 不存在、未启用或并发已达上限时抛出
     */
    String triggerDag(String dagKey, String triggerBy);

    // ==================== P1-8: 工作流版本管理 ====================

    /**
     * P1-8: 查询 DAG 版本历史。
     *
     * @param dagId DAG ID
     * @param limit 最多返回条数（默认 50）
     * @return 版本历史列表（按版本号倒序）
     */
    List<JobDagVersionDO> listDagVersions(String dagId, int limit);

    /**
     * P1-8: 查询指定版本的 DAG 快照。
     *
     * @param dagId   DAG ID
     * @param version 版本号
     * @return 版本快照
     * @throws com.njydsz.pmis.common.exception.BizException 当版本不存在时抛出
     */
    JobDagVersionDO getDagVersion(String dagId, int version);

    /**
     * P1-8: 回滚 DAG 到指定版本。
     *
     * <p>将指定版本的 dagDefinition 复制到当前 DAG 定义，并创建新的版本快照。
     * 回滚本身也是一个新版本，版本号继续递增（不重用旧版本号）。
     *
     * @param dagId    DAG ID
     * @param version  要回滚到的版本号
     * @param changedBy 操作人
     * @return 回滚后的新版本号
     * @throws com.njydsz.pmis.common.exception.BizException 当 DAG 或版本不存在时抛出
     */
    int rollbackDagVersion(String dagId, int version, String changedBy);
}
