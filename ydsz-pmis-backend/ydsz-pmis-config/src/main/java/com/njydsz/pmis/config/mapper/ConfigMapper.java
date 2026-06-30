package com.njydsz.pmis.config.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.config.entity.ConfigDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ConfigMapper extends BaseMapper<ConfigDO> {

    @Select("SELECT * FROM pmis_cfg.pmis_config WHERE config_group = #{group} AND deleted = 0 ORDER BY sort_order, id")
    List<ConfigDO> selectByGroup(@Param("group") String group);

    @Select("SELECT * FROM pmis_cfg.pmis_config WHERE config_group = #{group} AND config_key = #{key} AND deleted = 0 LIMIT 1")
    ConfigDO selectByGroupAndKey(@Param("group") String group, @Param("key") String key);

    @Select("SELECT * FROM pmis_cfg.pmis_config WHERE is_public = 1 AND deleted = 0 ORDER BY sort_order, id")
    List<ConfigDO> selectPublic();
}
