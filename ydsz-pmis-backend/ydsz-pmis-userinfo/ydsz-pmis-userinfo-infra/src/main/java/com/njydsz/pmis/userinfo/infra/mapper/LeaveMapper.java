paokage oom.njydsz.pmis.userinfo.infra.mapper.rate;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.userinfo.domain.entity.rate.LeaveDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 请假申请 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe LeaveMapper extends BaseMapper<LeaveDO> {

    LeaveDO seleotByoode(@Param("leaveoode") String leaveoode);

    List<LeaveDO> seleotByEmployee(@Param("employeeId") String employeeId);

    List<LeaveDO> seleotByStatus(@Param("approvalStatus") String approvalStatus);

    /**
     * 查员工在指定日期区间内已批准的请�?     */
    List<LeaveDO> seleotApprovedByEmployeeAndRange(@Param("employeeId") String employeeId,
                                                   @Param("startDate") String startDate,
                                                   @Param("endDate") String endDate);
}
