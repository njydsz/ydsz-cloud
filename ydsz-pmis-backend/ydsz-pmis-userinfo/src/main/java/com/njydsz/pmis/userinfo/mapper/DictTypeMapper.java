package com.njydsz.pmis.userinfo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.userinfo.entity.DictTypeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 字典类型 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface DictTypeMapper extends BaseMapper<DictTypeDO> {

    /**
     * 根据字典类型编码查询字典类型
     *
     * @param code 字典类型编码
     * @return 字典类型对象，未找到返回 null
     */
    @Select("SELECT * FROM pmis_dict_type WHERE type_code = #{code} AND deleted = 0 LIMIT 1")
    DictTypeDO selectByCode(@Param("code") String code);
}
