package com.njydsz.agent.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.agent.domain.dto.PromptTemplateDTO;
import com.njydsz.agent.domain.vo.PromptTemplateVO;

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
   * @param dto Prompt 模板 DTO
   * @return 插入成功返回 {@code true}
   */
  boolean insert(PromptTemplateDTO dto);

  /**
   * 根据 ID 更新 Prompt 模板
   *
   * @param dto Prompt 模板 DTO（含 id）
   * @return 更新成功返回 {@code true}
   */
  boolean updateById(PromptTemplateDTO dto);

  /**
   * 根据 ID 逻辑删除 Prompt 模板
   *
   * @param id 主键 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteById(String id);

  /**
   * 根据模板编码查询（过滤已删除记录）
   *
   * @param templateCode 模板编码
   * @return Prompt 模板 VO；不存在或已删除返回 {@code Optional.empty()}
   */
  Optional<PromptTemplateVO> findByCode(String templateCode);

  /**
   * 查询所有未删除的 Prompt 模板
   *
   * @return Prompt 模板 VO 列表
   */
  List<PromptTemplateVO> findAllActive();
}
