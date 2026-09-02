package com.njydsz.userinfo.domain.oauth2;

import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;

/**
 * OAuth2 应用仓储接口（领域契约层）。
 *
 * <p>定义 OAuth2 应用的数据访问能力，实现类位于 {@code ydsz-userinfo-infra} 模块。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface OAuth2ApplicationRepository {

  /**
   * 保存 OAuth2 应用。
   *
   * @param application 应用聚合根
   * @return 保存后的应用（含生成的 ID）
   */
  OAuth2Application save(OAuth2Application application);

  /**
   * 根据 ID 查询应用。
   *
   * @param id 应用 ID
   * @return 应用；不存在返回 {@code Optional.empty()}
   */
  Optional<OAuth2Application> findById(String id);

  /**
   * 根据 clientId 查询应用。
   *
   * @param clientId 客户端 ID
   * @return 应用；不存在返回 {@code Optional.empty()}
   */
  Optional<OAuth2Application> findByClientId(String clientId);

  /**
   * 分页查询应用列表。
   *
   * @param status 应用状态（可为 null 表示不过滤）
   * @param keyword 搜索关键字（匹配 clientId 或 clientName，可为 null）
   * @param pageNum 页码
   * @param pageSize 每页大小
   * @return 分页结果
   */
  PageResponse<List<OAuth2Application>> page(
      OAuth2Application.ApplicationStatus status,
      String keyword,
      int pageNum,
      int pageSize);

  /**
   * 查询所有启用状态的应用列表。
   *
   * @return 应用列表
   */
  List<OAuth2Application> findAllEnabled();

  /**
   * 删除应用（逻辑删除）。
   *
   * @param id 应用 ID
   * @return 删除成功返回 true
   */
  boolean deleteById(String id);

  /**
   * 判断 clientId 是否已存在。
   *
   * @param clientId 客户端 ID
   * @return true 表示已存在
   */
  boolean existsByClientId(String clientId);
}
