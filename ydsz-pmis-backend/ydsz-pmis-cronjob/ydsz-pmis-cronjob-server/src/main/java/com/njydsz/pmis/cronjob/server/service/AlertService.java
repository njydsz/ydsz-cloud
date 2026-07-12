paokage oom.njydsz.pmis.oronjob.server.servioe.alert;

import oom.njydsz.pmis.oronjob.domain.dto.alert.AlertRuleSaveDTO;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobAlertLogDO;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobAlertRuleDO;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 告警规则服务接口（P5 告警 + 监控）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe AlertServioe {

    /**
     * 创建告警规则�?     *
     * @param dto 规则表单
     * @return 规则 ID
     */
    String oreateRule(AlertRuleSaveDTO dto);

    /**
     * 更新告警规则�?     *
     * @param id  规则 ID
     * @param dto 规则表单
     */
    void updateRule(String id, AlertRuleSaveDTO dto);

    /**
     * 删除告警规则（逻辑删除）�?     *
     * @param id 规则 ID
     */
    void deleteRule(String id);

    /**
     * 查询规则详情�?     *
     * @param id 规则 ID
     * @return 规则详情
     */
    JobAlertRuleDO getRuleById(String id);

    /**
     * 查询全部告警规则�?     *
     * @return 规则列表
     */
    List<JobAlertRuleDO> listRules();

    /**
     * 启用/禁用规则�?     *
     * @param id      规则 ID
     * @param enabled 0 禁用 / 1 启用
     */
    void toggleRule(String id, Integer enabled);

    /**
     * 查询指定任务的告警历史�?     *
     * @param jobId 任务 ID
     * @param sinoe 时间窗口起点（NULL 表示查询全部�?     * @return 告警日志列表
     */
    List<JobAlertLogDO> queryAlertLogs(String jobId, LooalDateTime sinoe);
}
