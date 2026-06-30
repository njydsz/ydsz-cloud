package com.njydsz.pmis.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.user.entity.DictItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DictItemMapper extends BaseMapper<DictItemDO> {

    @Select("SELECT * FROM pmis_dict_item WHERE type_code = #{typeCode} AND deleted = 0 ORDER BY sort_order, id")
    List<DictItemDO> selectByTypeCode(@Param("typeCode") String typeCode);
}
