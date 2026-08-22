package com.njydsz.userinfo.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.userinfo.domain.dto.SocialAccountDTO;
import com.njydsz.userinfo.domain.vo.SocialAccountVO;

/**
 * 社交账号绑定仓储接口（领域契约层）。
 *
 * <p>定义社交账号绑定的数据访问能力，入参为领域 DTO / 基本类型字段，返回值为领域 VO。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>入参必须是 {@code dto/} 下的 DTO 或基本类型字段，禁止接受 infra 层 DO/PO</li>
 *   <li>返回值必须是 {@code vo/} 下的 VO 或 {@code Optional<VO>}，禁止返回 infra 层持久化实体</li>
 *   <li>domain 层对 infra 层零感知，禁止 import {@code infra.entity} 包</li>
 *   <li>实现类位于 {@code ydsz-userinfo-infra} 模块，通过 Converter 完成 DO ↔ VO 转换</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface SocialAccountRepository {

  /**
   * 根据平台标识和 openId 查询社交账号绑定。
   *
   * <p>用于社交登录回调时判断该社交账号是否已绑定系统用户。
   *
   * @param platform 平台标识（如 WECHAT/GITHUB）
   * @param openId 平台用户唯一标识
   * @return 社交账号绑定 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<SocialAccountVO> findByPlatformAndOpenId(String platform, String openId);

  /**
   * 根据用户 ID 和平台标识查询社交账号绑定。
   *
   * <p>用于判断用户是否已绑定某平台账号（防重复绑定）。
   *
   * @param userId 用户 ID
   * @param platform 平台标识
   * @return 社交账号绑定 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<SocialAccountVO> findByUserIdAndPlatform(String userId, String platform);

  /**
   * 查询用户的所有社交账号绑定列表。
   *
   * @param userId 用户 ID
   * @return 社交账号绑定 VO 列表（无绑定返回空列表）
   */
  List<SocialAccountVO> listByUserId(String userId);

  /**
   * 保存社交账号绑定记录。
   *
   * <p>由 Service 层传入已填充的 DTO，Repository 负责加密敏感字段后持久化。
   *
   * @param dto 社交账号绑定 DTO
   */
  void save(SocialAccountDTO dto);

  /**
   * 根据用户 ID 和平台标识删除社交账号绑定（逻辑删除）。
   *
   * @param userId 用户 ID
   * @param platform 平台标识
   */
  void deleteByUserIdAndPlatform(String userId, String platform);
}
