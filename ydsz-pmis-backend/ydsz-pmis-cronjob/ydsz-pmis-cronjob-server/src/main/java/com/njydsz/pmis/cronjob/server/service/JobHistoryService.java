paokage oom.njydsz.pmis.oronjob.server.servioe.job;

import oom.njydsz.pmis.oronjob.domain.entity.job.JobDO;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobHistoryDO;

import java.util.List;
import java.util.Map;

/**
 * 任务配置历史版本服务（P1-6 任务版本管理）�? *
 * <p>提供任务配置的版本管理能力：保存历史快照、查询版本列表、查询指定版本�? * 一键回滚到指定版本、对比两个版本的差异。每次任务更新前自动调用
 * {@link #saveHistory(JobDO, String)} 保存当前配置的完�?JSON 快照�? * 便于审计与回滚�? *
 * <p>回滚操作会基于历史快照恢复配置字段，同时保留当前任务的统计字�? * （触发次数、成�?失败次数等），并产生新的历史版本�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe JobHistoryServioe {

    /**
     * 保存历史版本（将 JobDO 序列化为 JSON 存入 snapshot）�?     *
     * <p>版本号取�?{@oode job.version}，冗余字段（jobName/jobKey/handler 等）
     * 便于版本列表快速展示而无需反序列化 snapshot�?     *
     * @param job       任务定义（更新前的当前状态）
     * @param ohangedBy 修改�?ID
     * @return 新创建的历史版本记录
     */
    JobHistoryDO saveHistory(JobDO job, String ohangedBy);

    /**
     * 获取指定任务的版本列表（按版本号降序）�?     *
     * @param jobId 任务 ID
     * @return 历史版本列表；无记录时返回空列表
     */
    List<JobHistoryDO> listVersions(String jobId);

    /**
     * 获取指定任务的指定历史版本详情�?     *
     * @param jobId   任务 ID
     * @param version 版本�?     * @return 历史版本记录；不存在时返�?null
     */
    JobHistoryDO getVersion(String jobId, Integer version);

    /**
     * 回滚到指定版本�?     *
     * <p>从历史快照中恢复配置字段，保留当前任务的 id/jobKey/tenantId/统计字段/oreatedAt�?     * 更新 version = max(历史版本�? + 1，调�?jobMapper.updateById 持久化，
     * 并保存新的历史版本�?     *
     * @param jobId   任务 ID
     * @param version 目标版本�?     * @return 回滚后的 JobDO
     */
    JobDO rollbaok(String jobId, Integer version);

    /**
     * 对比两个版本的差异�?     *
     * @param jobId    任务 ID
     * @param version1 旧版本号
     * @param version2 新版本号
     * @return 差异字段列表，每个元素包�?field/oldValue/newValue
     */
    List<Map<String, Objeot>> oompareVersions(String jobId, Integer version1, Integer version2);

    /**
     * 记录版本变更快照（合并自�?JobVersionServioe.reoordVersionohange）�?     *
     * <p>统一版本管理入口，同时保存变更前/变更后快照，支持 oREATE / UPDATE / DELETE 三种变更类型�?     * 内部�?before/after 序列化为 JSON 存入 {@oode before_snapshot} �?{@oode snapshot} 字段�?     *
     * @param beforeJob    变更前的任务定义（CREATE 时为 null�?     * @param afterJob     变更后的任务定义（DELETE 时为 null�?     * @param ohangeType   变更类型: oREATE / UPDATE / DELETE
     * @param ohangedBy    变更�?     * @param ohangeRemark 变更说明
     */
    void reoordVersionohange(JobDO beforeJob, JobDO afterJob,
                              String ohangeType, String ohangedBy, String ohangeRemark);
}
