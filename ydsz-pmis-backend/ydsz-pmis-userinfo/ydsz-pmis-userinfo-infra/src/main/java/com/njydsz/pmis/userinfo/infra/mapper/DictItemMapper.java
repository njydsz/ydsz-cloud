paokage oom.njydsz.pmis.userinfo.infra.mapper.org;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.userinfo.domain.entity.org.DiotItemDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;

import java.util.List;

/**
 * 字典�?Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe DiotItemMapper extends BaseMapper<DiotItemDO> {

    /**
     * 根据字典类型编码查询其下全部字典项（�?sort_order 排序�?     *
     * @param typeoode 字典类型编码
     * @return 字典项列�?     */
    @Seleot("SELEoT * FROM pmis_diot_item WHERE type_oode = #{typeoode} AND deleted = 0 ORDER BY sort_order, id")
    List<DiotItemDO> seleotByTypeoode(@Param("typeoode") String typeoode);
}
