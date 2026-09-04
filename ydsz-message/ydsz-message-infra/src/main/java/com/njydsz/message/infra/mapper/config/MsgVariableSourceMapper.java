package com.njydsz.message.infra.mapper.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.message.domain.entity.MsgVariableSource;

/**
 * 消息变量数据源 Mapper
 *
 * <p>对应数据表 <code>ydsz_msg_variable_source</code>。
 *
 * <p>数据源声明模板变量从哪个服务/接口/SQL 取值（动态变量），发送时由 {@code VariableResolver} 解析替换 ${var}。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_var_key — 变量 KEY 唯一索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.message.domain.entity.config.MsgVariableSource 变量源实体
 * @see com.njydsz.message.server.service.MsgVariableSourceService 变量源 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface MsgVariableSourceMapper extends BaseMapper<MsgVariableSource> {}
