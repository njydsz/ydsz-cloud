package com.njydsz.system.infra.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.system.domain.entity.Config;

/**
 * 系统配置 Mapper 接口
 *
 * <p>提供对 {@code ydsz_config} 表的 CRUD 操作 + 高频查询自定义 SQL。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得基础 CRUD 能力；
 * 通过 {@link Select} 注解声明按 {@code configKey} 单条查询方法。
 *
 * <p><b>自定义 SQL：</b>
 * <ul>
 *   <li>{@link #selectByConfigKey} — 按 {@code configKey} 单条查询（已过滤启用 + 未删除）</li>
 * </ul>
 *
 * <p><b>租户隔离：</b>所有查询自动由 MyBatis 拦截器注入 {@code tenant_id} 过滤条件。
 *
 * <p><b>逻辑删除：</b>实体配置了 {@code @TableLogic} 字段 {@code deleted}，删除为逻辑删除。
 *
 * <p><b>索引利用：</b>{@code config_key} 命中 {@code uk_tenant_group_key} 唯一索引的「不指定 group」前缀扫描。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.system.domain.entity.Config 系统配置实体
 * @see com.njydsz.system.server.service.ConfigService 系统配置 Service
 */
@Mapper
public interface ConfigMapper extends BaseMapper<Config> {

    /**
     * 按配置键查询启用的配置项
     *
     * <p>走 {@code uk_tenant_group_key} 唯一索引前缀；仅返回 {@code status=ENABLED AND deleted=0} 的记录。
     *
     * @param configKey 配置键
     * @return 配置 DO；不存在返回 {@code null}
     */
    @Select("SELECT * FROM ydsz_config WHERE config_key = #{configKey} AND deleted = 0 AND status = 'ENABLED' LIMIT 1")
    Config selectByConfigKey(@Param("configKey") String configKey);
}
