package com.njydsz.pmis.system.infra.mapper.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.system.domain.entity.config.ConfigDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 系统配置 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface ConfigMapper extends BaseMapper<ConfigDO> {

    /**
     * 按分组查询全部配置
     *
     * @param group 配置分组
     * @return 配置列表
     */
    @Select("SELECT * FROM pmis_config WHERE config_group = #{group} AND deleted = 0 ORDER BY sort_order, id")
    List<ConfigDO> selectByGroup(@Param("group") String group);

    /**
     * 按 group + key 查询单条配置
     *
     * @param group 配置分组
     * @param key   配置键
     * @return 配置实体
     */
    @Select("SELECT * FROM pmis_config WHERE config_group = #{group} AND config_key = #{key} AND deleted = 0 LIMIT 1")
    ConfigDO selectByGroupAndKey(@Param("group") String group, @Param("key") String key);

    /**
     * 查询全部公开配置
     *
     * @return 公开配置列表
     */
    @Select("SELECT * FROM pmis_config WHERE is_public = 1 AND deleted = 0 ORDER BY sort_order, id")
    List<ConfigDO> selectPublic();

    /**
     * 按 group 逻辑删除所有配置（批量清理用）
     */
    @Update("UPDATE pmis_config SET deleted = 1, updated_at = CURRENT_TIMESTAMP " +
            "WHERE config_group = #{group} AND deleted = 0")
    int deleteByGroup(@Param("group") String group);

    /**
     * 按 group 批量更新状态
     */
    @Update("UPDATE pmis_config SET status = #{status}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE config_group = #{group} AND deleted = 0")
    int updateStatusByGroup(@Param("group") String group, @Param("status") String status);
}
