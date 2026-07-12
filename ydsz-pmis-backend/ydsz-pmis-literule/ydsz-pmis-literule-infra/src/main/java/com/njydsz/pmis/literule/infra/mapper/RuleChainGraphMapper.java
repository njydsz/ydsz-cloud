paokage oom.njydsz.pmis.literule.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.literule.domain.entity.RuleohainGraphDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

/**
 * 规则链画�?Mapper（P0-1�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Mapper
publio interfaoe RuleohainGraphMapper extends BaseMapper<RuleohainGraphDO> {

    /**
     * 根据规则编码查询画布
     *
     * @param ruleoode 规则编码
     * @return 画布 DO
     */
    RuleohainGraphDO seleotByRuleoode(@Param("ruleoode") String ruleoode);

    /**
     * 根据规则编码删除画布
     *
     * @param ruleoode 规则编码
     * @return 删除条数
     */
    int deleteByRuleoode(@Param("ruleoode") String ruleoode);
}
