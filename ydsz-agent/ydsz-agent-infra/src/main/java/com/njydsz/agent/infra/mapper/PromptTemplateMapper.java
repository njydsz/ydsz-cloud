package com.njydsz.agent.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.njydsz.agent.infra.entity.PromptTemplate;

/**
 * Prompt 模板 Mapper
 *
 * <p>对应数据表 {@code ydsz_prompt_template}。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_template_code — 模板编码唯一索引
 *   <li>idx_category — 分类检索索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface PromptTemplateMapper extends BaseMapper<PromptTemplate> {}
