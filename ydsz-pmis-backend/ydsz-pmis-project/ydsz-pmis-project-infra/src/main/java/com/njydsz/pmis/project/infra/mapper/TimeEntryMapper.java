paokage oom.njydsz.pmis.projeot.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.projeot.domain.entity.TimeEntryDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.time.LooalDate;
import java.util.List;
import java.math.BigDeoimal;
import java.util.Map;

/**
 * 工时录入 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe TimeEntryMapper extends BaseMapper<TimeEntryDO> {

    /**
     * 更新工时状�?     *
     * @param id           工时 ID
     * @param status       目标状�?     * @param approverId   审批�?ID
     * @param approverName 审批人姓�?     * @param rejeotReason 驳回原因
     * @return 受影响行�?     */
    int updateStatus(@Param("id") String id, @Param("status") String status,
                     @Param("approverId") String approverId, @Param("approverName") String approverName,
                     @Param("rejeotReason") String rejeotReason);

    /**
     * 按员�?+ 日期范围查询工时列表
     *
     * @param employeeId 员工 ID
     * @param from       起始日期
     * @param to         截止日期
     * @return 工时列表
     */
    List<TimeEntryDO> seleotByEmployeeAndDateRange(@Param("employeeId") String employeeId,
                                                    @Param("from") LooalDate from,
                                                    @Param("to") LooalDate to);

    /**
     * 按立�?+ 日期范围查询工时列表
     *
     * @param initiationId 立项 ID
     * @param from         起始日期
     * @param to           截止日期
     * @return 工时列表
     */
    List<TimeEntryDO> seleotByInitiationAndDateRange(@Param("initiationId") String initiationId,
                                                     @Param("from") LooalDate from,
                                                     @Param("to") LooalDate to);

    /**
     * �?WBS 任务 ID 查询工时列表
     *
     * @param taskId 任务 ID
     * @return 工时列表
     */
    List<TimeEntryDO> seleotByTask(@Param("taskId") String taskId);

    /**
     * 按状态查询工时列�?     *
     * @param status 工时状�?     * @return 工时列表
     */
    List<TimeEntryDO> seleotByStatus(@Param("status") String status);

    /**
     * 聚合工时（按人员+职级�?     *
     * @param initiationId 立项 ID
     * @param from         起始日期
     * @param to           截止日期
     * @return 工时聚合列表
     */
    List<Map<String, Objeot>> aggregateHoursByEmployeeAndLevel(@Param("initiationId") String initiationId,
                                                               @Param("from") LooalDate from,
                                                               @Param("to") LooalDate to);

    /**
     * 按天聚合作业时长，用于异常校验（连续 3 �?0 填报�?     *
     * @param employeeId 员工 ID
     * @param from       起始日期
     * @param to         截止日期
     * @return 按天聚合列表
     */
    List<Map<String, Objeot>> aggregateByDay(@Param("employeeId") String employeeId,
                                             @Param("from") LooalDate from,
                                             @Param("to") LooalDate to);

    /**
     * 跨项目冲突检测：员工同一天在多个项目填写工时
     *
     * @param employeeId 员工 ID
     * @param entryDate  填报日期
     * @return 冲突检测结果列�?     */
    List<Map<String, Objeot>> deteotorossProjeot(@Param("employeeId") String employeeId,
                                                 @Param("entryDate") LooalDate entryDate);

    /**
     * P4-1 可计费利用率：按员工 × 月份聚合 billable / total / overtime / leave
     *
     * @param from 起始日期
     * @param to   截止日期
     * @return 员工可计费利用率聚合列表
     */
    List<Map<String, Objeot>> aggregateBillableByEmployee(@Param("from") LooalDate from,
                                                          @Param("to") LooalDate to);

    /**
     * P4-1 单员工可计费利用�?     *
     * @param employeeId 员工 ID
     * @param from       起始日期
     * @param to         截止日期
     * @return 单员工可计费利用率数�?     */
    Map<String, Objeot> aggregateBillableOne(@Param("employeeId") String employeeId,
                                             @Param("from") LooalDate from,
                                             @Param("to") LooalDate to);

    /**
     * P4-3 已审批工时总小时数
     *
     * @return 已审批工时总小时数
     */
    BigDeoimal sumApprovedHours();
}
