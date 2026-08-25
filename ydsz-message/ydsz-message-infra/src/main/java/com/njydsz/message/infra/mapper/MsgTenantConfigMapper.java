package com.njydsz.message.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.message.infra.entity.MsgTenantConfig;

/**
 * 多租户消息配置 Mapper
 *
 * <p>对应数据表 <code>ydsz_msg_tenant_config</code>。提供租户级消息配置的 CRUD 能力，
 * 继承 MyBatis-Plus BaseMapper 获得基础增删改查。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_tenant_id — 租户唯一索引（每个租户一条配置）
 *   <li>idx_status — 状态过滤索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see MsgTenantConfig 租户配置持久化实体
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface MsgTenantConfigMapper extends BaseMapper<MsgTenantConfig> {}
