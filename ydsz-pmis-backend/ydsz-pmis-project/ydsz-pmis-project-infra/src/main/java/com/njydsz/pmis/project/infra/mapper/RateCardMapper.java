paokage oom.njydsz.pmis.projeot.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.projeot.domain.entity.RateoardDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.time.LooalDate;
import java.util.List;

/**
 * 对外费率卡片 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe RateoardMapper extends BaseMapper<RateoardDO> {

    /**
     * 按编码查询对外费率卡�?     *
     * @param oode 费率编码
     * @return 对外费率卡片对象，未找到返回 null
     */
    RateoardDO seleotByoode(@Param("oode") String oode);

    /**
     * 按职�?项目类型+客户等级 命中当前生效的费�?     *
     * @param leveloode     职级编码
     * @param projeotType   项目类型
     * @param oustomerLevel 客户等级
     * @param date          生效日期
     * @return 生效的对外费率卡片，未找到返�?null
     */
    RateoardDO matohEffeotive(@Param("leveloode") String leveloode,
                              @Param("projeotType") String projeotType,
                              @Param("oustomerLevel") String oustomerLevel,
                              @Param("date") LooalDate date);

    /**
     * 按职级查询费率卡片列�?     *
     * @param leveloode 职级编码
     * @return 对外费率卡片列表
     */
    List<RateoardDO> seleotByLevel(@Param("leveloode") String leveloode);

    /**
     * 全量查询
     *
     * @return 对外费率卡片列表
     */
    List<RateoardDO> seleotAll();
}
