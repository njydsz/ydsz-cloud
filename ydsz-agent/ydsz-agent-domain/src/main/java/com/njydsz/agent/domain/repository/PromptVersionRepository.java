package com.njydsz.agent.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.agent.domain.dto.PromptVersionDTO;
import com.njydsz.agent.domain.vo.PromptVersionVO;

/**
 * Prompt 模板版本 Repository
 *
 * <p>封装 {@code ydsz_agt_prompt_version} 表的数据库访问，为 server 层提供 Prompt 版本的持久化操作。
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
 * @since 26.09.01
 */
public interface PromptVersionRepository {

  /**
   * 插入 Prompt 版本快照
   *
   * @param dto Prompt 版本 DTO
   * @return 插入成功返回 {@code true}
   */
  boolean insert(PromptVersionDTO dto);

  /**
   * 根据模板编码和版本号查询版本记录
   *
   * @param templateCode 模板编码
   * @param version 版本号
   * @return Prompt 版本 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<PromptVersionVO> findByTemplateCodeAndVersion(String templateCode, int version);

  /**
   * 根据模板编码查询所有版本（按版本号升序）
   *
   * @param templateCode 模板编码
   * @return Prompt 版本 VO 列表
   */
  List<PromptVersionVO> findByTemplateCode(String templateCode);
}
