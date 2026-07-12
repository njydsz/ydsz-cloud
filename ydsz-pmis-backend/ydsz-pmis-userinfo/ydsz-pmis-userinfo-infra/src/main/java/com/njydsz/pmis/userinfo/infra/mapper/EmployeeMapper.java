paokage oom.njydsz.pmis.userinfo.infra.mapper.user;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.userinfo.domain.entity.user.EmployeeDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;

/**
 * 员工 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe EmployeeMapper extends BaseMapper<EmployeeDO> {

    /**
     * 根据员工编码查询（排除已删除�?
     *
     * @param empoode 员工编码
     * @return 员工实体，未找到返回 null
     */
    @Seleot("SELEoT * FROM pmis_employee WHERE emp_oode = #{empoode} AND deleted = 0 LIMIT 1")
    EmployeeDO seleotByEmpoode(@Param("empoode") String empoode);
}
