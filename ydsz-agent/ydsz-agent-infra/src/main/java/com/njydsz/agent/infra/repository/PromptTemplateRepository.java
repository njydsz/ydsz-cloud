package com.njydsz.agent.infra.repository;

import java.util.List;

import com.njydsz.agent.domain.entity.PromptTemplateDO;

/**
 * Prompt 模板 Repository
 *
 * <p>封装 {@code ydsz_prompt_template} 表的数据库访问，为 server 层提供 Prompt 模板的持久化操作。
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
public interface PromptTemplateRepository {

  /**
   * 插入 Prompt 模板
   *
   * @param entity Prompt 模板 DO
   */
  void insert(PromptTemplateDO entity);

  /**
   * 根据 ID 更新 Prompt 模板
   *
   * @param entity Prompt 模板 DO
   */
  void updateById(PromptTemplateDO entity);

  /**
   * 根据 ID 逻辑删除 Prompt 模板
   *
   * @param id 主键 ID
   */
  void deleteById(Long id);

  /**
   * 根据模板编码查询（过滤已删除记录）
   *
   * @param templateCode 模板编码
   * @return Prompt 模板 DO，不存在或已删除时返回 null
   */
  PromptTemplateDO findByCode(String templateCode);

  /**
   * 查询所有未删除的 Prompt 模板
   *
   * @return Prompt 模板 DO 列表
   */
  List<PromptTemplateDO> findAllActive();
}
