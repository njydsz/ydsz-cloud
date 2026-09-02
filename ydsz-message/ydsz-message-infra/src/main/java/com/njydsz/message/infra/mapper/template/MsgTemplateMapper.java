package com.njydsz.message.infra.mapper.template;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.message.infra.entity.MsgTemplate;

/**
 * 消息模板 Mapper
 *
 * <p>对应数据表 <code>ydsz_msg_template</code>。
 *
 * <p>模板定义消息的标题/内容/变量占位符（{@code ${var}}）/渠道（IM/邮件/短信/站内），按版本管理（{@code ydsz_msg_template_version}）。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_template_code — 模板编码唯一索引
 *   <li>idx_biz_type — 业务类型过滤索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see MsgTemplate 模板实体
 * @see com.njydsz.message.server.service.MsgTemplateService 模板 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface MsgTemplateMapper extends BaseMapper<MsgTemplate> {}
