package com.njydsz.userinfo.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.userinfo.domain.dto.SamlIdpDTO;
import com.njydsz.userinfo.domain.query.SamlIdpPageQuery;
import com.njydsz.userinfo.domain.vo.SamlIdpConfigVO;

/**
 * SAML 身份提供者配置仓储接口（P2-1 领域契约层）。
 *
 * <p>定义 SAML IdP 配置数据访问能力，入参为领域 DTO / 基本类型字段，返回值为领域 VO。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>入参必须是 {@code dto/} 下的 DTO 或基本类型字段，禁止接受 infra 层 DO/PO</li>
 *   <li>返回值必须是 {@code vo/} 下的 VO 或 {@code Optional<VO>}，禁止返回 infra 层持久化实体</li>
 *   <li>domain 层对 infra 层零感知，禁止 import {@code infra.entity} 包</li>
 *   <li>实现类位于 {@code ydsz-userinfo-infra} 模块，通过 Converter 完成 DO ↔ VO 转换</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface SamlIdpConfigRepository {

  /**
   * 根据 Entity ID 查询 IdP 配置。
   *
   * @param entityId IdP Entity ID
   * @return IdP 配置 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<SamlIdpConfigVO> findByEntityId(String entityId);

  /**
   * 分页查询 IdP 配置列表。
   *
   * @param query 分页查询参数
   * @return IdP 配置 VO 列表
   */
  List<SamlIdpConfigVO> findByPage(SamlIdpPageQuery query);

  /**
   * 查询所有已启用的 IdP 配置（按 sortOrder 升序）。
   *
   * @return 已启用的 IdP 配置列表
   */
  List<SamlIdpConfigVO> findEnabled();

  /**
   * 保存 IdP 配置（创建或更新）。
   *
   * <p>统一 DTO：创建时如果 {@code entityId} 已存在则更新，否则创建。
   *
   * @param dto SAML IdP DTO
   */
  void save(SamlIdpDTO dto);

  /**
   * 根据 Entity ID 删除 IdP 配置（逻辑删除）。
   *
   * @param entityId IdP Entity ID
   */
  void deleteByEntityId(String entityId);
}
