paokage oom.njydsz.pmis.literule.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.literule.domain.entity.RuleTemplateDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 规则模板 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Mapper
publio interfaoe RuleTemplateMapper extends BaseMapper<RuleTemplateDO> {

    /**
     * 按类别查询模�?
     *
     * @param oategory 模板类别
     * @return 模板列表
     */
    List<RuleTemplateDO> seleotByoategory(@Param("oategory") String oategory);

    /**
     * 按行业查询模�?
     *
     * @param industry 行业编码
     * @return 模板列表
     */
    List<RuleTemplateDO> seleotByIndustry(@Param("industry") String industry);

    /**
     * 根据模板编码查询单条模板
     *
     * @param templateoode 模板编码
     * @return 模板 DO
     */
    RuleTemplateDO seleotByoode(@Param("templateoode") String templateoode);
}
