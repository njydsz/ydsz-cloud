package com.njydsz.pmis.userinfo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.userinfo.dto.AttendanceCreateDTO;
import com.njydsz.pmis.userinfo.dto.LeaveCreateDTO;
import com.njydsz.pmis.userinfo.dto.OvertimeCreateDTO;
import com.njydsz.pmis.userinfo.entity.AttendanceDO;
import com.njydsz.pmis.userinfo.entity.LeaveDO;
import com.njydsz.pmis.userinfo.entity.OvertimeDO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 考勤服务
 *
 * <p>覆盖出勤/加班/请假 三类记录。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface AttendanceService {

    // ===== 出勤 =====

    /**
     * 登记出勤 (打卡/手动)
     *
     * @param dto 出勤登记表单
     * @return 出勤记录 ID
     */
    Long recordAttendance(AttendanceCreateDTO dto);

    /**
     * 查询出勤记录
     *
     * @param employeeId 员工 ID
     * @param startDate  开始日期
     * @param endDate    结束日期
     * @param page       页码
     * @param size       每页条数
     * @return 分页结果
     */
    Page<AttendanceDO> pageAttendance(Long employeeId, LocalDate startDate, LocalDate endDate, int page, int size);

    /**
     * 月度出勤统计
     *
     * @param employeeId 员工 ID
     * @param startDate  开始日期
     * @param endDate    结束日期
     * @return 按状态分组的统计结果
     */
    List<Map<String, Object>> statByStatus(Long employeeId, LocalDate startDate, LocalDate endDate);

    // ===== 加班 =====

    /**
     * 提交加班申请
     *
     * @param dto 加班申请表单
     * @return 加班记录 ID
     */
    Long submitOvertime(OvertimeCreateDTO dto);

    /**
     * 审批加班
     *
     * @param id          加班记录 ID
     * @param action      审批动作：APPROVED/REJECTED
     * @param approverId  审批人 ID
     * @param approverName 审批人姓名
     * @param remark      审批意见
     */
    void approveOvertime(Long id, String action, String approverId, String approverName, String remark);

    /**
     * 加班分页
     *
     * @param employeeId     员工 ID
     * @param approvalStatus 审批状态
     * @param page           页码
     * @param size           每页条数
     * @return 分页结果
     */
    Page<OvertimeDO> pageOvertime(Long employeeId, String approvalStatus, int page, int size);

    /**
     * 加班详情
     *
     * @param id 加班记录 ID
     * @return 加班实体，不存在时返回 null
     */
    OvertimeDO getOvertime(Long id);

    // ===== 请假 =====

    /**
     * 提交请假
     *
     * @param dto 请假申请表单
     * @return 请假记录 ID
     */
    Long submitLeave(LeaveCreateDTO dto);

    /**
     * 审批请假
     *
     * @param id          请假记录 ID
     * @param action      审批动作：APPROVED/REJECTED
     * @param approverId  审批人 ID
     * @param approverName 审批人姓名
     * @param remark      审批意见
     */
    void approveLeave(Long id, String action, String approverId, String approverName, String remark);

    /**
     * 请假分页
     *
     * @param employeeId     员工 ID
     * @param approvalStatus 审批状态
     * @param page           页码
     * @param size           每页条数
     * @return 分页结果
     */
    Page<LeaveDO> pageLeave(Long employeeId, String approvalStatus, int page, int size);

    /**
     * 请假详情
     *
     * @param id 请假记录 ID
     * @return 请假实体，不存在时返回 null
     */
    LeaveDO getLeave(Long id);

    /**
     * 员工在指定日期范围内已批准的请假 (供出勤模块关联判断)
     *
     * @param employeeId 员工 ID
     * @param startDate  开始日期
     * @param endDate    结束日期
     * @return 已批准请假列表
     */
    List<LeaveDO> listApprovedLeaves(Long employeeId, LocalDate startDate, LocalDate endDate);
}
