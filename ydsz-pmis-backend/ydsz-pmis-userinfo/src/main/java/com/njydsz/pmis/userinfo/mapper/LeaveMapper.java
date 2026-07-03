package com.njydsz.pmis.userinfo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.userinfo.entity.LeaveDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 请假申请 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface LeaveMapper extends BaseMapper<LeaveDO> {

    LeaveDO selectByCode(@Param("leaveCode") String leaveCode);

    List<LeaveDO> selectByEmployee(@Param("employeeId") Long employeeId);

    List<LeaveDO> selectByStatus(@Param("approvalStatus") String approvalStatus);

    /**
     * 查员工在指定日期区间内已批准的请假
     */
    List<LeaveDO> selectApprovedByEmployeeAndRange(@Param("employeeId") Long employeeId,
                                                   @Param("startDate") String startDate,
                                                   @Param("endDate") String endDate);
}
