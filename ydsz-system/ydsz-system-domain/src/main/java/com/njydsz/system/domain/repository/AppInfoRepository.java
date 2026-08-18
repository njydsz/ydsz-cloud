package com.njydsz.system.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;

import com.njydsz.system.domain.dto.AppInfoDTO;
import com.njydsz.system.domain.query.AppInfoPageQuery;
import com.njydsz.system.domain.vo.AppInfoVO;

/**
 * 应用信息仓储接口（domain 层契约）。
 *
 * <p>定义应用注册域的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link AppInfoVO}），非 DTO / infra 实体
 *   <li>CUD 入参使用领域 DTO（{@link AppInfoDTO}），禁止接受 infra 实体
 *   <li>分页查询入参使用领域 Query（{@link AppInfoPageQuery}）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AppInfoRepository {

  /**
   * 按应用 Key 查询启用的应用（含 appSecret，仅供密钥校验内部使用）。
   *
   * @param appKey 应用 Key
   * @return 应用 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<AppInfoVO> findEnabledByAppKey(String appKey);

  /**
   * 校验应用 Key 是否已存在。
   *
   * @param appKey 应用 Key
   * @return 已存在返回 {@code true}
   */
  boolean existsByAppKey(String appKey);

  /**
   * 根据主键查询应用。
   *
   * @param id 应用主键
   * @return 应用 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<AppInfoVO> findById(String id);

  /**
   * 分页查询应用。
   *
   * @param query 分页查询参数
   * @return 分页结果（VO 分页）
   */
  PageResponse<List<AppInfoVO>> findByPage(AppInfoPageQuery query);

  /**
   * 查询全部应用（不区分状态）。
   *
   * @return 全部应用 VO 列表
   */
  List<AppInfoVO> findAll();

  /**
   * 插入应用。
   *
   * @param dto 应用 DTO
   * @return 插入成功返回 {@code true}
   */
  boolean insert(AppInfoDTO dto);

  /**
   * 更新应用。
   *
   * @param dto 应用 DTO
   * @return 更新成功返回 {@code true}
   */
  boolean updateById(AppInfoDTO dto);

  /**
   * 逻辑删除应用。
   *
   * @param id 应用 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteById(String id);
}
