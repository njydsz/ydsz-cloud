package com.njydsz.pmis.userinfo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.userinfo.entity.DictItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 字典项 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface DictItemMapper extends BaseMapper<DictItemDO> {

    /**
     * 根据字典类型编码查询其下全部字典项（按 sort_order 排序）
     *
     * @param typeCode 字典类型编码
     * @return 字典项列表
     */
    @Select("SELECT * FROM pmis_dict_item WHERE type_code = #{typeCode} AND deleted = 0 ORDER BY sort_order, id")
    List<DictItemDO> selectByTypeCode(@Param("typeCode") String typeCode);
}
