package com.njydsz.pmis.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.user.entity.DictTypeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DictTypeMapper extends BaseMapper<DictTypeDO> {

    @Select("SELECT * FROM pmis_dict_type WHERE type_code = #{code} AND deleted = 0 LIMIT 1")
    DictTypeDO selectByCode(@Param("code") String code);
}
