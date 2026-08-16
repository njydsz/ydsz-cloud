package com.njydsz.cronjob.server.service.dag;

import java.util.List;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.cronjob.domain.dto.dag.JobDagSaveDTO;
import com.njydsz.cronjob.domain.entity.dag.JobDag;
import com.njydsz.cronjob.domain.entity.dag.JobDagVersion;

/**
 * DAG 工作流定义 Service
 *
 * <p>负责 DAG(有向无环图)工作流定义的完整生命周期：CRUD、状态流转、版本管理、手动触发。
 * DAG 用于表达"多个任务按依赖关系编排执行"的需求(任务 A 完成后才能执行任务 B,C 失败时跳过 D),
 * 比单任务更适合复杂的业务编排场景。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #createDag} / {@link #updateDag} / {@link #deleteDag}</li>
 *   <li><b>状态流转</b>：{@link #enableDag} / {@link #disableDag} — {@code DRAFT/DISABLED ↔ ENABLED}</li>
 *   <li><b>查询</b>：{@link #getDagById} / {@link #getDagByKey} / {@link #listEnabledDags} / {@link #listCronEnabledDags}</li>
 *   <li><b>手动触发</b>：{@link #triggerDag} — 立即执行并返回实例 ID</li>
 *   <li><b>版本管理(P1-8)</b>：{@link #listDagVersions} / {@link #getDagVersion} / {@link #rollbackDagVersion}</li>
 * </ul>
 *
 * <p><b>DAG 状态机：</b>{@code DRAFT → ENABLED ↔ DISABLED →(删除)→ DELETED(逻辑删除)}。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * <p><b>关联模块：</b>DAG 节点引用 {@link JobService} 管理的任务,通过 {@code dagDefinition.nodes[].jobId} 关联。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.cronjob.domain.entity.dag.JobDag DAG 定义实体
 * @see com.njydsz.cronjob.domain.entity.dag.JobDagVersion DAG 版本实体
 * @see JobDagInstanceService DAG 实例 Service
 */
public interface JobDagService {

    /**
     * 创建 DAG 定义。
     *
     * @param dto DAG 表单
     * @return 新建的 DAG ID
     * @throws SysException 当 dagKey 已存在或 dagDefinition 非法时抛出
     */
    String createDag(JobDagSaveDTO dto);

    /**
     * 更新 DAG 定义。
     *
     * @param dagId DAG ID
     * @param dto   DAG 表单
     * @throws SysException 当 DAG 不存在或 dagDefinition 非法时抛出
     */
    void updateDag(String dagId, JobDagSaveDTO dto);

    /**
     * 删除 DAG 定义（逻辑删除）。
     *
     * @param dagId DAG ID
     * @throws SysException 当 DAG 不存在时抛出
     */
    void deleteDag(String dagId);

    /**
     * 启用 DAG（DRAFT/DISABLED → ENABLED）。
     *
     * @param dagId DAG ID
     * @throws SysException 当 DAG 不存在或状态非法时抛出
     */
    void enableDag(String dagId);

    /**
     * 禁用 DAG（ENABLED → DISABLED）。
     *
     * @param dagId DAG ID
     * @throws SysException 当 DAG 不存在或状态非法时抛出
     */
    void disableDag(String dagId);

    /**
     * 查询 DAG 定义。
     *
     * @param dagId DAG ID
     * @return DAG 定义
     * @throws SysException 当 DAG 不存在时抛出
     */
    JobDag getDagById(String dagId);

    /**
     * 根据 KEY 查询 DAG 定义。
     *
     * @param dagKey DAG KEY
     * @return DAG 定义
     * @throws SysException 当 DAG 不存在时抛出
     */
    JobDag getDagByKey(String dagKey);

    /**
     * 查询所有启用的 DAG。
     *
     * @return 启用状态的 DAG 列表
     */
    List<JobDag> listEnabledDags();

    /**
     * 查询所有 CRON 触发的启用 DAG（调度器扫描用）。
     *
     * @return CRON 启用状态的 DAG 列表
     */
    List<JobDag> listCronEnabledDags();

    /**
     * 手动触发 DAG 执行。
     *
     * @param dagKey    DAG KEY
     * @param triggerBy 触发人（用户 ID，可空）
     * @return DAG 实例 ID
     * @throws SysException 当 DAG 不存在、未启用或并发已达上限时抛出
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
    List<JobDagVersion> listDagVersions(String dagId, int limit);

    /**
     * P1-8: 查询指定版本的 DAG 快照。
     *
     * @param dagId   DAG ID
     * @param version 版本号
     * @return 版本快照
     * @throws SysException 当版本不存在时抛出
     */
    JobDagVersion getDagVersion(String dagId, int version);

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
     * @throws SysException 当 DAG 或版本不存在时抛出
     */
    int rollbackDagVersion(String dagId, int version, String changedBy);
}
