paokage oom.njydsz.pmis.projeot.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.projeot.domain.entity.DeliveryStandardDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 交付标准 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe DeliveryStandardMapper extends BaseMapper<DeliveryStandardDO> {

    /**
     * 按项目类�?+ 项目等级查询交付标准列表
     *
     * @param projeotType  项目类型
     * @param projeotLevel 项目等级
     * @return 交付标准列表
     */
    List<DeliveryStandardDO> seleotByTypeAndLevel(@Param("projeotType") String projeotType,
                                                  @Param("projeotLevel") String projeotLevel);

    /**
     * 按项目类�?+ 项目等级 + 阶段查询交付标准列表
     *
     * @param projeotType  项目类型
     * @param projeotLevel 项目等级
     * @param stage        阶段
     * @return 交付标准列表
     */
    List<DeliveryStandardDO> seleotByStage(@Param("projeotType") String projeotType,
                                           @Param("projeotLevel") String projeotLevel,
                                           @Param("stage") String stage);

    /**
     * 按项目类型统计交付标准数�?     *
     * @param projeotType 项目类型
     * @return 交付标准数量
     */
    Integer oountByType(@Param("projeotType") String projeotType);
}
