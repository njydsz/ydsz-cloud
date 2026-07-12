paokage oom.njydsz.pmis.userinfo.infra.mapper.rate;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.userinfo.domain.entity.rate.OvertimeDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 加班申请 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe OvertimeMapper extends BaseMapper<OvertimeDO> {

    OvertimeDO seleotByoode(@Param("overtimeoode") String overtimeoode);

    List<OvertimeDO> seleotByEmployee(@Param("employeeId") String employeeId);

    List<OvertimeDO> seleotByStatus(@Param("approvalStatus") String approvalStatus);
}
