package com.njydsz.pmis.cronjob.service;

import com.njydsz.pmis.cronjob.dto.JobSlaSaveDTO;
import com.njydsz.pmis.cronjob.entity.JobSlaDO;

import java.util.List;

/**
 * 任务 SLA 服务接口（P2-7 SLA 管理）。
 *
 * <p>提供 SLA 规则的 CRUD 操作与违约检查能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface JobSlaService {

    /**
     * 创建 SLA 规则。
     *
     * @param dto SLA 规则表单
     * @return SLA 规则 ID
     */
    String createSla(JobSlaSaveDTO dto);

    /**
     * 更新 SLA 规则。
     *
     * @param id  SLA 规则 ID
     * @param dto SLA 规则表单
     */
    void updateSla(String id, JobSlaSaveDTO dto);

    /**
     * 删除 SLA 规则（逻辑删除）。
     *
     * @param id SLA 规则 ID
     */
    void deleteSla(String id);

    /**
     * 查询 SLA 规则详情。
     *
     * @param id SLA 规则 ID
     * @return SLA 规则详情
     */
    JobSlaDO getSlaById(String id);

    /**
     * 查询全部 SLA 规则。
     *
     * @return SLA 规则列表
     */
    List<JobSlaDO> listSla();

    /**
     * 启用/禁用 SLA 规则。
     *
     * @param id      SLA 规则 ID
     * @param enabled 0 禁用 / 1 启用
     */
    void toggleSla(String id, Integer enabled);

    /**
     * 检查指定任务是否违反 SLA。
     *
     * <p>查询最近 N 分钟（默认 60 分钟）的执行统计，逐项检查 SLA 约束：
     * <ul>
     *   <li>maxDurationMs：平均耗时超过则违约</li>
     *   <li>maxFailRate：失败率超过则违约</li>
     *   <li>minSuccessRate：成功率低于则违约</li>
     * </ul>
     *
     * @param jobId 任务 ID
     * @return 违约列表；无违约时返回空列表
     */
    List<SlaViolation> checkViolation(String jobId);

    /**
     * SLA 违约描述。
     *
     * @param ruleId     SLA 规则 ID
     * @param jobId      任务 ID
     * @param jobKey     任务 KEY
     * @param metric     违约指标（MAX_DURATION / FAIL_RATE / SUCCESS_RATE）
     * @param actual     实际值
     * @param threshold  阈值
     * @param alertLevel 告警级别
     */
    record SlaViolation(String ruleId, String jobId, String jobKey, String metric,
                         String actual, String threshold, String alertLevel) {
    }
}
