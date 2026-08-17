package com.njydsz.system.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.njydsz.system.domain.entity.AppInfo;

/**
 * 应用信息仓储接口（Infra 层契约）。
 *
 * <p>定义应用注册域的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域实体（{@link AppInfo}），非 DTO / VO
 *   <li>分页查询通过 {@link Page} + {@link IPage} 标准契约返回
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
   * @return 应用实体；不存在返回 {@code Optional.empty()}
   */
  Optional<AppInfo> findEnabledByAppKey(String appKey);

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
   * @return 应用实体；不存在返回 {@code Optional.empty()}
   */
  Optional<AppInfo> findById(String id);

  /**
   * 分页查询应用。
   *
   * @param page 分页参数
   * @param appName 应用名模糊匹配（可选）
   * @param status 状态精确匹配（可选）
   * @return 分页结果
   */
  IPage<AppInfo> findByPage(Page<AppInfo> page, String appName, String status);

  /**
   * 查询全部应用（不区分状态）。
   *
   * @return 全部应用列表
   */
  List<AppInfo> findAll();

  /**
   * 插入应用。
   *
   * @param entity 应用实体
   * @return 插入成功返回 {@code true}
   */
  boolean insert(AppInfo entity);

  /**
   * 更新应用。
   *
   * @param entity 应用实体
   * @return 更新成功返回 {@code true}
   */
  boolean updateById(AppInfo entity);

  /**
   * 逻辑删除应用。
   *
   * @param id 应用 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteById(String id);
}
