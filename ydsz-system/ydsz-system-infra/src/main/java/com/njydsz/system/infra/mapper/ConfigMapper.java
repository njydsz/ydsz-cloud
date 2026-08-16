package com.njydsz.system.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.njydsz.system.domain.entity.Config;

/**
 * 系统配置 Mapper
 *
 * <p>对应数据表 <code>ydsz_config</code>。
 * <p>配置项是平台级/租户级配置（功能开关/三方密钥/超时时间），支持热更新。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_config_key — 配置 KEY 唯一索引</li>
 *   <li>idx_tenant_id — 租户隔离索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.system.domain.entity.Config 配置实体
 * @see com.njydsz.system.server.service.ConfigService 配置 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
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
