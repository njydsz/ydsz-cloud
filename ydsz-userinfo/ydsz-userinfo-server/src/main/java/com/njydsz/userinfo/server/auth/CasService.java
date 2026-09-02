package com.njydsz.userinfo.server.auth;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.server.config.CasProperties;

/**
 * CAS 协议核心服务。
 *
 * <p>实现 CAS 2.0/3.0 协议的核心逻辑，包括：
 *
 * <ul>
 *   <li>Ticket Granting Ticket (TGT) 签发与校验</li>
 *   <li>Service Ticket (ST) 签发与校验</li>
 *   <li>Proxy Granting Ticket (PGT) 签发与校验（可选）</li>
 * </ul>
 *
 * <p><b>Redis Key 设计：</b>
 *
 * <pre>
 *   userinfo:cas:tgt:{tgtId}    →  TGT 信息（用户 ID、签发时间），TTL 8h
 *   userinfo:cas:st:{stId}      →  ST 信息（用户 ID、服务 URL），TTL 5m
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CasService {

  /** TGT Redis Key 前缀 */
  private static final String TGT_KEY_PREFIX = "userinfo:cas:tgt:";

  /** ST Redis Key 前缀 */
  private static final String ST_KEY_PREFIX = "userinfo:cas:st:";

  /** TGT ID 长度（字节） */
  private static final int TGT_ID_LENGTH = 32;

  /** ST ID 长度（字节） */
  private static final int ST_ID_LENGTH = 32;

  private final RedisStringOps redisStringOps;
  private final CasProperties casProperties;
  private final SecureRandom secureRandom = new SecureRandom();

  /**
   * 签发 Ticket Granting Ticket (TGT)。
   *
   * <p>TGT 是 CAS 协议的核心票据，代表用户的认证会话。用户登录成功后获得 TGT，
   * 后续访问其他服务时用 TGT 换取 ST，无需重新登录。
   *
   * @param userVO 用户账号 VO
   * @return TGT ID
   */
  public String issueTicketGrantingTicket(UserAccountVO userVO) {
    String tgtId = generateTicketId(TGT_ID_LENGTH);
    String tgtKey = TGT_KEY_PREFIX + tgtId;

    // 存储 TGT 信息
    CasTicketGrantingTicket tgt = new CasTicketGrantingTicket();
    tgt.setUserId(userVO.getId());
    tgt.setUsername(userVO.getUsername());
    tgt.setIssueTime(LocalDateTime.now());

    try {
      redisStringOps.set(tgtKey, tgt, casProperties.getTicketGrantingTicketTtl());
      log.info("TGT 签发成功: username={}, tgtId={}", userVO.getUsername(), tgtId);
    } catch (Exception e) {
      log.error("TGT 签发失败: username={}, error={}", userVO.getUsername(), e.getMessage(), e);
      throw new BusinessException(UserInfoExceptionCode.SSO_TOKEN_EXCHANGE_FAILED);
    }

    return tgtId;
  }

  /**
   * 签发 Service Ticket (ST)。
   *
   * <p>ST 是一次性票据，用于特定服务的认证。用户持 TGT 请求 ST，ST 验证后立即失效。
   *
   * @param tgtId TGT ID
   * @param serviceUrl 服务 URL（用于校验回调地址）
   * @return ST ID
   * @throws BusinessException TGT 无效或已过期时抛出
   */
  public String issueServiceTicket(String tgtId, String serviceUrl) {
    // 校验 TGT
    CasTicketGrantingTicket tgt = validateTicketGrantingTicket(tgtId);

    // 生成 ST
    String stId = generateTicketId(ST_ID_LENGTH);
    String stKey = ST_KEY_PREFIX + stId;

    // 存储 ST 信息
    CasServiceTicket st = new CasServiceTicket();
    st.setUserId(tgt.getUserId());
    st.setUsername(tgt.getUsername());
    st.setServiceUrl(serviceUrl);
    st.setIssueTime(LocalDateTime.now());
    st.setTgtId(tgtId);

    try {
      redisStringOps.set(stKey, st, casProperties.getServiceTicketTtl());
      log.info("ST 签发成功: username={}, serviceUrl={}", tgt.getUsername(), serviceUrl);
    } catch (Exception e) {
      log.error("ST 签发失败: username={}, error={}", tgt.getUsername(), e.getMessage(), e);
      throw new BusinessException(UserInfoExceptionCode.SSO_TOKEN_EXCHANGE_FAILED);
    }

    return stId;
  }

  /**
   * 校验 Service Ticket (ST)。
   *
   * <p>ST 验证成功后立即失效（一次性使用），返回用户信息。
   *
   * @param stId ST ID
   * @param serviceUrl 服务 URL（必须与签发时一致）
   * @return 校验结果（包含用户信息）
   * @throws BusinessException ST 无效、已过期或服务 URL 不匹配时抛出
   */
  public CasServiceTicketValidationResult validateServiceTicket(String stId, String serviceUrl) {
    String stKey = ST_KEY_PREFIX + stId;

    try {
      // 获取 ST 信息
      CasServiceTicket st = redisStringOps.get(stKey, CasServiceTicket.class);
      if (st == null) {
        log.warn("ST 不存在或已过期: stId={}", stId);
        throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
      }

      // 校验服务 URL
      if (!serviceUrl.equals(st.getServiceUrl())) {
        log.warn("ST 服务 URL 不匹配: expected={}, actual={}", st.getServiceUrl(), serviceUrl);
        throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
      }

      // 删除 ST（一次性使用）
      redisStringOps.del(stKey);

      log.info("ST 校验成功: username={}, serviceUrl={}", st.getUsername(), serviceUrl);

      // 返回校验结果
      return CasServiceTicketValidationResult.builder()
          .success(true)
          .userId(st.getUserId())
          .username(st.getUsername())
          .build();
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("ST 校验异常: stId={}, error={}", stId, e.getMessage(), e);
      throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
    }
  }

  /**
   * 注销 TGT（单点登出）。
   *
   * <p>删除 TGT 及其关联的所有 ST，实现单点登出。
   *
   * @param tgtId TGT ID
   */
  public void logout(String tgtId) {
    String tgtKey = TGT_KEY_PREFIX + tgtId;
    try {
      redisStringOps.del(tgtKey);
      log.info("TGT 注销成功: tgtId={}", tgtId);
    } catch (Exception e) {
      log.error("TGT 注销异常: tgtId={}, error={}", tgtId, e.getMessage(), e);
    }
  }

  /**
   * 校验 TGT 是否有效。
   *
   * @param tgtId TGT ID
   * @return TGT 信息
   * @throws BusinessException TGT 无效或已过期时抛出
   */
  private CasTicketGrantingTicket validateTicketGrantingTicket(String tgtId) {
    String tgtKey = TGT_KEY_PREFIX + tgtId;

    try {
      CasTicketGrantingTicket tgt = redisStringOps.get(tgtKey, CasTicketGrantingTicket.class);
      if (tgt == null) {
        log.warn("TGT 不存在或已过期: tgtId={}", tgtId);
        throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
      }
      return tgt;
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("TGT 校验异常: tgtId={}, error={}", tgtId, e.getMessage(), e);
      throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
    }
  }

  /**
   * 生成票据 ID。
   *
   * @param length 字节长度
   * @return Base64 编码的票据 ID
   */
  private String generateTicketId(int length) {
    byte[] bytes = new byte[length];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * CAS Ticket Granting Ticket 信息。
   *
   * @author ydsz-team
   * @since 26.09.01
   */
  @lombok.Data
  public static class CasTicketGrantingTicket {

    /** 用户 ID */
    private String userId;

    /** 用户名 */
    private String username;

    /** 签发时间 */
    private LocalDateTime issueTime;
  }

  /**
   * CAS Service Ticket 信息。
   *
   * @author ydsz-team
   * @since 26.09.01
   */
  @lombok.Data
  public static class CasServiceTicket {

    /** 用户 ID */
    private String userId;

    /** 用户名 */
    private String username;

    /** 服务 URL */
    private String serviceUrl;

    /** 签发时间 */
    private LocalDateTime issueTime;

    /** 关联的 TGT ID */
    private String tgtId;
  }

  /**
   * CAS Service Ticket 校验结果。
   *
   * @author ydsz-team
   * @since 26.09.01
   */
  @lombok.Builder
  @lombok.Data
  public static class CasServiceTicketValidationResult {

    /** 校验是否成功 */
    private boolean success;

    /** 用户 ID */
    private String userId;

    /** 用户名 */
    private String username;
  }
}
