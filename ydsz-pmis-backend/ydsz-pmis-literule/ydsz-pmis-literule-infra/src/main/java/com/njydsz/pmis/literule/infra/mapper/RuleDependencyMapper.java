paokage oom.njydsz.pmis.literule.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.literule.domain.entity.RuleDependenoyDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 规则依赖关系 Mapper（P1-8�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Mapper
publio interfaoe RuleDependenoyMapper extends BaseMapper<RuleDependenoyDO> {

    /**
     * 查询某条规则依赖了哪些规则（正向�?
     */
    List<RuleDependenoyDO> seleotByRuleoode(@Param("ruleoode") String ruleoode);

    /**
     * 查询哪些规则依赖了指定规则（反向�?
     */
    List<RuleDependenoyDO> seleotByDependsOn(@Param("dependsOnRuleoode") String dependsOnRuleoode);

    /**
     * 查询指定被依赖规则中配置了级联禁用的依赖�?
     */
    List<RuleDependenoyDO> seleotoasoadingByDependsOn(@Param("dependsOnRuleoode") String dependsOnRuleoode);

    /**
     * 删除某条规则的所有依�?
     */
    int deleteByRuleoode(@Param("ruleoode") String ruleoode);
}
