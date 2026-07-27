package com.njydsz.system.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.system.domain.entity.DictItem;

/**
 * 字典项 Mapper。
 *
 * @author ydsz-team
 */
@Mapper
public interface DictItemMapper extends BaseMapper<DictItem> {

    /**
     * 按类型编码和字典项编码查询启用的字典项。
     *
     * @param typeCode 字典类型编码
     * @param itemCode 字典项编码
     * @return 字典项 DO，不存在返回 null
     */
    @Select("SELECT * FROM ydsz_dict_item WHERE type_code = #{typeCode} AND item_code = #{itemCode} "
            + "AND deleted = 0 AND status = 'ENABLED' LIMIT 1")
    DictItem selectByTypeAndCode(@Param("typeCode") String typeCode, @Param("itemCode") String itemCode);

    /**
     * 按类型编码查询所有启用的字典项（按排序号升序）。
     *
     * @param typeCode 字典类型编码
     * @return 字典项列表
     */
    @Select("SELECT * FROM ydsz_dict_item WHERE type_code = #{typeCode} AND deleted = 0 AND status = 'ENABLED' "
            + "ORDER BY sort_order ASC")
    List<DictItem> listEnabledByTypeCode(@Param("typeCode") String typeCode);
}
