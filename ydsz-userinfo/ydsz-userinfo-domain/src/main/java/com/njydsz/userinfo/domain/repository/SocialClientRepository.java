package com.njydsz.userinfo.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.userinfo.domain.dto.SocialClientCreateDTO;
import com.njydsz.userinfo.domain.dto.SocialClientUpdateDTO;
import com.njydsz.userinfo.domain.query.SocialClientPageQuery;
import com.njydsz.userinfo.domain.vo.SocialClientVO;

/**
 * 社交平台客户端配置仓储接口（P1-1 领域契约层）。
 *
 * <p>定义社交平台 OAuth2 客户端配置的数据访问能力，入参为领域 DTO / 基本类型字段，返回值为领域 VO。
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
 * @since 2.24.0
 */
public interface SocialClientRepository {

  /**
   * 分页查询社交平台客户端配置列表。
   *
   * @param query 分页查询参数
   * @return 配置 VO 列表
   */
  List<SocialClientVO> findByPage(SocialClientPageQuery query);

  /**
   * 查询所有已启用的平台配置（按 sort_order 升序）。
   *
   * @return 已启用的配置 VO 列表
   */
  List<SocialClientVO> findEnabled();

  /**
   * 根据平台标识查询客户端配置。
   *
   * @param platform 平台标识
   * @return 配置 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<SocialClientVO> findByPlatform(String platform);

  /**
   * 新增社交平台客户端配置。
   *
   * @param dto 创建 DTO
   */
  void save(SocialClientCreateDTO dto);

  /**
   * 更新社交平台客户端配置。
   *
   * @param platform 平台标识
   * @param dto 更新 DTO
   */
  void update(String platform, SocialClientUpdateDTO dto);

  /**
   * 根据平台标识删除客户端配置（逻辑删除）。
   *
   * @param platform 平台标识
   */
  void deleteByPlatform(String platform);
}
