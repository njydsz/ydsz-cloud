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
   * 灵活列表查询（用于游标分页等需要自定义 QueryWrapper 的场景）。
   *
   * <p>Service 层通过本方法传入预构建的 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}，
   * 实现游标分页、导出数据加载等自定义查询需求。
   *
   * @param wrapper 查询条件包装器（已包含排序、LIMIT 等约束）
   * @return 配置实体列表
   */
  List<Config> findList(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Config> wrapper);

  /**
   * 灵活计数查询（用于游标分页等需要自定义 QueryWrapper 的场景）。
   *
   * @param wrapper 查询条件包装器
   * @return 记录数
   */
  long findCount(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Config> wrapper);
}
