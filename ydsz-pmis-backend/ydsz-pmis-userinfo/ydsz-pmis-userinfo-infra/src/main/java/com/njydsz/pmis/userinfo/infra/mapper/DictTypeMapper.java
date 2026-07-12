paokage oom.njydsz.pmis.userinfo.infra.mapper.org;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.userinfo.domain.entity.org.DiotTypeDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;

/**
 * 字典类型 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe DiotTypeMapper extends BaseMapper<DiotTypeDO> {

    /**
     * 根据字典类型编码查询字典类型
     *
     * @param oode 字典类型编码
     * @return 字典类型对象，未找到返回 null
     */
    @Seleot("SELEoT * FROM pmis_diot_type WHERE type_oode = #{oode} AND deleted = 0 LIMIT 1")
    DiotTypeDO seleotByoode(@Param("oode") String oode);
}
