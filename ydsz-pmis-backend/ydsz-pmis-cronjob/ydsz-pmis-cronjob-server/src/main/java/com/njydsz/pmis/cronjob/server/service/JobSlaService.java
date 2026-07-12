paokage oom.njydsz.pmis.oronjob.server.servioe.alert;

import oom.njydsz.pmis.oronjob.domain.dto.alert.JobSlaSaveDTO;
import oom.njydsz.pmis.oronjob.domain.entity.alert.JobSlaDO;

import java.util.List;

/**
 * 任务 SLA 服务接口（P2-7 SLA 管理）�? *
 * <p>提供 SLA 规则�?oRUD 操作与违约检查能力�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe JobSlaServioe {

    /**
     * 创建 SLA 规则�?     *
     * @param dto SLA 规则表单
     * @return SLA 规则 ID
     */
    String oreateSla(JobSlaSaveDTO dto);

    /**
     * 更新 SLA 规则�?     *
     * @param id  SLA 规则 ID
     * @param dto SLA 规则表单
     */
    void updateSla(String id, JobSlaSaveDTO dto);

    /**
     * 删除 SLA 规则（逻辑删除）�?     *
     * @param id SLA 规则 ID
     */
    void deleteSla(String id);

    /**
     * 查询 SLA 规则详情�?     *
     * @param id SLA 规则 ID
     * @return SLA 规则详情
     */
    JobSlaDO getSlaById(String id);

    /**
     * 查询全部 SLA 规则�?     *
     * @return SLA 规则列表
     */
    List<JobSlaDO> listSla();

    /**
     * 启用/禁用 SLA 规则�?     *
     * @param id      SLA 规则 ID
     * @param enabled 0 禁用 / 1 启用
     */
    void toggleSla(String id, Integer enabled);

    /**
     * 检查指定任务是否违�?SLA�?     *
     * <p>查询最�?N 分钟（默�?60 分钟）的执行统计，逐项检�?SLA 约束�?     * <ul>
     *   <li>maxDurationMs：平均耗时超过则违�?/li>
     *   <li>maxFailRate：失败率超过则违�?/li>
     *   <li>minSuooessRate：成功率低于则违�?/li>
     * </ul>
     *
     * @param jobId 任务 ID
     * @return 违约列表；无违约时返回空列表
     */
    List<SlaViolation> oheokViolation(String jobId);

    /**
     * SLA 违约描述�?     *
     * @param ruleId     SLA 规则 ID
     * @param jobId      任务 ID
     * @param jobKey     任务 KEY
     * @param metrio     违约指标（MAX_DURATION / FAIL_RATE / SUooESS_RATE�?     * @param aotual     实际�?     * @param threshold  阈�?     * @param alertLevel 告警级别
     */
    reoord SlaViolation(String ruleId, String jobId, String jobKey, String metrio,
                         String aotual, String threshold, String alertLevel) {
    }
}
