package com.njydsz.userinfo.server.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.userinfo.domain.dto.SamlIdpCreateDTO;
import com.njydsz.userinfo.domain.dto.SamlIdpUpdateDTO;
import com.njydsz.userinfo.domain.query.SamlIdpPageQuery;
import com.njydsz.userinfo.domain.repository.SamlIdpConfigRepository;
import com.njydsz.userinfo.domain.vo.SamlIdpConfigVO;

/**
 * SAML 身份提供者配置服务（P2-1 多租户）。
 *
 * <p>提供 SAML IdP 配置管理能力，支持多租户独立配置 SAML IdP（如企业微信 SAML、飞书 SAML、ADFS）。
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>租户 A 对接企业微信 SAML — 配置 entityId、ssoUrl、证书</li>
 *   <li>租户 B 对接飞书 SAML — 独立配置，互不影响</li>
 *   <li>全局默认 IdP — 作为未配置 SAML 的租户的回落方案</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SamlIdpConfigService {

  private final SamlIdpConfigRepository samlIdpConfigRepository;

  /**
   * 分页查询 SAML IdP 配置列表。
   *
   * @param query 分页查询参数
   * @return IdP 配置 VO 列表
   */
  public List<SamlIdpConfigVO> findByPage(SamlIdpPageQuery query) {
    return samlIdpConfigRepository.findByPage(query);
  }

  /**
   * 查询所有已启用的 IdP 配置（按 sortOrder 升序）。
   *
   * @return 已启用的 IdP 配置列表
   */
  public List<SamlIdpConfigVO> findEnabled() {
    return samlIdpConfigRepository.findEnabled();
  }

  /**
   * 根据 Entity ID 查询 IdP 配置。
   *
   * @param entityId IdP Entity ID
   * @return IdP 配置 VO
   */
  public SamlIdpConfigVO findByEntityId(String entityId) {
    return samlIdpConfigRepository.findByEntityId(entityId).orElse(null);
  }

  /**
   * 创建 SAML IdP 配置。
   *
   * @param dto 创建 DTO
   */
  public void create(SamlIdpCreateDTO dto) {
    samlIdpConfigRepository.save(dto);
    log.info("SAML IdP 配置已创建: entityId={}", dto.getEntityId());
  }

  /**
   * 更新 SAML IdP 配置。
   *
   * @param entityId IdP Entity ID
   * @param dto 更新 DTO
   */
  public void update(String entityId, SamlIdpUpdateDTO dto) {
    samlIdpConfigRepository.update(entityId, dto);
    log.info("SAML IdP 配置已更新: entityId={}", entityId);
  }

  /**
   * 删除 SAML IdP 配置。
   *
   * @param entityId IdP Entity ID
   */
  public void delete(String entityId) {
    samlIdpConfigRepository.deleteByEntityId(entityId);
    log.info("SAML IdP 配置已删除: entityId={}", entityId);
  }
}
