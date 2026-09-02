package com.njydsz.userinfo.server.service;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.userinfo.domain.dto.AuthPolicyDTO;
import com.njydsz.userinfo.domain.query.AuthPolicyPageQuery;
import com.njydsz.userinfo.domain.repository.AuthPolicyRepository;
import com.njydsz.userinfo.domain.vo.AuthPolicyVO;

/**
 * 认证策略服务（P3-1 多租户认证域隔离）。
 *
 * <p>提供租户级认证策略管理能力，支持不同租户独立配置认证策略。
 *
 * <p><b>策略继承：</b>租户级策略未配置的字段继承全局默认策略值。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthPolicyService {
  /** 默认密码最小长度 */
  private static final int DEFAULT_PASSWORD_MIN_LENGTH = 8;

  /** 默认每用户最大会话数 */
  private static final int DEFAULT_MAX_SESSIONS_PER_USER = 3;

  /** 默认会话超时（秒）：2 小时 */
  private static final long DEFAULT_SESSION_TIMEOUT_SECONDS = 7200;


  private final AuthPolicyRepository authPolicyRepository;

  /**
   * 分页查询认证策略列表。
   *
   * @param query 分页查询参数
   * @return 策略 VO 列表
   */
  public List<AuthPolicyVO> findByPage(AuthPolicyPageQuery query) {
    return authPolicyRepository.findByPage(query);
  }

  /**
   * 根据租户 ID 查询认证策略（含全局默认值合并）。
   *
   * <p>租户策略优先，未设置字段使用全局默认值。
   *
   * @param tenantId 租户 ID
   * @return 合并后的认证策略 VO
   */
  public AuthPolicyVO findByTenantId(String tenantId) {
    // 先查租户级策略
    Optional<AuthPolicyVO> tenantPolicy = authPolicyRepository.findByTenantId(tenantId);

    // 查全局默认策略
    Optional<AuthPolicyVO> defaultPolicy = authPolicyRepository.findByTenantId(null);

    if (tenantPolicy.isEmpty() && defaultPolicy.isEmpty()) {
      // 两者都不存在 → 返回硬编码默认值
      return buildHardcodedDefault();
    }

    if (tenantPolicy.isPresent() && defaultPolicy.isEmpty()) {
      return tenantPolicy.get();
    }

    if (tenantPolicy.isEmpty()) {
      return defaultPolicy.get();
    }

    // 两者都存在 → 合并（租户策略优先）
    return mergePolicies(defaultPolicy.get(), tenantPolicy.get());
  }

  /**
   * 保存认证策略（创建或更新）。
   *
   * @param dto 认证策略 DTO
   */
  public void save(AuthPolicyDTO dto) {
    authPolicyRepository.save(dto);
    log.info("认证策略已保存: tenantId={}, name={}", dto.getTenantId(), dto.getName());
  }

  /**
   * 删除认证策略。
   *
   * @param tenantId 租户 ID
   */
  public void delete(String tenantId) {
    authPolicyRepository.deleteByTenantId(tenantId);
    log.info("认证策略已删除: tenantId={}", tenantId);
  }

  /**
   * 合并策略：租户策略优先，未设置字段使用全局默认值。
   */
  private AuthPolicyVO mergePolicies(AuthPolicyVO defaultPolicy, AuthPolicyVO tenantPolicy) {
    AuthPolicyVO merged = new AuthPolicyVO();
    merged.setId(tenantPolicy.getId());
    merged.setTenantId(tenantPolicy.getTenantId());
    merged.setName(tenantPolicy.getName() != null ? tenantPolicy.getName() : defaultPolicy.getName());
    merged.setPasswordMinLength(tenantPolicy.getPasswordMinLength() != null
        ? tenantPolicy.getPasswordMinLength() : defaultPolicy.getPasswordMinLength());
    merged.setPasswordRequireUppercase(tenantPolicy.getPasswordRequireUppercase() != null
        ? tenantPolicy.getPasswordRequireUppercase() : defaultPolicy.getPasswordRequireUppercase());
    merged.setPasswordRequireDigit(tenantPolicy.getPasswordRequireDigit() != null
        ? tenantPolicy.getPasswordRequireDigit() : defaultPolicy.getPasswordRequireDigit());
    merged.setMfaEnabled(tenantPolicy.getMfaEnabled() != null
        ? tenantPolicy.getMfaEnabled() : defaultPolicy.getMfaEnabled());
    merged.setCaptchaEnabled(tenantPolicy.getCaptchaEnabled() != null
        ? tenantPolicy.getCaptchaEnabled() : defaultPolicy.getCaptchaEnabled());
    merged.setAllowedIdentityProviders(tenantPolicy.getAllowedIdentityProviders() != null
        ? tenantPolicy.getAllowedIdentityProviders() : defaultPolicy.getAllowedIdentityProviders());
    merged.setMaxSessionsPerUser(tenantPolicy.getMaxSessionsPerUser() != null
        ? tenantPolicy.getMaxSessionsPerUser() : defaultPolicy.getMaxSessionsPerUser());
    merged.setSessionTimeoutSeconds(tenantPolicy.getSessionTimeoutSeconds() != null
        ? tenantPolicy.getSessionTimeoutSeconds() : defaultPolicy.getSessionTimeoutSeconds());
    merged.setRemark(tenantPolicy.getRemark());
    merged.setCreatedAt(tenantPolicy.getCreatedAt());
    merged.setUpdatedAt(tenantPolicy.getUpdatedAt());
    return merged;
  }

  /**
   * 构建硬编码默认策略（数据库无任何配置时的最终兜底）。
   */
  private AuthPolicyVO buildHardcodedDefault() {
    AuthPolicyVO vo = new AuthPolicyVO();
    vo.setId("hardcoded-default");
    vo.setName("硬编码默认策略");
    vo.setPasswordMinLength(DEFAULT_PASSWORD_MIN_LENGTH);
    vo.setPasswordRequireUppercase(true);
    vo.setPasswordRequireDigit(true);
    vo.setMfaEnabled(false);
    vo.setCaptchaEnabled(true);
    vo.setAllowedIdentityProviders("LOCAL");
    vo.setMaxSessionsPerUser(DEFAULT_MAX_SESSIONS_PER_USER);
    vo.setSessionTimeoutSeconds((int) DEFAULT_SESSION_TIMEOUT_SECONDS);
    vo.setRemark("硬编码兜底策略");
    return vo;
  }
}
