package com.njydsz.userinfo.server.device;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.enums.DeviceType;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.server.auth.SessionManager;

/**
 * 设备会话管理服务（P3-2）。
 *
 * <p>提供设备列表查询、单设备下线、设备置信标记等能力。
 *
 * <p><b>Redis Key 设计：</b>
 *
 * <pre>
 *   userinfo:device:trusted:{userId}  →  Set&lt;deviceFingerprint&gt;  用户信任设备指纹集合
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceSessionService {
  /** User-Agent 截断长度 */
  private static final int USER_AGENT_MAX_LENGTH = 30;


  /** 信任设备 Redis Key 前缀 */
  private static final String TRUSTED_DEVICE_KEY_PREFIX = "userinfo:device:trusted:";

  /** 日期格式化 */
  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final SessionManager sessionManager;

  /**
   * 查询当前用户的所有活跃设备会话。
   *
   * @param userId 用户 ID
   * @return 设备会话列表（按登录时间倒序）
   */
  public List<DeviceSessionVO> listMyDevices(String userId) {
    if (userId == null || userId.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.PARAM_INVALID);
    }

    List<Map<String, String>> sessionDetails = sessionManager.listSessionDeviceDetails(userId);
    List<DeviceSessionVO> result = new ArrayList<>();

    for (Map<String, String> details : sessionDetails) {
      DeviceSessionVO vo = new DeviceSessionVO();
      vo.setSessionId(details.get("sessionId"));
      vo.setDeviceType(details.getOrDefault("deviceType", "unknown"));
      vo.setDeviceTypeDesc(resolveDeviceTypeDesc(details.get("deviceType")));
      vo.setLoginIp(details.get("loginIp"));
      vo.setLoginTime(parseTime(details.get("loginTime")));
      // 最后活跃时间：当前实现以 loginTime 为准（可按需扩展心跳机制）
      vo.setLastActiveTime(parseTime(details.get("loginTime")));
      vo.setDeviceFingerprint(maskUserAgent(details.get("userAgent")));
      result.add(vo);
    }

    return result;
  }

  /**
   * 下线指定设备会话（强制登出）。
   *
   * <p>吊销该 session 的 access_token 与 refresh_token，从全局/分端索引中移除。
   *
   * @param userId 用户 ID（用于安全校验）
   * @param sessionId 会话 ID（access_token 前 8 位 + ****）
   * @return true 下线成功
   */
  public boolean revokeDevice(String userId, String sessionId) {
    if (userId == null || userId.isBlank() || sessionId == null || sessionId.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.PARAM_INVALID);
    }

    // 查找匹配的 token（通过前缀匹配）
    String tokenPrefix = sessionId.replace("****", "");
    return sessionManager.listActiveSessions(userId).stream()
        .filter(token -> token.startsWith(tokenPrefix))
        .findFirst()
        .map(token -> {
          String revokedUserId = sessionManager.evictSession(token);
          if (revokedUserId != null) {
            log.info("设备会话被用户主动下线: userId={}, sessionId={}", userId, sessionId);
            return true;
          }
          return false;
        })
        .orElseThrow(() -> {
          log.warn("设备下线失败[会话不存在]: userId={}, sessionId={}", userId, sessionId);
          return new BusinessException(UserInfoExceptionCode.SESSION_NOT_FOUND);
        });
  }

  /**
   * 解析时间字符串。
   *
   * @param timeStr ISO 时间字符串
   * @return LocalDateTime，解析失败返回 null
   */
  private LocalDateTime parseTime(String timeStr) {
    if (timeStr == null || timeStr.isBlank()) {
      return null;
    }
    try {
      return LocalDateTime.parse(timeStr);
    } catch (Exception e) {
      log.debug("时间解析失败: {}", timeStr);
      return null;
    }
  }

  /**
   * 解析设备类型描述。
   *
   * @param code 设备类型编码
   * @return 中文描述
   */
  private String resolveDeviceTypeDesc(String code) {
    if (code == null) {
      return DeviceType.UNKNOWN.getDescription();
    }
    try {
      return DeviceType.valueOf(code.toUpperCase()).getDescription();
    } catch (IllegalArgumentException e) {
      return DeviceType.UNKNOWN.getDescription();
    }
  }

  /**
   * 对 User-Agent 进行摘要掩码（取前 30 字符 + ...）。
   *
   * @param userAgent 完整 User-Agent
   * @return 掩码后的摘要
   */
  private String maskUserAgent(String userAgent) {
    if (userAgent == null || userAgent.isBlank()) {
      return "未知设备";
    }
    if (userAgent.length() <= USER_AGENT_MAX_LENGTH) {
      return userAgent;
    }
    return userAgent.substring(0, USER_AGENT_MAX_LENGTH) + "...";
  }
}
