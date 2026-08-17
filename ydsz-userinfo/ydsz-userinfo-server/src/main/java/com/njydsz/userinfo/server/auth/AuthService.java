package com.njydsz.userinfo.server.auth;

import java.util.List;
import java.util.Set;

import com.njydsz.userinfo.domain.dto.LoginDTO;
import com.njydsz.userinfo.domain.vo.LoginVO;
import com.njydsz.userinfo.domain.vo.UserSessionVO;

/**
 * 认证服务接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AuthService {

  /**
   * 用户登录。
   *
   * @param loginDTO 登录请求（含用户名、密码、验证码等）
   * @return 登录结果
   */
  LoginVO login(LoginDTO loginDTO);

  /**
   * 用户登出。
   *
   * @param accessToken 访问令牌
   */
  void logout(String accessToken);

  /**
   * 刷新 Token。
   *
   * @param refreshToken 刷新令牌
   * @return 新的登录结果
   */
  LoginVO refresh(String refreshToken);

  /**
   * 强制下线指定用户（踢人）。
   *
   * <p>将该用户全部活跃会话（Redis Set 中所有 accessToken）加入黑名单并清理索引。 供管理员调用，实现"强制某用户下线"能力。
   *
   * @param userId 用户 ID，不可为 null
   */
  void kickOutUser(String userId);

  /**
   * 驱逐指定用户的全部活跃会话（改密/禁用时调用）。
   *
   * <p>内部逻辑与 {@link #kickOutUser(String)} 相同，但语义上区分"管理员主动踢人" 与"业务操作触发会话失效"两种场景，便于审计日志区分。
   *
   * @param userId 用户 ID，不可为 null 或空
   */
  void evictAllSessions(String userId);

  /**
   * P1-1: 失效指定用户的角色缓存。
   *
   * <p>在角色分配变更时调用，保证 Redis 中的角色缓存与数据库一致。
   *
   * @param userId 用户 ID，不可为 null 或空
   */
  void evictUserRolesCache(String userId);

  /**
   * 查询用户活跃会话列表。
   *
   * <p>返回该用户当前所有活跃的 accessToken 集合，供前端展示会话管理界面。
   *
   * @param userId 用户 ID
   * @return 活跃 accessToken 集合，无会话时返回空集合
   */
  Set<String> listActiveSessions(String userId);
}
