package com.njydsz.userinfo.server.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.dto.UserPreferenceDTO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.vo.UserPreferenceVO;
import com.njydsz.userinfo.server.service.UserPreferenceService;

/**
 * 用户偏好 Service 实现
 *
 * <p>基于 Redis 的用户偏好持久化：偏好 JSON 按用户维度隔离存储，无 TTL（随账号存续）。
 * 偏好属非关键路径 UI 状态，读写异常降级为返回空偏好 / 保存失败告警，不阻塞主链路。
 *
 * <p><b>存储设计：</b>
 *
 * <ul>
 *   <li>key：{@code userinfo:preference:User:{userId}}，按用户隔离
 *   <li>value：{@link UserPreferenceDTO} 序列化 JSON
 *   <li>不设 TTL：偏好随账号长期有效；注销清理由用户生命周期流程负责
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 * @see UserPreferenceService 接口定义
 * @see com.njydsz.userinfo.web.controller.UserPreferenceController 对外端点
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferenceServiceImpl implements UserPreferenceService {

  /** 偏好存储 Redis Key 前缀：{@code userinfo:preference:User:{userId}} */
  private static final String CACHE_KEY_PREFIX = "userinfo:preference:User:";

  /** Redis 操作（偏好 JSON 读写） */
  private final RedisStringOps redisStringOps;

  @Override
  public UserPreferenceVO get(String userId) {
    requireUserId(userId);
    try {
      String json = redisStringOps.get(buildCacheKey(userId), String.class);
      if (json == null || json.isBlank()) {
        return new UserPreferenceVO();
      }
      UserPreferenceVO vo = YdszJson.fromJson(json, UserPreferenceVO.class);
      return vo != null ? vo : new UserPreferenceVO();
    } catch (Exception e) {
      log.warn("Failed to read user preference, fallback to empty: userId={}, error={}",
          userId, e.getMessage());
      return new UserPreferenceVO();
    }
  }

  @Override
  public boolean save(String userId, UserPreferenceDTO dto) {
    requireUserId(userId);
    if (dto == null) {
      throw new BusinessException(UserInfoExceptionCode.PARAM_INVALID);
    }
    try {
      redisStringOps.set(buildCacheKey(userId), YdszJson.toJson(dto));
      log.info("User preference saved: userId={}", userId);
      return true;
    } catch (Exception e) {
      log.error("Failed to save user preference: userId={}, error={}", userId, e.getMessage(), e);
      return false;
    }
  }

  @Override
  public UserPreferenceVO reset(String userId) {
    requireUserId(userId);
    try {
      redisStringOps.del(buildCacheKey(userId));
      log.info("User preference reset: userId={}", userId);
    } catch (Exception e) {
      log.warn("Failed to reset user preference: userId={}, error={}", userId, e.getMessage());
    }
    return new UserPreferenceVO();
  }

  /** 校验用户 ID 非空（当前用户上下文缺失时快速失败）。 */
  private void requireUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
    }
  }

  /** 构建偏好存储 Redis Key。 */
  private String buildCacheKey(String userId) {
    return CACHE_KEY_PREFIX + userId;
  }
}
