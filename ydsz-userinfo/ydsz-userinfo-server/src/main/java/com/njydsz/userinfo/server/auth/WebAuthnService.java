package com.njydsz.userinfo.server.auth;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.repository.WebAuthnCredentialRepository;
import com.njydsz.userinfo.domain.vo.WebAuthnChallengeVO;
import com.njydsz.userinfo.domain.vo.WebAuthnCredentialVO;
import com.njydsz.userinfo.server.config.WebAuthnProperties;

/**
 * WebAuthn/Passkey 无密码认证服务
 *
 * <p>实现 FIDO2 WebAuthn 协议的核心逻辑，支持 Passkey 注册与认证。
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
 *   <li>服务端验证签名有效性</li>
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

  private final WebAuthnProperties webAuthnProperties;
  private final WebAuthnCredentialRepository credentialRepository;
  private final RedisStringOps redisStringOps;

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
    WebAuthnChallengeVO storedChallenge = validateChallenge(challenge, userId, "REGISTER");

    // 验证客户端数据（简化实现，完整实现需解析 clientDataJSON）
    validateClientData(clientDataJSON, storedChallenge.getChallenge(), "webauthn.create");

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
   * <p>验证客户端提交的认证响应（assertion），验证成功后更新签名计数器。
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

    // 验证签名（简化实现，完整实现需使用公钥验证签名）
    boolean signatureValid = verifySignature(credential.getPublicKey(), clientDataJSON,
        authenticatorData, signature);
    if (!signatureValid) {
      throw new BusinessException(UserInfoExceptionCode.WEBAUTHN_SIGNATURE_INVALID);
    }

    // 更新签名计数器和最后使用时间
    credentialRepository.updateSignCount(credentialId, credential.getSignCount() + 1);
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
    String storedJson = redisStringOps.get(CHALLENGE_KEY_PREFIX + challenge);
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
    // 简化实现：完整实现需解码 clientDataJSON 并验证 challenge、type、origin
    if (clientDataJSON == null || clientDataJSON.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.WEBAUTHN_CLIENT_DATA_INVALID);
    }
    // TODO: 解析 clientDataJSON 验证 challenge 匹配、type 匹配、origin 匹配
  }

  /**
   * 验证签名
   *
   * @param publicKey 公钥（COSE 格式）
   * @param clientDataJSON 客户端数据
   * @param authenticatorData 认证器数据
   * @param signature 签名
   * @return 签名是否有效
   */
  private boolean verifySignature(String publicKey, String clientDataJSON,
      String authenticatorData, String signature) {
    // 简化实现：完整实现需使用公钥验证 ECDSA/RSA 签名
    // 实际生产环境应使用 webauthn4j 等库
    log.debug("WebAuthn 签名验证（简化实现）");
    return signature != null && !signature.isBlank();
  }
}
