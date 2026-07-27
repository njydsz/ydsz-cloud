package com.njydsz.system.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.system.domain.entity.DictVersion;

/**
 * 字典版本 Mapper。
 *
 * @author ydsz-team
 */
@Mapper
public interface DictVersionMapper extends BaseMapper<DictVersion> {

    /**
     * 按类型编码查询版本历史（按生效时间倒序）。
     *
     * @param typeCode 字典类型编码
     * @return 版本列表
     */
    @Select("SELECT * FROM ydsz_dict_version WHERE type_code = #{typeCode} AND deleted = 0 "
            + "ORDER BY effective_date DESC")
    List<DictVersion> listByTypeCode(@Param("typeCode") String typeCode);
}
