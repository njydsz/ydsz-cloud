package com.njydsz.system.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.system.domain.entity.Config;

/**
 * 系统配置仓储接口（Infra 层契约）。
 *
 * <p>定义配置域的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域实体（{@link Config}），非 DTO / VO
 *   <li>分页查询通过 {@link Page} + {@link IPage} 标准契约返回
 * </ul>
 *
 * @author ydsz-team
 * @since 1.1.0
 */
public interface ConfigRepository {

  /**
   * 按配置键查询启用的配置项。
   *
   * @param configKey 配置键
   * @return 配置实体；不存在返回 {@code Optional.empty()}
   */
  Optional<Config> findEnabledByKey(String configKey);

  /**
   * 按配置键查询配置项（不区分状态）。
   *
   * @param configKey 配置键
   * @return 配置实体；不存在返回 {@code Optional.empty()}
   */
  Optional<Config> findByKeyIgnoreStatus(String configKey);

  /**
   * 按分组查询启用状态配置（按 sortOrder 升序）。
   *
   * @param configGroup 配置分组
   * @return 启用配置列表
   */
  List<Config> findEnabledByGroup(String configGroup);

  /**
   * 查询全部公开配置（按 sortOrder 升序）。
   *
   * @return 公开配置列表
   */
  List<Config> findPublicEnabled();

  /**
   * 校验同分组下配置键是否已存在。
   *
   * @param configGroup 配置分组
   * @param configKey 配置键
   * @return 已存在返回 {@code true}
   */
  boolean existsByGroupAndKey(String configGroup, String configKey);

  /**
   * 分页查询配置。
   *
   * @param page 分页参数
   * @param configGroup 配置分组（可选）
   * @param configKey 配置键模糊匹配（可选）
   * @param status 状态（可选）
   * @return 分页结果
   */
  IPage<Config> findByPage(Page<Config> page, String configGroup, String configKey, String status);

  /**
   * 插入配置。
   *
   * @param entity 配置实体
   * @return 插入成功返回 {@code true}
   */
  boolean insert(Config entity);

  /**
   * 更新配置。
   *
   * @param entity 配置实体
   * @return 更新成功返回 {@code true}
   */
  boolean updateById(Config entity);

  /**
   * 逻辑删除配置。
   *
   * @param id 配置 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteById(String id);

  /**
   * 按 ID 查询配置。
   *
   * @param id 配置 ID
   * @return 配置实体；不存在返回 {@code Optional.empty()}
   */
  Optional<Config> findById(String id);

  /**
   * 批量插入配置。
   *
   * @param entities 配置实体列表
   * @return 插入成功返回 {@code true}
   */
  boolean insertBatch(List<Config> entities);

  /**
   * 游标分页查询（seek method，{@code id > cursor} 升序，最多返回 {@code limit} 条）。
   *
   * <p>供游标分页场景使用，按分组精确 / 配置键模糊过滤，避免深度分页 offset 扫描。
   *
   * @param configGroup 配置分组（可选，精确匹配）
   * @param configKey 配置键（可选，模糊匹配）
   * @param cursor 游标（上一页最后一条 ID，可选）
   * @param limit 返回条数上限
   * @return 配置实体列表（按 ID 升序）
   */
  List<Config> findForCursor(String configGroup, String configKey, String cursor, int limit);

  /**
   * 判断游标之后是否还有更多记录。
   *
   * @param configGroup 配置分组（可选）
   * @param configKey 配置键（可选，模糊匹配）
   * @param cursor 游标（上一页最后一条 ID）
   * @return 存在后续记录返回 {@code true}
   */
  boolean existsAfterCursor(String configGroup, String configKey, String cursor);

  /**
   * 查询待导出配置（未删除记录，按分组/排序号有序）。
   *
   * @param configGroup 配置分组（为空导出全部）
   * @return 配置实体列表
   */
  List<Config> findForExport(String configGroup);

  /**
   * 查询全部启用状态的配置（用于缓存预热）。
   *
   * @return 启用且未删除的配置列表
   */
  List<Config> findEnabledConfigs();

  /**
   * 按分组查询配置列表（含未删除条件）。
   *
   * @param configGroup 配置分组
   * @return 配置实体列表
   */
  List<Config> findByGroup(String configGroup);

  /**
   * 查询全部未删除配置（用于搜索索引全量重建）。
   *
   * @return 未删除配置列表
   */
  List<Config> findAll();

  /**
   * 按租户 ID 查询未删除配置（用于搜索索引全量重建）。
   *
   * @param tenantId 租户 ID（null 或空表示全量）
   * @return 未删除配置列表
   */
  List<Config> findByTenantId(String tenantId);
}
