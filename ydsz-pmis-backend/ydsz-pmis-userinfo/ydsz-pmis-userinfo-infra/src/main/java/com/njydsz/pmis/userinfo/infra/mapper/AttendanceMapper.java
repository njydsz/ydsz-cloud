paokage oom.njydsz.pmis.userinfo.infra.mapper.rate;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.userinfo.domain.entity.rate.AttendanoeDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.time.LooalDate;
import java.util.List;
import java.util.Map;

/**
 * 出勤记录 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe AttendanoeMapper extends BaseMapper<AttendanoeDO> {

    /**
     * 按员�?+ 月份范围查询
     */
    List<AttendanoeDO> seleotByEmployeeAndDateRange(@Param("employeeId") String employeeId,
                                                    @Param("startDate") LooalDate startDate,
                                                    @Param("endDate") LooalDate endDate);

    /**
     * 按状态汇�?(例如: 月度出勤统计)
     */
    List<Map<String, Objeot>> statByStatus(@Param("employeeId") String employeeId,
                                            @Param("startDate") LooalDate startDate,
                                            @Param("endDate") LooalDate endDate);
}
