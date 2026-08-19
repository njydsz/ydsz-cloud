package com.njydsz.userinfo.server.oauth2;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.oauth2.OAuth2Application;
import com.njydsz.userinfo.domain.oauth2.OAuth2ApplicationRepository;

/**
 * OAuth2 应用管理服务。
 *
 * <p>提供 OAuth2 客户端应用的注册、查询、更新和删除功能。
 *
 * <p><b>核心规则：</b>
 *
 * <ul>
 *   <li>clientId 全局唯一，自动生成（前缀 + 随机串）</li>
 *   <li>clientSecret 使用 BCrypt 加密存储，创建时返回明文（仅此一次）</li>
 *   <li>支持 CONFIDENTIAL（机密客户端）和 PUBLIC（公共客户端）两种类型</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 2.18.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2ApplicationService {

  /** clientId 前缀 */
  private static final String CLIENT_ID_PREFIX = "ydsz_";

  /** clientId 随机部分长度（字节） */
  private static final int CLIENT_ID_RANDOM_BYTES = 16;

  /** clientSecret 随机部分长度（字节） */
  private static final int CLIENT_SECRET_RANDOM_BYTES = 32;

  private final OAuth2ApplicationRepository applicationRepository;
  private final BCryptPasswordEncoder passwordEncoder;
  private final SecureRandom secureRandom = new SecureRandom();

  /**
   * 注册 OAuth2 应用。
   *
   * <p>自动生成 clientId 和 clientSecret，clientSecret 使用 BCrypt 加密存储。
   * 创建成功后返回的应用对象中包含明文 clientSecret（仅此一次，请妥善保存）。
   *
   * @param clientName 应用名称
   * @param clientType 客户端类型
   * @param redirectUris 授权回调地址白名单
   * @param allowedScopes 允许申请的权限范围
   * @param allowedAudiences 允许的受众
   * @param description 应用描述
   * @param iconUrl 应用图标 URL
   * @return 注册成功的应用（含明文 clientSecret）
   */
  @Transactional(rollbackFor = Exception.class)
  public OAuth2Application registerApplication(
      String clientName,
      OAuth2Application.ClientType clientType,
      List<String> redirectUris,
      java.util.Set<String> allowedScopes,
      java.util.Set<String> allowedAudiences,
      String description,
      String iconUrl) {
    // 参数校验
    if (clientName == null || clientName.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.PARAM_INVALID);
    }
    if (redirectUris == null || redirectUris.isEmpty()) {
      throw new BusinessException(UserInfoExceptionCode.PARAM_INVALID);
    }

    String clientId = generateClientId();
    String plainClientSecret = generateClientSecret();
    String encodedClientSecret = passwordEncoder.encode(plainClientSecret);

    OAuth2Application application = new OAuth2Application(
        UUID.randomUUID().toString(),
        clientId,
        clientName,
        encodedClientSecret,
        clientType,
        redirectUris,
        allowedScopes,
        allowedAudiences,
        OAuth2Application.ApplicationStatus.ENABLED,
        description,
        iconUrl,
        java.time.LocalDateTime.now(),
        java.time.LocalDateTime.now(),
        getCurrentUserId());

    OAuth2Application saved = applicationRepository.save(application);

    // 返回包含明文密钥的应用对象（仅创建时返回）
    log.info("OAuth2 application registered: clientId={}, clientName={}", clientId, clientName);
    return saved.withPlainSecret(plainClientSecret);
  }

  /**
   * 重置应用密钥。
   *
   * <p>生成新的 clientSecret，旧密钥立即失效。
   *
   * @param id 应用 ID
   * @return 包含新明文 clientSecret 的应用
   */
  @Transactional(rollbackFor = Exception.class)
  public OAuth2Application resetSecret(String id) {
    OAuth2Application existing = applicationRepository.findById(id)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.OAUTH2_CLIENT_INVALID));

    String newPlainSecret = generateClientSecret();
    String encodedSecret = passwordEncoder.encode(newPlainSecret);

    OAuth2Application updated = new OAuth2Application(
        existing.id(),
        existing.clientId(),
        existing.clientName(),
        encodedSecret,
        existing.clientType(),
        existing.redirectUris(),
        existing.allowedScopes(),
        existing.allowedAudiences(),
        existing.status(),
        existing.description(),
        existing.iconUrl(),
        existing.createdAt(),
        java.time.LocalDateTime.now(),
        existing.createdBy());

    applicationRepository.save(updated);
    log.info("OAuth2 client secret reset: clientId={}", existing.clientId());
    return updated.withPlainSecret(newPlainSecret);
  }

  /**
   * 更新应用信息。
   *
   * @param id 应用 ID
   * @param clientName 应用名称
   * @param redirectUris 授权回调地址白名单
   * @param allowedScopes 允许申请的权限范围
   * @param allowedAudiences 允许的受众
   * @param description 应用描述
   * @param iconUrl 应用图标 URL
   * @param status 应用状态
   * @return 更新后的应用
   */
  @Transactional(rollbackFor = Exception.class)
  public OAuth2Application updateApplication(
      String id,
      String clientName,
      List<String> redirectUris,
      java.util.Set<String> allowedScopes,
      java.util.Set<String> allowedAudiences,
      String description,
      String iconUrl,
      OAuth2Application.ApplicationStatus status) {
    OAuth2Application existing = applicationRepository.findById(id)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.OAUTH2_CLIENT_INVALID));

    OAuth2Application updated = new OAuth2Application(
        existing.id(),
        existing.clientId(),
        clientName != null ? clientName : existing.clientName(),
        existing.clientSecret(),
        existing.clientType(),
        redirectUris != null ? redirectUris : existing.redirectUris(),
        allowedScopes != null ? allowedScopes : existing.allowedScopes(),
        allowedAudiences != null ? allowedAudiences : existing.allowedAudiences(),
        status != null ? status : existing.status(),
        description != null ? description : existing.description(),
        iconUrl != null ? iconUrl : existing.iconUrl(),
        existing.createdAt(),
        java.time.LocalDateTime.now(),
        existing.createdBy());

    applicationRepository.save(updated);
    log.info("OAuth2 application updated: clientId={}", existing.clientId());
    return updated;
  }

  /**
   * 删除应用。
   *
   * @param id 应用 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteApplication(String id) {
    OAuth2Application existing = applicationRepository.findById(id)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.OAUTH2_CLIENT_INVALID));
    applicationRepository.deleteById(id);
    log.info("OAuth2 application deleted: clientId={}", existing.clientId());
  }

  /**
   * 根据 ID 查询应用。
   *
   * @param id 应用 ID
   * @return 应用
   */
  public OAuth2Application getById(String id) {
    return applicationRepository.findById(id)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.OAUTH2_CLIENT_INVALID));
  }

  /**
   * 根据 clientId 查询应用。
   *
   * @param clientId 客户端 ID
   * @return 应用
   */
  public OAuth2Application getByClientId(String clientId) {
    return applicationRepository.findByClientId(clientId)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.OAUTH2_CLIENT_INVALID));
  }

  /**
   * 分页查询应用列表。
   *
   * @param status 应用状态
   * @param keyword 搜索关键字
   * @param pageNum 页码
   * @param pageSize 每页大小
   * @return 分页结果
   */
  public com.njydsz.common.core.response.PageResponse<List<OAuth2Application>> page(
      OAuth2Application.ApplicationStatus status, String keyword, int pageNum, int pageSize) {
    return applicationRepository.page(status, keyword, pageNum, pageSize);
  }

  /**
   * 查询所有启用状态的应用。
   *
   * @return 应用列表
   */
  public List<OAuth2Application> findAllEnabled() {
    return applicationRepository.findAllEnabled();
  }

  /**
   * 验证 clientSecret 是否匹配。
   *
   * @param clientId 客户端 ID
   * @param clientSecret 明文密钥
   * @return true 表示匹配
   */
  public boolean validateClientSecret(String clientId, String clientSecret) {
    OAuth2Application application = applicationRepository.findByClientId(clientId)
        .orElse(null);
    if (application == null) {
      return false;
    }
    return passwordEncoder.matches(clientSecret, application.clientSecret());
  }

  /**
   * 生成唯一的 clientId。
   *
   * @return clientId
   */
  private String generateClientId() {
    byte[] bytes = new byte[CLIENT_ID_RANDOM_BYTES];
    secureRandom.nextBytes(bytes);
    return CLIENT_ID_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * 生成明文 clientSecret。
   *
   * @return 明文密钥
   */
  private String generateClientSecret() {
    byte[] bytes = new byte[CLIENT_SECRET_RANDOM_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * 获取当前用户 ID。
   *
   * @return 用户 ID
   */
  private String getCurrentUserId() {
    try {
      return RequestContext.getUserId();
    } catch (Exception e) {
      return "SYSTEM";
    }
  }
}
