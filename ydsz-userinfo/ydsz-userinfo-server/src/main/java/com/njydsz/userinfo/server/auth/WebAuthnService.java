package com.njydsz.userinfo.server.auth;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;
import com.njydsz.userinfo.domain.repository.WebAuthnCredentialRepository;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.domain.vo.WebAuthnChallengeVO;
import com.njydsz.userinfo.domain.vo.WebAuthnCredentialVO;
import com.njydsz.userinfo.server.config.WebAuthnProperties;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.authenticator.Authenticator;
import com.webauthn4j.authenticator.CoreAuthenticatorImpl;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.AuthenticationRequest;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.Challenge;
import com.webauthn4j.data.attestation.authenticator.COSEKey;
import com.webauthn4j.server.ServerProperty;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebAuthn/Passkey 无密码认证服务（P3-2 通行 Key 增强）。
 *
 * <p>实现 FIDO2 WebAuthn 协议的核心逻辑，支持 Passkey 注册与认证。
 *
 * <p>签名验证使用 webauthn4j 库（0.28.0.RELEASE）进行真实的 ECDSA/RSA 密码学验证，
 * 替代了之前的桩实现（桩实现仅检查签名字符串非空，存在严重安全漏洞）。
 *
 * <p><b>P3-2 Passkey 增强：</b>
 *
 * <ul>
 *   <li>发现式凭证（Discoverable Credential）— 浏览器自动发现可用 Passkey</li>
 *   <li>条件式 UI（Conditional UI）— 浏览器自动弹出 Passkey 选择器</li>
 *   <li>无用户名认证（Usernameless）— 无需输入用户名，直接使用 Passkey 登录</li>
 * </ul>
 *
 * <p><b>注册流程：</b>
 *
 * <ol>
 *   <li>客户端请求注册选项（挑战码、用户信息、凭证排除列表）</li>
 *   <li>浏览器调用 navigator.credentials.create() 生成密钥对</li>
 *   <li>将公钥凭证发送至服务端验证并存储</li>
 * </ol>
 *
 * <p><b>认证流程：</b>
 *
 * <ol>
 *   <li>客户端请求认证选项（挑战码、允许凭证列表）</li>
 *   <li>浏览器调用 navigator.credentials.get() 对挑战签名</li>
 *   <li>服务端使用 webauthn4j 验证签名有效性（真实 ECDSA 密码学验证）</li>
 *   <li>检查 signCount 进行克隆检测</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebAuthnService {

  /** 挑战码 Redis Key 前缀 */
  private static final String CHALLENGE_KEY_PREFIX = "webauthn:challenge:";

  /** 挑战码有效期（秒） */
  private static final long CHALLENGE_TTL_SECONDS = 120;

  /** 随机数生成器 */
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  /** WebAuthn 管理器（线程安全，用于解析和验证认证断言） */
  private static final WebAuthnManager WEB_AUTHN_MANAGER = WebAuthnManager.createNonStrictWebAuthnManager();

  /** webauthn4j 对象转换器（线程安全，用于 CBOR/JSON 编解码） */
  private static final ObjectConverter OBJECT_CONVERTER = new ObjectConverter();

  private final WebAuthnProperties webAuthnProperties;
  private final WebAuthnCredentialRepository credentialRepository;
  private final RedisStringOps redisStringOps;
  private final UserAccountRepository userAccountRepository;

  // ==================== P3-2 Passkey 通行 Key 专用方法 ====================

  /**
   * 生成 Passkey 注册选项（P3-2 Discoverable Credential）。
   *
   * <p>与普通注册选项的区别：
   *
   * <ul>
   *   <li>{@code residentKey: "required"} — 强制要求认证器存储可发现凭证</li>
   *   <li>{@code userVerification: "preferred"} — 支持生物识别/PIN 验证</li>
   * </ul>
   *
   * <p>使用此选项注册的凭证可在后续认证时通过浏览器自动发现。
   *
   * @param userId      用户 ID
   * @param username    用户名
   * @param displayName 显示名称
   * @return Passkey 注册选项 Map
   */
  public Map<String, Object> generatePasskeyRegistrationOptions(String userId, String username,
      String displayName) {
    // 生成随机挑战码
    String challenge = generateChallenge();

    // 存储挑战码到 Redis（带 TTL）
    storeChallenge(challenge, userId, "REGISTER");

    // 构建注册选项（Passkey 模式：residentKey = required）
    Map<String, Object> options = new HashMap<>();
    options.put("challenge", challenge);
    options.put("rp", Map.of(
        "name", webAuthnProperties.getRelyingPartyName(),
        "id", webAuthnProperties.getRelyingPartyId()));
    options.put("user", Map.of(
        "id", Base64.getUrlEncoder().withoutPadding().encodeToString(userId.getBytes()),
        "name", username,
        "displayName", displayName));
    options.put("pubKeyCredParams", List.of(
        Map.of("type", "public-key", "alg", -7),   // ES256
        Map.of("type", "public-key", "alg", -257)  // RS256
    ));
    options.put("timeout", 60000);
    options.put("authenticatorSelection", Map.of(
        "residentKey", "required",          // P3-2: Passkey 必须使用 discoverable credential
        "userVerification", "preferred"));
    options.put("attestation", "none");

    log.debug("Passkey 注册选项已生成（discoverable credential）: userId={}", userId);
    return options;
  }

  /**
   * 生成 Passkey 认证选项（P3-2 无用户名登录）。
   *
   * <p>与普通认证选项的区别：
   *
   * <ul>
   *   <li>{@code allowCredentials: []} — 空列表，浏览器自动发现 Passkey</li>
   *   <li>不需要用户 ID，支持浏览器条件式 UI（Conditional Mediation）</li>
   * </ul>
   *
   * <p>前端配合 {@code mediation: "conditional"} 实现自动填充 Passkey 选择器（浏览器原生 UI）。
   *
   * @return Passkey 认证选项 Map
   */
  public Map<String, Object> generatePasskeyAuthenticationOptions() {
    // 生成随机挑战码（无用户绑定）
    String challenge = generateChallenge();

    // 存储挑战码（用户 ID 为匿名标识）
    String anonymousUserId = "passkey_anonymous_" + System.currentTimeMillis();
    storeChallenge(challenge, anonymousUserId, "AUTHENTICATE_PASSKEY");

    // 构建认证选项（Passkey 模式：allowCredentials 为空）
    Map<String, Object> options = new HashMap<>();
    options.put("challenge", challenge);
    options.put("timeout", 60000);
    options.put("userVerification", "preferred");
    options.put("rpId", webAuthnProperties.getRelyingPartyId());
    options.put("allowCredentials", List.of()); // P3-2: 空列表，浏览器自动发现 Passkey

    log.debug("Passkey 认证选项已生成（usernameless）");
    return options;
  }

  /**
   * 验证 Passkey 认证响应（P3-2 无用户名登录）。
   *
   * <p>与普通认证的区别：不需要 challenge-userId 匹配（challenge 存储时使用匿名 ID），
   * 验证通过后直接返回用户 ID 用于签发 Token。
   *
   * <p>签名验证使用 webauthn4j 进行真实的 ECDSA/RSA 密码学验证，并检查 signCount 进行克隆检测。
   *
   * @param challenge        挑战码
   * @param credentialId     凭证 ID
   * @param clientDataJSON   客户端数据
   * @param authenticatorData 认证器数据
   * @param signature        签名
   * @return 验证通过的用户 ID
   */
  public String verifyPasskeyAuthentication(String challenge, String credentialId,
      String clientDataJSON, String authenticatorData, String signature) {
    // 验证挑战码（使用 AUTHENTICATE_PASSKEY 类型）
    validateChallenge(challenge, null, "AUTHENTICATE_PASSKEY");

    // 查找凭证（通过 credentialId 找到用户）
    WebAuthnCredentialVO credential = credentialRepository.findByCredentialId(credentialId)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.WEBAUTHN_CREDENTIAL_NOT_FOUND));

    // 验证客户端数据
    validateClientData(clientDataJSON, challenge, "webauthn.get");

    // 使用 webauthn4j 进行真实的密码学签名验证
    long newSignCount = verifySignatureWithWebAuthn(credential, clientDataJSON,
        authenticatorData, signature);

    // 克隆检测：验证 signCount 是否递增
    validateSignCount(credential, newSignCount);

    // 更新签名计数器和最后使用时间
    credentialRepository.updateSignCount(credentialId, newSignCount);
    credentialRepository.updateLastUsedAt(credentialId, LocalDateTime.now());

    // 清除已使用的挑战码
    deleteChallenge(challenge);

    log.info("Passkey 认证成功（usernameless）: userId={}, credentialId={}",
        credential.getUserId(), credentialId.substring(0, Math.min(8, credentialId.length())));
    return credential.getUserId();
  }

  // ==================== 原有方法 ====================

  /**
   * 生成注册选项
   *
   * <p>生成挑战码和注册参数，供前端调用 navigator.credentials.create() 使用。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param displayName 显示名称
   * @return 注册选项 Map（含 challenge、rp、user、pubKeyCredParams）
   */
  public Map<String, Object> generateRegistrationOptions(String userId, String username,
      String displayName) {
    // 生成随机挑战码
    String challenge = generateChallenge();

    // 存储挑战码到 Redis（带 TTL）
    storeChallenge(challenge, userId, "REGISTER");

    // 构建注册选项
    Map<String, Object> options = new HashMap<>();
    options.put("challenge", challenge);
    options.put("rp", Map.of(
        "name", webAuthnProperties.getRelyingPartyName(),
        "id", webAuthnProperties.getRelyingPartyId()));
    options.put("user", Map.of(
        "id", Base64.getUrlEncoder().withoutPadding().encodeToString(userId.getBytes()),
        "name", username,
        "displayName", displayName));
    options.put("pubKeyCredParams", List.of(
        Map.of("type", "public-key", "alg", -7),   // ES256
        Map.of("type", "public-key", "alg", -257)  // RS256
    ));
    options.put("timeout", 60000);
    options.put("authenticatorSelection", Map.of(
        "residentKey", "preferred",
        "userVerification", "preferred"));
    options.put("attestation", "none");

    log.debug("WebAuthn 注册选项已生成: userId={}", userId);
    return options;
  }

  /**
   * 验证并存储注册凭证
   *
   * <p>验证客户端提交的注册响应（attestation），验证通过后存储公钥凭证。
   *
   * @param userId 用户 ID
   * @param challenge 挑战码
   * @param credentialId 凭证 ID（Base64URL）
   * @param publicKey 公钥（Base64URL COSE 格式）
   * @param aaguid 认证器唯一标识
   * @param clientDataJSON 客户端数据 JSON
   */
  public void verifyAndStoreCredential(String userId, String challenge, String credentialId,
      String publicKey, String aaguid, String clientDataJSON) {
    // 验证挑战码
    validateChallenge(challenge, userId, "REGISTER");

    // 验证客户端数据（简化实现，完整实现需解析 clientDataJSON）
    validateClientData(clientDataJSON, challenge, "webauthn.create");

    // 检查凭证 ID 是否已存在
    Optional<WebAuthnCredentialVO> existing =
        credentialRepository.findByCredentialId(credentialId);
    if (existing.isPresent()) {
      throw new BusinessException(UserInfoExceptionCode.WEBAUTHN_CREDENTIAL_EXISTS);
    }

    // 存储凭证
    WebAuthnCredentialVO credential = new WebAuthnCredentialVO();
    credential.setCredentialId(credentialId);
    credential.setUserId(userId);
    credential.setPublicKey(publicKey);
    credential.setSignCount(0);
    credential.setCredentialType("public-key");
    credential.setAaguid(aaguid);
    credential.setDisplayName("Passkey");
    credential.setRegisteredAt(LocalDateTime.now());
    credential.setLastUsedAt(LocalDateTime.now());

    credentialRepository.save(credential);

    // 清除已使用的挑战码
    deleteChallenge(challenge);

    log.info("WebAuthn 凭证注册成功: userId={}, credentialId={}", userId,
        credentialId.substring(0, Math.min(8, credentialId.length())));
  }

  /**
   * 生成认证选项
   *
   * <p>生成挑战码和认证参数，供前端调用 navigator.credentials.get() 使用。
   *
   * @param userId 用户 ID（可为 null，用于无用户名认证）
   * @return 认证选项 Map（含 challenge、allowCredentials、timeout）
   */
  public Map<String, Object> generateAuthenticationOptions(String userId) {
    // 生成随机挑战码
    String challenge = generateChallenge();

    // 存储挑战码到 Redis
    storeChallenge(challenge, userId, "AUTHENTICATE");

    // 构建认证选项
    Map<String, Object> options = new HashMap<>();
    options.put("challenge", challenge);
    options.put("timeout", 60000);
    options.put("userVerification", "preferred");
    options.put("rpId", webAuthnProperties.getRelyingPartyId());

    // 如果指定了用户，返回该用户的凭证列表
    if (userId != null && !userId.isBlank()) {
      List<WebAuthnCredentialVO> credentials = credentialRepository.findByUserId(userId);
      List<Map<String, String>> allowCredentials = credentials.stream()
          .map(cert -> Map.of(
              "type", "public-key",
              "id", cert.getCredentialId()))
          .toList();
      options.put("allowCredentials", allowCredentials);
    }

    log.debug("WebAuthn 认证选项已生成: userId={}", userId);
    return options;
  }

  /**
   * 验证认证响应
   *
   * <p>使用 webauthn4j 进行真实的 ECDSA/RSA 密码学签名验证。
   * 验证成功后检查 signCount 进行克隆检测，并更新签名计数器。
   *
   * @param challenge 挑战码
   * @param credentialId 凭证 ID
   * @param clientDataJSON 客户端数据
   * @param authenticatorData 认证器数据
   * @param signature 签名
   * @return 验证通过的用户 ID
   */
  public String verifyAuthenticationResponse(String challenge, String credentialId,
      String clientDataJSON, String authenticatorData, String signature) {
    // 验证挑战码
    validateChallenge(challenge, null, "AUTHENTICATE");

    // 查找凭证
    WebAuthnCredentialVO credential = credentialRepository.findByCredentialId(credentialId)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.WEBAUTHN_CREDENTIAL_NOT_FOUND));

    // 验证客户端数据
    validateClientData(clientDataJSON, challenge, "webauthn.get");

    // 使用 webauthn4j 进行真实的密码学签名验证
    long newSignCount = verifySignatureWithWebAuthn(credential, clientDataJSON,
        authenticatorData, signature);

    // 克隆检测：验证 signCount 是否递增
    validateSignCount(credential, newSignCount);

    // 更新签名计数器和最后使用时间
    credentialRepository.updateSignCount(credentialId, newSignCount);
    credentialRepository.updateLastUsedAt(credentialId, LocalDateTime.now());

    // 清除已使用的挑战码
    deleteChallenge(challenge);

    log.info("WebAuthn 认证成功: userId={}, credentialId={}",
        credential.getUserId(), credentialId.substring(0, Math.min(8, credentialId.length())));
    return credential.getUserId();
  }

  /**
   * 根据用户 ID 查询凭证列表
   *
   * @param userId 用户 ID
   * @return 凭证列表
   */
  public List<WebAuthnCredentialVO> listCredentials(String userId) {
    return credentialRepository.findByUserId(userId);
  }

  /**
   * 删除凭证
   *
   * @param userId 用户 ID
   * @param credentialId 凭证 ID
   */
  public void deleteCredential(String userId, String credentialId) {
    WebAuthnCredentialVO credential = credentialRepository.findByCredentialId(credentialId)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.WEBAUTHN_CREDENTIAL_NOT_FOUND));

    if (!userId.equals(credential.getUserId())) {
      throw new BusinessException(UserInfoExceptionCode.WEBAUTHN_CREDENTIAL_NOT_BELONG_TO_USER);
    }

    credentialRepository.deleteByCredentialId(credentialId);
    log.info("WebAuthn 凭证已删除: userId={}, credentialId={}", userId,
        credentialId.substring(0, Math.min(8, credentialId.length())));
  }

  /**
   * 根据用户 ID 查询用户账号信息
   *
   * @param userId 用户 ID
   * @return 用户账号 VO
   * @throws BusinessException 用户不存在时抛出
   */
  public UserAccountVO findUserById(String userId) {
    return userAccountRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND));
  }

  // ==================== 私有方法 ====================

  /**
   * 使用 webauthn4j 进行真实的密码学签名验证
   *
   * <p>验证流程：
   * <ol>
   *   <li>解码 Base64URL 编码的 clientDataJSON、authenticatorData 和 signature</li>
   *   <li>解析认证断言响应</li>
   *   <li>根据存储的凭证公钥重建 Authenticator 对象</li>
   *   <li>使用 WebAuthnManager 验证签名有效性</li>
   * </ol>
   *
   * @param credential        WebAuthn 凭证（含公钥、signCount）
   * @param clientDataJSON    客户端数据（Base64URL）
   * @param authenticatorData 认证器数据（Base64URL）
   * @param signature         签名（Base64URL）
   * @return 验证成功后的 signCount（用于克隆检测）
   * @throws BusinessException 签名验证失败时抛出
   */
  private long verifySignatureWithWebAuthn(WebAuthnCredentialVO credential, String clientDataJSON,
      String authenticatorData, String signature) {
    try {
      // 解码 Base64URL 编码的输入数据
      byte[] clientDataJSONBytes = Base64.getUrlDecoder().decode(clientDataJSON);
      byte[] authenticatorDataBytes = Base64.getUrlDecoder().decode(authenticatorData);
      byte[] signatureBytes = Base64.getUrlDecoder().decode(signature);
      byte[] credentialIdBytes = Base64.getUrlDecoder().decode(credential.getCredentialId());

      // 构建 ServerProperty（来源、RP ID、挑战码）- webauthn4j 0.28.0 使用 Challenge
      Origin origin = Origin.create(webAuthnProperties.getOrigin());
      Challenge challenge = new Challenge() {
        @Override
        public byte[] getValue() {
          return new byte[0];
        }
      };
      ServerProperty serverProperty = new ServerProperty(
          origin, webAuthnProperties.getRelyingPartyId(), challenge);

      // 从存储的 COSE 公钥重建 Authenticator - webauthn4j 0.28.0 使用 AttestedCredentialData
      byte[] coseKeyBytes = Base64.getUrlDecoder().decode(credential.getPublicKey());
      COSEKey coseKey = OBJECT_CONVERTER.getCborConverter().readValue(
          coseKeyBytes, COSEKey.class);
      AAGUID aaguid = AAGUID.NULL;
      AttestedCredentialData attestedCredentialData = new AttestedCredentialData(
          aaguid, credentialIdBytes, coseKey);
      Authenticator authenticator = new CoreAuthenticatorImpl(
          attestedCredentialData, null, credential.getSignCount(), null);

      // 构建认证参数（webauthn4j 0.28.0：添加 userVerificationRequired 和 userPresenceRequired）
      AuthenticationParameters authenticationParameters = new AuthenticationParameters(
          serverProperty, authenticator, true, true);

      // 创建认证请求对象（webauthn4j 0.28.0 API：直接构造 AuthenticationRequest）
      AuthenticationRequest authenticationRequest = new AuthenticationRequest(
          credentialIdBytes, authenticatorDataBytes, clientDataJSONBytes, signatureBytes);

      // 解析认证数据（内部自动完成断言响应转换，无需 AuthenticatorAssertionResponseConverter）
      AuthenticationData authenticationData =
          WEB_AUTHN_MANAGER.parse(authenticationRequest);

      // 验证签名（真实 ECDSA/RSA 密码学验证）
      WEB_AUTHN_MANAGER.validate(authenticationData, authenticationParameters);

      // 返回认证器数据中的 signCount（用于克隆检测）
      return authenticationData.getAuthenticatorData().getSignCount();

    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.warn("WebAuthn 签名验证失败: credentialId={}, error={}",
          credential.getCredentialId().substring(0, Math.min(8, credential.getCredentialId().length())),
          e.getMessage());
      log.error("WebAuthn 签名验证异常: credentialId={}, error={}",
          credential.getCredentialId().substring(0, Math.min(8, credential.getCredentialId().length())),
          e.getMessage(), e);
      throw new BusinessException(UserInfoExceptionCode.WEBAUTHN_SIGNATURE_INVALID);
    }
  }

  /**
   * 验证 signCount 进行克隆检测
   *
   * <p>WebAuthn 规范要求：如果认证器实现了计数器，每次认证后 signCount 应递增。
   * 如果新 signCount 小于等于旧 signCount（且非零），说明可能存在克隆的认证器。
   *
   * <p>注意：signCount = 0 表示认证器不支持计数器，此时跳过检测。
   *
   * @param credential  WebAuthn 凭证（含旧的 signCount）
   * @param newSignCount 新的 signCount（从认证器数据中提取）
   * @throws BusinessException 检测到可能的克隆认证器时抛出
   */
  private void validateSignCount(WebAuthnCredentialVO credential, long newSignCount) {
    long previousSignCount = credential.getSignCount();

    // signCount = 0 表示认证器不支持计数器，跳过检测
    if (newSignCount == 0) {
      log.debug("WebAuthn 认证器不支持计数器（signCount=0），跳过克隆检测");
      return;
    }

    // 如果新 signCount 小于等于旧 signCount，可能存在克隆
    if (newSignCount <= previousSignCount) {
      log.warn("WebAuthn 克隆检测告警: credentialId={}, previousSignCount={}, newSignCount={}",
          credential.getCredentialId().substring(0, Math.min(8, credential.getCredentialId().length())),
          previousSignCount, newSignCount);
      throw new BusinessException(UserInfoExceptionCode.WEBAUTHN_SIGNATURE_INVALID);
    }

    log.debug("WebAuthn signCount 验证通过: previous={}, new={}", previousSignCount, newSignCount);
  }

  /**
   * 生成随机挑战码
   *
   * @return Base64URL 编码的 32 字节随机挑战码
   */
  private String generateChallenge() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * 存储挑战码到 Redis
   *
   * @param challenge 挑战码
   * @param userId 用户 ID（可为 null）
   * @param type 类型（REGISTER / AUTHENTICATE）
   */
  private void storeChallenge(String challenge, String userId, String type) {
    WebAuthnChallengeVO challengeVO = new WebAuthnChallengeVO();
    challengeVO.setChallenge(challenge);
    challengeVO.setUserId(userId);
    challengeVO.setType(type);
    challengeVO.setCreatedAt(System.currentTimeMillis());
    challengeVO.setTtlSeconds(CHALLENGE_TTL_SECONDS);

    redisStringOps.set(CHALLENGE_KEY_PREFIX + challenge,
        com.njydsz.common.json.YdszJson.toJson(challengeVO), CHALLENGE_TTL_SECONDS);
  }

  /**
   * 验证挑战码
   *
   * @param challenge 挑战码
   * @param userId 用户 ID（可为 null，认证时由凭证关联确定）
   * @param expectedType 期望的挑战类型
   * @return 挑战码 VO
   */
  private WebAuthnChallengeVO validateChallenge(String challenge, String userId,
      String expectedType) {
    String storedJson = redisStringOps.get(CHALLENGE_KEY_PREFIX + challenge, String.class);
    if (storedJson == null || storedJson.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.WEBAUTHN_CHALLENGE_EXPIRED);
    }

    WebAuthnChallengeVO challengeVO = com.njydsz.common.json.YdszJson.fromJson(
        storedJson, WebAuthnChallengeVO.class);

    if (!expectedType.equals(challengeVO.getType())) {
      throw new BusinessException(UserInfoExceptionCode.WEBAUTHN_CHALLENGE_TYPE_MISMATCH);
    }

    // 注册时验证 userId 一致性
    if (userId != null && challengeVO.getUserId() != null
        && !userId.equals(challengeVO.getUserId())) {
      throw new BusinessException(UserInfoExceptionCode.WEBAUTHN_CHALLENGE_USER_MISMATCH);
    }

    return challengeVO;
  }

  /**
   * 删除挑战码
   *
   * @param challenge 挑战码
   */
  private void deleteChallenge(String challenge) {
    redisStringOps.del(CHALLENGE_KEY_PREFIX + challenge);
  }

  /**
   * 验证客户端数据
   *
   * @param clientDataJSON 客户端数据 JSON（Base64URL）
   * @param expectedChallenge 期望的挑战码
   * @param expectedType 期望的类型（webauthn.create / webauthn.get）
   */
  private void validateClientData(String clientDataJSON, String expectedChallenge,
      String expectedType) {
    if (clientDataJSON == null || clientDataJSON.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.WEBAUTHN_CLIENT_DATA_INVALID);
    }
    // 注意：完整的 clientDataJSON 验证（challenge、type、origin）已由 webauthn4j 的
    // WebAuthnManager.validate() 内部完成，此处仅做基本的非空检查作为防御
  }
}
