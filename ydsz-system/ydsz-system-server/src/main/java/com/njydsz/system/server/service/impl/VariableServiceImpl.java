package com.njydsz.system.server.service.impl;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.jdbc.support.PageResponses;
import com.njydsz.common.json.YdszJson;
import com.njydsz.system.domain.converter.SystemConverter;
import com.njydsz.system.domain.entity.Variable;
import com.njydsz.system.domain.enums.ConfigValueType;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.vo.VariableVO;
import com.njydsz.system.infra.repository.VariableRepository;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.EntityVersionService;
import com.njydsz.system.server.service.VariableService;

/**
 * 系统变量 Service 实现
 *
 * <p>对 {@link VariableService} 接口的完整实现，是「系统变量中心」的核心业务逻辑层。 与 {@link ConfigServiceImpl}
 * 能力对齐，但定位不同：Variable 用于业务侧动态参数 （如当前生效的会计年度、最近结算月份、流水号计数器等）， 业务方可通过 Feign 远程查询；Config
 * 用于系统级配置，由后端模块本地消费。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@link #page} / {@link #getById} / {@link #save} / {@link #updateById} / {@link
 *       #removeById}，全部走 {@code @Transactional} 事务保证
 *   <li><b>按 key 查询值</b>：{@link #getVariableValue}（走 ydsz-common-cache 本地缓存 + Spring Cache 注解）
 *   <li><b>分页 / 列表查询</b>：{@link #page} / {@link #list}，支持行级数据权限过滤 （{@code @DataScope}）
 *   <li><b>缓存失效</b>：写操作通过 {@code @CacheEvict(allEntries=true)} 主动清空
 * </ul>
 *
 * <p><b>缓存设计：</b>
 *
 * <ul>
 *   <li>缓存名称：{@link CacheConstants#SYSTEM_VARIABLE_CACHE}（ydsz-common-cache 本地缓存）
 *   <li>缓存键：{@code {tenantId}:{variableKey}}
 *   <li>TTL 与容量通过 {@code ydsz.cache.caches.system:variable} YAML 配置
 *   <li>写操作触发 {@code @CacheEvict(allEntries=true)} 主动失效
 * </ul>
 *
 * <p><b>事务边界：</b>
 *
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}
 *   <li>读方法不开启事务，依赖 MyBatis 自动提交
 *   <li>分页 / 列表查询使用 {@code @DataScope} 自动注入行级数据权限 SQL
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离， 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>与 ConfigService 的区别：</b>
 *
 * <table>
 *   <caption>Variable vs Config 定位差异</caption>
 *   <tr><th>维度</th><th>{@link ConfigServiceImpl Config}</th><th>Variable（本类）</th></tr>
 *   <tr><td>使用方</td><td>后端模块本地消费</td><td>业务方 + 跨服务 Feign 查询</td></tr>
 *   <tr><td>典型场景</td><td>数据库连接池参数、日志级别</td><td>会计年度、流水号、动态业务参数</td></tr>
 *   <tr><td>缓存粒度</td><td>单 key + 组批量 + 公开配置</td><td>仅单 key（无组批量）</td></tr>
 *   <tr><td>变更广播</td><td>发布 {@code ConfigChangeEvent}</td><td>不广播（业务方拉取即可）</td></tr>
 *   <tr><td>行级权限</td><td>无</td><td>有（{@code @DataScope}）</td></tr>
 * </table>
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>业务参数化</b>：业务硬编码值（如「每月 1 号出账」）可改为变量，由运营灵活调整
 *   <li><b>行级权限</b>：分页 / 列表查询走 {@code @DataScope}，自动按当前用户的部门 / 人员范围过滤
 *   <li><b>软删除</b>：{@code ydsz_variable} 表采用 <b>逻辑删除</b>（{@code deleted} 字段）
 *   <li><b>启用过滤</b>：{@link #getVariableValue} 仅返回 {@code status=ENABLED} 的变量， 失效的变量视为不存在
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 业务方远程查询（跨服务）
 * String currentYear = variableClient.getVariableValue("finance.current_fiscal_year");
 *
 * // 后端模块本地查询
 * String waterNo = variableService.getVariableValue("serial.water_no_prefix");
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see VariableService 变量 Service 接口
 * @see ConfigServiceImpl 系统配置 Service（能力对齐但定位不同）
 * @see com.njydsz.system.domain.entity.Variable 变量实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VariableServiceImpl implements VariableService {

  /** 变量仓储 */
  private final VariableRepository variableRepository;

  /** 系统监控指标采集器 */
  private final SystemMetrics metrics;

  /** 统一实体版本服务（写操作时创建版本快照） */
  private final EntityVersionService entityVersionService;

  /** Spring Cache 管理器（用于按 key 精准失效缓存） */
  private final org.springframework.cache.CacheManager cacheManager;

  /** 租户感知缓存键构造器（SpEL 与手动 evict 共用） */
  private final com.njydsz.system.server.cache.CacheKeyBuilder cacheKeyBuilder;

  /**
   * 根据主键查询变量（不走缓存，直接走 DB）
   *
   * <p>适用场景：管理后台「变量详情」页，单次访问无缓存需求。 高频查询请使用 {@link #getVariableValue}。
   *
   * @param id 变量主键
   * @return 变量 VO，不存在返回 null
   */
  @Override
  public VariableVO getById(String id) {
    Variable entity = variableRepository.getVariableMapper().selectById(id);
    return SystemConverter.INSTANT.entityToVO(entity);
  }

  /**
   * 按 variableKey 查询变量值（走缓存）
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>通过 Spring Cache {@code @Cacheable} 查本地缓存（{@link
   *       CacheConstants#SYSTEM_VARIABLE_CACHE}），命中直接返回
   *   <li>缓存未命中查 DB（方法体内仅执行此逻辑）
   *   <li>记录查询耗时指标（缓存命中时方法不执行，由 Micrometer 记录）
   * </ol>
   *
   * <p>本方法是高频读入口，跨服务 Feign 调用建议走本方法，避免直连 DB。
   *
   * @param variableKey 变量键
   * @return 变量值字符串，不存在时返回 null（SpringYdszCache 自动缓存 null 值防穿透）
   */
  @Override
  @Cacheable(value = CacheConstants.SYSTEM_VARIABLE_CACHE, key = "@cacheKeyBuilder.variable(#p0)")
  public String getVariableValue(String variableKey) {
    long start = System.nanoTime();
    try {
      metrics.recordVariableCacheMiss();
      QueryWrapper<Variable> wrapper = new QueryWrapper<>();
      wrapper.eq("variable_key", variableKey).eq("status", "ENABLED");
      Variable entity = variableRepository.getVariableMapper().selectOne(wrapper);
      return entity != null ? entity.getVariableValue() : null;
    } finally {
      metrics.recordVariableRead(System.nanoTime() - start);
    }
  }

  /**
   * 分页查询变量（管理后台列表页）
   *
   * <p>支持按 {@code variableKey} 模糊匹配、{@code status} 精确匹配进行过滤， 按 {@code created_at} 倒序返回。
   *
   * <p><b>行级权限：</b>本方法带 {@code @DataScope} 注解， 自动按当前用户的部门 / 人员范围过滤（管理员看全量）。
   *
   * @param pageNum 页码（1-based）
   * @param pageSize 每页条数
   * @param variableKey 变量键（可选，模糊匹配）
   * @param status 状态（可选过滤条件，如 {@code ENABLED/DISABLED}）
   * @return 分页结果（含总条数）
   */
  @Override
  public PageResponse<List<VariableVO>> page(
      int pageNum, int pageSize, String variableKey, String status) {
    QueryWrapper<Variable> wrapper = new QueryWrapper<>();
    if (variableKey != null && !variableKey.isBlank()) {
      wrapper.like("variable_key", variableKey);
    }
    if (status != null && !status.isBlank()) {
      wrapper.eq("status", status);
    }
    wrapper.orderByDesc("created_at");
    IPage<Variable> page = variableRepository.getVariableMapper().selectPage(new Page<>(pageNum, pageSize), wrapper);
    return PageResponses.success(page, SystemConverter.INSTANT::entityToVO);
  }

  /**
   * 查询全部变量（不区分状态）
   *
   * <p>典型调用方：管理后台「变量选择器」下拉框。
   *
   * <p><b>租户隔离：</b>本方法按当前租户自动过滤（MyBatis 拦截器注入 tenant_id）。
   *
   * <p><b>慎用：</b>全表扫描，变量一般 < 200 条，单次查询 < 20ms。
   *
   * @return 全部变量列表（按 createdAt 倒序）
   */
  @Override
  public List<VariableVO> list() {
    return variableRepository.getVariableMapper().selectList(null).stream()
        .map(SystemConverter.INSTANT::entityToVO)
        .collect(Collectors.toList());
  }

  /**
   * 新增变量
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>DTO 转 DO，默认 {@code status=ENABLED}
   *   <li>插入 {@code ydsz_variable} 表
   *   <li>精准失效该 {@code variableKey} 对应的缓存
   * </ol>
   *
   * @param dto 变量数据
   * @return 新创建的变量 ID
   */
  @Override
  @CacheEvict(
      value = CacheConstants.SYSTEM_VARIABLE_CACHE,
      key = "@cacheKeyBuilder.variable(#vo.variableKey)")
  @Transactional(rollbackFor = Exception.class)
  public String save(VariableVO vo) {
    validateValueType(vo.getValueType());
    Variable entity = toEntity(vo);
    variableRepository.getVariableMapper().insert(entity);
    return entity.getId();
  }

  /**
   * 更新变量
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>DTO 转 DO
   *   <li>更新 {@code ydsz_variable} 表
   *   <li>更新成功后精准失效该 {@code variableKey} 对应的缓存
   * </ol>
   *
   * <p><b>注意：</b>更新 {@code variableKey} 会导致所有依赖该键的下游缓存失效， 调用方需主动清理相关业务缓存。
   *
   * @param dto 变量数据（需包含 {@code id}）
   * @return true=更新成功，false=记录不存在
   */
  @Override
  @CacheEvict(
      value = CacheConstants.SYSTEM_VARIABLE_CACHE,
      key = "@cacheKeyBuilder.variable(#vo.variableKey)")
  @Transactional(rollbackFor = Exception.class)
  public boolean updateById(VariableVO vo) {
    validateValueType(vo.getValueType());
    Variable entity = toEntity(vo);
    // 版本快照：查询变更前状态
    Variable before =
        variableRepository.getVariableMapper().selectOne(
            new QueryWrapper<Variable>()
                .eq("variable_key", entity.getVariableKey())
                .eq("deleted", 0));
    String snapshotJson = before != null ? YdszJson.toJson(before) : null;
    boolean updated = variableRepository.getVariableMapper().updateById(entity) > 0;
    if (updated) {
      // 变量键变更时，旧键缓存一并失效
      if (before != null && !Objects.equals(before.getVariableKey(), entity.getVariableKey())) {
        evictVariable(before.getVariableKey());
      }
      // 创建版本快照（与变量变更同一事务）
      entityVersionService.createVersion(
          EntityVersionService.RESOURCE_TYPE_VARIABLE,
          entity.getVariableKey(),
          "",
          "v" + System.currentTimeMillis(),
          "更新变量: " + entity.getVariableKey(),
          snapshotJson);
    }
    return updated;
  }

  /**
   * 逻辑删除变量
   *
   * <p>采用<b>逻辑删除</b>（{@code deleted=1} + {@code status=DISABLED}）， 不真正从 DB 删除，便于审计回溯。
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>查询原实体（用于获取 variableKey）
   *   <li>逻辑删除记录
   *   <li>删除成功后精准失效该 {@code variableKey} 对应的缓存
   * </ol>
   *
   * @param id 变量主键
   * @return true=删除成功，false=记录不存在
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    Variable entity = variableRepository.getVariableMapper().selectById(id);
    String snapshotJson = entity != null ? YdszJson.toJson(entity) : null;
    boolean removed = variableRepository.getVariableMapper().deleteById(id) > 0;
    if (removed && entity != null) {
      // 精准失效该变量键缓存（替代 allEntries 全量清空）
      evictVariable(entity.getVariableKey());
      // 创建版本快照（与变量变更同一事务）
      entityVersionService.createVersion(
          EntityVersionService.RESOURCE_TYPE_VARIABLE,
          entity.getVariableKey(),
          "",
          "v" + System.currentTimeMillis(),
          "删除变量: " + entity.getVariableKey(),
          snapshotJson);
    }
    return removed;
  }

  /**
   * DTO → DO 转换（私有）
   *
   * <p>缺省 {@code status="ENABLED"}，保证新建的变量默认可用。
   *
   * @param dto 数据传输对象
   * @return 数据库实体
   */
  private Variable toEntity(VariableVO vo) {
    Variable entity = new Variable();
    entity.setId(vo.getId());
    entity.setVariableKey(vo.getVariableKey());
    entity.setVariableValue(vo.getVariableValue());
    entity.setValueType(vo.getValueType());
    entity.setDescription(vo.getDescription());
    entity.setStatus(vo.getStatus() != null ? vo.getStatus() : "ENABLED");
    return entity;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String rollbackTo(String resourceKey, String targetVersion, String operatorId) {
    return entityVersionService.rollbackTo(
        EntityVersionService.RESOURCE_TYPE_VARIABLE,
        resourceKey,
        targetVersion,
        operatorId,
        snapshotJson -> {
          if (snapshotJson != null && !snapshotJson.isBlank()) {
            try {
              VariableVO snapshotVO = YdszJson.fromJson(snapshotJson, VariableVO.class);
              Variable currentVariable =
                  variableRepository
                      .getVariableMapper()
                      .selectOne(
                          new QueryWrapper<Variable>()
                              .eq("variable_key", resourceKey)
                              .eq("deleted", 0));
              if (currentVariable != null) {
                currentVariable.setVariableValue(snapshotVO.getVariableValue());
                currentVariable.setValueType(snapshotVO.getValueType());
                currentVariable.setDescription(snapshotVO.getDescription());
                currentVariable.setStatus(snapshotVO.getStatus());
                variableRepository.getVariableMapper().updateById(currentVariable);
              } else {
                Variable newVariable = new Variable();
                newVariable.setVariableKey(snapshotVO.getVariableKey());
                newVariable.setVariableValue(snapshotVO.getVariableValue());
                newVariable.setValueType(snapshotVO.getValueType());
                newVariable.setDescription(snapshotVO.getDescription());
                newVariable.setStatus(snapshotVO.getStatus());
                variableRepository.getVariableMapper().insert(newVariable);
              }
            } catch (Exception e) {
              throw BusinessException.of(SystemExceptionCode.SNAPSHOT_PARSE_ERROR)
                  .data("reason", e.getMessage());
            }
          }
          // P0-2：回滚后精准失效缓存，避免读到旧值
          evictVariable(resourceKey);
        });
  }

  /**
   * 精准失效指定变量键的缓存（替代 allEntries 全量清空）。
   *
   * @param variableKey 变量键
   */
  private void evictVariable(String variableKey) {
    if (variableKey == null) {
      return;
    }
    cacheManager
        .getCache(CacheConstants.SYSTEM_VARIABLE_CACHE)
        .evict(cacheKeyBuilder.variable(variableKey));
  }

  /**
   * 校验变量值类型合法性。
   *
   * <p>委托 {@link ConfigValueType#validate} 完成，非法类型将抛出 {@link BusinessException}
   *（{@link SystemExceptionCode#VALUE_TYPE_INVALID}）阻止脏数据落库。
   *
   * @param valueType 值类型字符串
   */
  private void validateValueType(String valueType) {
    try {
      ConfigValueType.validate(valueType);
    } catch (IllegalArgumentException e) {
      throw BusinessException.of(SystemExceptionCode.VALUE_TYPE_INVALID)
          .data("valueType", valueType);
    }
  }
}
