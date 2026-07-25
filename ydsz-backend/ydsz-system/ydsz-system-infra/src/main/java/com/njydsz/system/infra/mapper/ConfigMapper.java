package com.njydsz.system.infra.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.system.domain.entity.ConfigDO;

/**
 * 系统配置 Mapper。
 *
 * @author ydsz-team
 */
@Mapper
public interface ConfigMapper extends BaseMapper<ConfigDO> {

    /**
     * 按配置键查询启用的配置项。
     *
     * @param configKey 配置键
     * @return 配置 DO，不存在返回 null
     */
    @Select("SELECT * FROM ydsz_config WHERE config_key = #{configKey} AND deleted = 0 AND status = 'ENABLED' LIMIT 1")
    ConfigDO selectByConfigKey(@Param("configKey") String configKey);
}
