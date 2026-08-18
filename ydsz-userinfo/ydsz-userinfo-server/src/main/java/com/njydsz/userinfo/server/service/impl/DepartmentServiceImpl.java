package com.njydsz.userinfo.server.service.impl;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheBuilder;
import com.njydsz.common.domain.tree.TreeBuilder;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.bean.BeanUpdateUtil;
import com.njydsz.userinfo.infra.converter.UserInfoConverter;
import com.njydsz.userinfo.domain.dto.create.DepartmentCreateDTO;
import com.njydsz.userinfo.domain.dto.update.DepartmentUpdateDTO;
import com.njydsz.userinfo.infra.entity.DepartmentDO;
import com.njydsz.userinfo.infra.entity.UserDeptDO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.domain.repository.DepartmentRepository;
import com.njydsz.userinfo.domain.repository.UserDeptRepository;
import com.njydsz.userinfo.server.event.UserDomainEventPublisher;
import com.njydsz.userinfo.server.service.DepartmentService;
import com.njydsz.userinfo.server.service.WorkflowApproverCacheService;

/**
 * 部门 Service 实现
 *
 * <p>实现 {@link DepartmentService} 接口，封装部门的完整业务逻辑：CRUD、deptCode 唯一性校验、
 * 子部门/人员引用检查、树形结构构建、审批人展开查询、跨服务名称富化。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li>部门 CRUD（含 {@code deptCode} 唯一性校验）
 *   <li>删除前置校验（有子部门 / 仍有人员关联时禁止删除）
 *   <li>部门树形结构查询（递归构建父子关系）
 *   <li>部门负责人查询（{@code getDeptLeaderByDeptId} / {@code getDeptLeaderByDeptCode}， 供工作流 {@code
 *       dept:xxx} 审批人展开调用）
 *   <li>跨服务名称富化（{@code batchNamesByIds}，供 NameAssembler 调用）
 * </ul>
 *
 * <p><b>事务：</b>所有写操作（{@code create/update/removeById}） 开启 {@code @Transactional(rollbackFor =
 * Exception.class)}，确保任一异常触发完整回滚。
 *
 * <p><b>性能：</b>
 *
 * <ul>
 *   <li>{@link #tree} 一次性查询全表（已逻辑删除过滤），在内存中构建树，避免 N+1
 *   <li>{@link #batchNamesByIds} 使用单条 {@code IN} 查询，单次往返
 *   <li>{@link #getDeptLeaderByDeptCode} 使用 {@code LIMIT 1} 兜底（避免极端重复编码）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see DepartmentService Service 接口
 * @see DepartmentDO 部门实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

  /** 部门树缓存 Redis key */
  private static final String CACHE_KEY_DEPT_TREE = "userinfo:dept:tree";

  /** 部门树缓存过期时间（秒）：10 分钟 */
  private static final long CACHE_TTL_DEPT_TREE = 600;

  /** 部门树 L1 本地缓存最大条目数 */
  private static final int L1_CACHE_MAX_SIZE = 50;

  /** 部门树 L1 本地缓存过期时间（毫秒）：2 分钟 */
  private static final long L1_CACHE_TTL_MILLIS = 120000;

  /** 部门 Repository */
  private final DepartmentRepository departmentRepository;

  /** 用户-部门关联 Repository（用于删除前检查是否有人员关联） */
  private final UserDeptRepository userDeptRepository;

  /** Redis 服务 */
  private final RedisStringOps redisStringOps;

  /** L1: 本地 Caffeine 缓存（部门树） */
  private Cache<String, String> l1Cache;

  /** 领域事件发布器 */
  private final UserDomainEventPublisher eventPublisher;

  /** 工作流审批人缓存服务（懒加载，避免与 DepartmentService 构造循环依赖） */
  private final ObjectProvider<WorkflowApproverCacheService> workflowCacheProvider;

  /** 初始化 L1 本地缓存 */
  @PostConstruct
  void initL1Cache() {
    l1Cache =
        CacheBuilder.<String, String>newBuilder()
            .maximumSize(L1_CACHE_MAX_SIZE)
            .expireAfterWrite(L1_CACHE_TTL_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build();
  }

  /**
   * {@inheritDoc}
   *
   * @throws BusinessException 当部门不存在或已删除时抛出
   */
  @Override
  public DepartmentVO getById(String id) {
    DepartmentDO entity = departmentRepository.findById(id);
    if (entity == null || entity.getDeleted() == 1) {
      throw new BusinessException(UserInfoExceptionCode.DEPARTMENT_NOT_FOUND);
    }
    return UserInfoConverter.INSTANT.entityToVO(entity);
  }

  /**
   * {@inheritDoc}
   *
   * @return 全部未删除部门列表（按 sortOrder 降序）
   */
  @Override
  public List<DepartmentVO> list() {
    LambdaQueryWrapper<DepartmentDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.orderByDesc(DepartmentDO::getSortOrder);
    return departmentRepository.list(wrapper).stream()
        .map(UserInfoConverter.INSTANT::entityToVO)
        .collect(Collectors.toList());
  }

  /**
   * {@inheritDoc}
   *
   * <p>执行 deptCode 唯一性校验后插入，status 默认 ENABLED，parentId 为空时默认 "0"（根节点）。
   *
   * <p>创建成功后主动失效部门树缓存。
   *
   * @throws BusinessException 当 deptCode 已存在时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String create(DepartmentCreateDTO dto) {
    LambdaQueryWrapper<DepartmentDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DepartmentDO::getDeptCode, dto.getDeptCode());
    if (departmentRepository.count(wrapper) > 0) {
      throw new BusinessException(UserInfoExceptionCode.DEPARTMENT_CODE_DUPLICATE);
    }

    DepartmentDO entity = UserInfoConverter.INSTANT.postDtoToEntity(dto);
    if (entity.getStatus() == null) {
      entity.setStatus("ENABLED");
    }
    if (entity.getParentId() == null || entity.getParentId().isBlank()) {
      entity.setParentId("0");
    }
    departmentRepository.insert(entity);
    log.info("DepartmentDO created: code={}, id={}", entity.getDeptCode(), entity.getId());

    // 部门变更后失效缓存
    evictDeptTreeCache();
    // 发布部门创建领域事件
    eventPublisher.publishDepartmentChanged(entity, "CREATED");

    return entity.getId();
  }

  /**
   * {@inheritDoc}
   *
   * <p>使用 MapStruct 转换（更新操作暂保留 BeanUtils）
   *
   * <p>更新成功后主动失效部门树缓存。
   *
   * @throws BusinessException 当部门不存在或已删除时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean update(DepartmentUpdateDTO dto) {
    DepartmentDO entity = departmentRepository.findById(dto.getId());
    if (entity == null || entity.getDeleted() == 1) {
      throw new BusinessException(UserInfoExceptionCode.DEPARTMENT_NOT_FOUND);
    }
    BeanUpdateUtil.copyNonNull(dto, entity, "id");
    boolean result = departmentRepository.updateById(entity) > 0;

    if (result) {
      // 部门变更后失效缓存（部门树 + 部门负责人工作流缓存）
      evictDeptTreeCache();
      evictDeptLeaderWorkflowCache(entity);
      // 发布部门更新领域事件
      eventPublisher.publishDepartmentChanged(entity, "UPDATED");
    }

    return result;
  }

  /**
   * {@inheritDoc}
   *
   * <p>删除前检查：有子部门不可删除、有人员关联不可删除。
   *
   * <p>删除成功后主动失效部门树缓存。
   *
   * @throws BusinessException 当部门不存在、有子部门、或仍有人员关联时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    DepartmentDO entity = departmentRepository.findById(id);
    if (entity == null || entity.getDeleted() == 1) {
      throw new BusinessException(UserInfoExceptionCode.DEPARTMENT_NOT_FOUND);
    }

    LambdaQueryWrapper<DepartmentDO> childWrapper = new LambdaQueryWrapper<>();
    childWrapper.eq(DepartmentDO::getParentId, id);
    if (departmentRepository.count(childWrapper) > 0) {
      throw new BusinessException(UserInfoExceptionCode.DEPARTMENT_HAS_CHILDREN);
    }

    LambdaQueryWrapper<UserDeptDO> udWrapper = new LambdaQueryWrapper<>();
    udWrapper.eq(UserDeptDO::getDeptId, id);
    if (userDeptRepository.count(udWrapper) > 0) {
      throw new BusinessException(UserInfoExceptionCode.DEPARTMENT_HAS_USERS);
    }

    boolean result = departmentRepository.deleteById(id) > 0;

    if (result) {
      // 部门变更后失效缓存（部门树 + 部门负责人工作流缓存）
      evictDeptTreeCache();
      evictDeptLeaderWorkflowCache(entity);
      // 发布部门删除领域事件
      eventPublisher.publishDepartmentChanged(entity, "DELETED");
    }

    return result;
  }

  /**
   * {@inheritDoc}
   *
   * <p>多级缓存策略：L1（Caffeine 本地缓存，2 分钟 TTL）→ L2（Redis 分布式缓存，10 分钟 TTL）→ DB。
   *
   * <p>缓存 TTL 为 {@value #CACHE_TTL_DEPT_TREE} 秒（L2）。
   *
   * @return 部门树形结构列表，空数据返回空列表
   */
  @Override
  public List<DepartmentTreeVO> tree() {
    // 1. 尝试从 L1 本地缓存获取
    String l1Key = CACHE_KEY_DEPT_TREE;
    String cachedJson = l1Cache.getIfPresent(l1Key);
    if (cachedJson != null && !cachedJson.isBlank()) {
      List<DepartmentTreeVO> cached =
          YdszJson.fromJson(cachedJson, List.class, DepartmentTreeVO.class);
      if (cached != null) {
        log.debug("DepartmentDO tree loaded from L1 cache");
        return cached;
      }
    }

    // 2. 尝试从 L2 Redis 缓存获取
    try {
      cachedJson = redisStringOps.get(CACHE_KEY_DEPT_TREE, String.class);
      if (cachedJson != null && !cachedJson.isBlank()) {
        List<DepartmentTreeVO> cached =
            YdszJson.fromJson(cachedJson, List.class, DepartmentTreeVO.class);
        if (cached != null) {
          log.debug("DepartmentDO tree loaded from L2 cache");
          // 回填 L1 缓存
          l1Cache.put(l1Key, cachedJson);
          return cached;
        }
      }
    } catch (Exception e) {
      log.warn("Failed to load DepartmentDO tree from L2 cache, fallback to DB: {}", e.getMessage());
    }

    // 3. 缓存未命中，查询数据库
    List<DepartmentDO> all =
        departmentRepository.list(
            new LambdaQueryWrapper<DepartmentDO>().eq(DepartmentDO::getDeleted, 0));
    if (all.isEmpty()) {
      return List.of();
    }

    List<DepartmentTreeVO> voList = UserInfoConverter.INSTANT.departmentTreeListToVO(all);
    List<DepartmentTreeVO> tree =
        TreeBuilder.buildSimple(
            voList,
            DepartmentTreeVO::getId,
            DepartmentTreeVO::getParentId,
            DepartmentTreeVO::setChildren,
            DepartmentTreeVO::getSortOrder);

    // 4. 写入多级缓存（异步异常不影响业务）
    try {
      String json = YdszJson.toJson(tree);
      // 写入 L1
      l1Cache.put(l1Key, json);
      // 写入 L2
      redisStringOps.set(CACHE_KEY_DEPT_TREE, json, Duration.ofSeconds(CACHE_TTL_DEPT_TREE));
    } catch (Exception e) {
      log.warn("Failed to cache DepartmentDO tree: {}", e.getMessage());
    }

    return tree;
  }

  /**
   * 失效部门树缓存
   *
   * <p>部门创建/更新/删除时调用，确保缓存数据与数据库一致。同时清除 L1 和 L2 缓存。
   */
  private void evictDeptTreeCache() {
    try {
      // 清除 L1 本地缓存
      l1Cache.invalidate(CACHE_KEY_DEPT_TREE);
      // 清除 L2 Redis 缓存
      redisStringOps.del(CACHE_KEY_DEPT_TREE);
      log.debug("DepartmentDO tree cache evicted (L1 + L2)");
    } catch (Exception e) {
      log.warn("Failed to evict DepartmentDO tree cache: {}", e.getMessage());
    }
  }

  /**
   * 失效部门负责人工作流缓存
   *
   * <p>部门负责人（{@code leader_id}）变更后，工作流 {@code dept:xxx} 审批人展开 使用的缓存必须失效，避免审批人解析到旧负责人。通过懒加载 {@link
   * WorkflowApproverCacheService} 委托处理，避免硬编码缓存 key。
   *
   * @param entity 已变更的部门实体（含 ID）
   */
  private void evictDeptLeaderWorkflowCache(DepartmentDO entity) {
    WorkflowApproverCacheService workflowCache = workflowCacheProvider.getIfAvailable();
    if (workflowCache == null) {
      return;
    }
    try {
      workflowCache.evictDeptLeaderCache(entity.getId());
    } catch (Exception e) {
      log.warn(
          "Failed to evict dept leader workflow cache: deptId={}, error={}",
          entity.getId(),
          e.getMessage());
    }
  }

  /**
   * 按部门 ID 查询部门负责人。
   *
   * <p>实现：直接读 ydsz_department.leader_id 字段。部门不存在或逻辑删除时返回 null。
   */
  @Override
  public String getDeptLeaderByDeptId(String deptId) {
    if (deptId == null || deptId.isBlank()) {
      return null;
    }
    DepartmentDO entity = departmentRepository.findById(deptId);
    if (entity == null || entity.getDeleted() == 1) {
      return null;
    }
    return entity.getLeaderId();
  }

  /**
   * 按部门编码查询部门负责人。
   *
   * <p>实现：按 dept_code 查 ydsz_department 后取 leader_id。
   */
  @Override
  public String getDeptLeaderByDeptCode(String deptCode) {
    if (deptCode == null || deptCode.isBlank()) {
      return null;
    }
    LambdaQueryWrapper<DepartmentDO> wrapper = new LambdaQueryWrapper<>();
    DepartmentDO entity = departmentRepository.findByDeptCode(deptCode);
    if (entity == null) {
      return null;
    }
    return entity.getLeaderId();
  }

  /**
   * 批量查询部门 ID → 部门名映射。
   *
   * <p>实现：{@link com.baomidou.mybatisplus.core.mapper.BaseMapper#selectBatchIds(Collection)} 单条 SQL
   * 完成（已自动追加 {@code deleted = 0} 条件，因 {@link DepartmentDO#getDeleted()} 标注了 {@link
   * com.baomidou.mybatisplus.annotation.TableLogic}）。
   */
  @Override
  public Map<String, String> batchNamesByIds(Collection<String> deptIds) {
    if (deptIds == null || deptIds.isEmpty()) {
      return Collections.emptyMap();
    }
    List<String> distinctIds =
        deptIds.stream()
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .collect(Collectors.toList());
    if (distinctIds.isEmpty()) {
      return Collections.emptyMap();
    }
    List<DepartmentDO> depts = departmentRepository.listByIds(distinctIds);
    Map<String, String> result = new LinkedHashMap<>(depts.size());
    for (DepartmentDO dept : depts) {
      if (dept.getDeptName() != null && !dept.getDeptName().isBlank()) {
        result.put(dept.getId(), dept.getDeptName());
      }
    }
    return result;
  }
}
