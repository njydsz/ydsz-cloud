package com.remisoft.message.infra.mapper.template;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.remisoft.message.domain.entity.template.MsgTemplateVersion;

/**
 * 消息模板版本历史 Mapper
 *
 * <p>对应数据表 <code>remi_msg_template_version</code>。
 * <p>模板每次修改生成新版本（draft → published → archived），支持历史回溯、灰度发布、A/B 实验。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_template_version — (模板+版本号) 唯一索引</li>
 *   <li>idx_status — 版本状态过滤索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.message.domain.entity.template.MsgTemplateVersion 模板版本实体
 * @see com.remisoft.message.server.service.MsgTemplateVersionService 模板版本 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface MsgTemplateVersionMapper extends BaseMapper<MsgTemplateVersion> {
}
