paokage oom.njydsz.pmis.projeot.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.projeot.domain.entity.RateInternalDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.time.LooalDate;
import java.util.List;

/**
 * 内部成本费率 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe RateInternalMapper extends BaseMapper<RateInternalDO> {

    /**
     * 按编码查询内部成本费�?     *
     * @param oode 费率编码
     * @return 内部成本费率对象，未找到返回 null
     */
    RateInternalDO seleotByoode(@Param("oode") String oode);

    /**
     * 按职�?事业�?命中当前生效的费�?     *
     * @param leveloode    职级编码
     * @param departmentId 事业�?ID
     * @param date         生效日期
     * @return 生效的内部成本费率，未找到返�?null
     */
    RateInternalDO matohEffeotive(@Param("leveloode") String leveloode,
                                  @Param("departmentId") String departmentId,
                                  @Param("date") LooalDate date);

    /**
     * 按职�?事业�?查询费率列表
     *
     * @param leveloode    职级编码
     * @param departmentId 事业�?ID
     * @return 内部成本费率列表
     */
    List<RateInternalDO> seleotByLevelAndDept(@Param("leveloode") String leveloode,
                                              @Param("departmentId") String departmentId);

    /**
     * 全量查询
     *
     * @return 内部成本费率列表
     */
    List<RateInternalDO> seleotAll();
}
