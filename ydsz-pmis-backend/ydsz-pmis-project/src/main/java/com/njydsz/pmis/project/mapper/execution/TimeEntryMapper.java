package com.njydsz.pmis.project.mapper.execution;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.execution.TimeEntryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 工时录入 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface TimeEntryMapper extends BaseMapper<TimeEntryDO> {

    /**
     * 更新工时状态
     *
     * @param id           工时 ID
     * @param status       目标状态
     * @param approverId   审批人 ID
     * @param approverName 审批人姓名
     * @param rejectReason 驳回原因
     * @return 受影响行数
     */
    int updateStatus(@Param("id") String id, @Param("status") String status,
                     @Param("approverId") String approverId, @Param("approverName") String approverName,
                     @Param("rejectReason") String rejectReason);

    /**
     * 按员工 + 日期范围查询工时列表
     *
     * @param employeeId 员工 ID
     * @param from       起始日期
     * @param to         截止日期
     * @return 工时列表
     */
    List<TimeEntryDO> selectByEmployeeAndDateRange(@Param("employeeId") String employeeId,
                                                    @Param("from") LocalDate from,
                                                    @Param("to") LocalDate to);

    /**
     * 按立项 + 日期范围查询工时列表
     *
     * @param initiationId 立项 ID
     * @param from         起始日期
     * @param to           截止日期
     * @return 工时列表
     */
    List<TimeEntryDO> selectByInitiationAndDateRange(@Param("initiationId") String initiationId,
                                                     @Param("from") LocalDate from,
                                                     @Param("to") LocalDate to);

    /**
     * 按 WBS 任务 ID 查询工时列表
     *
     * @param taskId 任务 ID
     * @return 工时列表
     */
    List<TimeEntryDO> selectByTask(@Param("taskId") String taskId);

    /**
     * 按状态查询工时列表
     *
     * @param status 工时状态
     * @return 工时列表
     */
    List<TimeEntryDO> selectByStatus(@Param("status") String status);

    /**
     * 聚合工时（按人员+职级）
     *
     * @param initiationId 立项 ID
     * @param from         起始日期
     * @param to           截止日期
     * @return 工时聚合列表
     */
    List<Map<String, Object>> aggregateHoursByEmployeeAndLevel(@Param("initiationId") String initiationId,
                                                               @Param("from") LocalDate from,
                                                               @Param("to") LocalDate to);

    /**
     * 按天聚合作业时长，用于异常校验（连续 3 天 0 填报）
     *
     * @param employeeId 员工 ID
     * @param from       起始日期
     * @param to         截止日期
     * @return 按天聚合列表
     */
    List<Map<String, Object>> aggregateByDay(@Param("employeeId") String employeeId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    /**
     * 跨项目冲突检测：员工同一天在多个项目填写工时
     *
     * @param employeeId 员工 ID
     * @param entryDate  填报日期
     * @return 冲突检测结果列表
     */
    List<Map<String, Object>> detectCrossProject(@Param("employeeId") String employeeId,
                                                 @Param("entryDate") LocalDate entryDate);

    /**
     * P4-1 可计费利用率：按员工 × 月份聚合 billable / total / overtime / leave
     *
     * @param from 起始日期
     * @param to   截止日期
     * @return 员工可计费利用率聚合列表
     */
    List<Map<String, Object>> aggregateBillableByEmployee(@Param("from") LocalDate from,
                                                          @Param("to") LocalDate to);

    /**
     * P4-1 单员工可计费利用率
     *
     * @param employeeId 员工 ID
     * @param from       起始日期
     * @param to         截止日期
     * @return 单员工可计费利用率数据
     */
    Map<String, Object> aggregateBillableOne(@Param("employeeId") String employeeId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    /**
     * P4-3 已审批工时总小时数
     *
     * @return 已审批工时总小时数
     */
    BigDecimal sumApprovedHours();
}
