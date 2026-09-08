package com.njydsz.userinfo.server.service;

import com.njydsz.userinfo.domain.dto.UserPreferenceDTO;
import com.njydsz.userinfo.domain.vo.UserPreferenceVO;

/**
 * 用户偏好 Service 接口
 *
 * <p>用户级偏好（默认首页/语言/主题/布局等）的读取、保存与重置。
 * 偏好数据按用户隔离，以 JSON 形式持久化于 Redis（key 按用户维度隔离），
 * 供前端「用户偏好设置」与「个性化首屏」能力使用。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
public interface UserPreferenceService {

  /**
   * 查询当前用户偏好配置。
   *
   * @param userId 用户 ID
   * @return 用户偏好 VO；用户从未保存过偏好时返回全空字段 VO（前端回退本地默认值）
   */
  UserPreferenceVO get(String userId);

  /**
   * 保存当前用户偏好配置（PUT 语义：整体覆盖）。
   *
   * @param userId 用户 ID
   * @param dto 偏好配置（对齐前端 UserPreferenceDTO 契约）
   * @return true=保存成功
   */
  boolean save(String userId, UserPreferenceDTO dto);

  /**
   * 重置当前用户偏好为默认值。
   *
   * <p>删除已持久化的偏好数据，返回全空字段 VO（前端按缺省字段回退默认值）。
   *
   * @param userId 用户 ID
   * @return 重置后的偏好 VO（全空字段）
   */
  UserPreferenceVO reset(String userId);
}
