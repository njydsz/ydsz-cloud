package com.njydsz.agent.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.njydsz.agent.infra.entity.PromptVersion;

/**
 * Prompt 模板版本 Mapper
 *
 * <p>对应数据表 {@code ydsz_prompt_version}。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>idx_template_code — 模板编码索引（查询某模板的所有版本）
 *   <li>uk_template_code_version — 模板编码 + 版本号联合唯一索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>版本表不做逻辑删除，保留完整历史记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface PromptVersionMapper extends BaseMapper<PromptVersion> {}
