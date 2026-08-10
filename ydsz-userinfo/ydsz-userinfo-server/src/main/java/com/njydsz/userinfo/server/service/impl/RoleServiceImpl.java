package com.njydsz.userinfo.server.service.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.util.bean.BeanUpdateUtil;
import com.njydsz.common.redis.service.RedisService;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.auth.annotation.DataScope;

import com.njydsz.userinfo.domain.dto.RolePageQueryDTO;
import com.njydsz.userinfo.domain.dto.post.RolePostDTO;
import com.njydsz.userinfo.domain.dto.put.RolePutDTO;
import com.njydsz.userinfo.domain.entity.Role;
import com.njydsz.userinfo.domain.entity.RolePermission;
import com.njydsz.userinfo.domain.entity.UserRole;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.infra.mapper.RoleMapper;
import com.njydsz.userinfo.infra.mapper.RolePermissionMapper;
import com.njydsz.userinfo.infra.mapper.UserRoleMapper;
import com.njydsz.userinfo.server.event.UserDomainEventPublisher;
import com.njydsz.userinfo.server.service.RoleService;
import com.njydsz.userinfo.domain.converter.UserInfoConverter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 角色 Service 实现
 *
 * <p>实现 {@link RoleService} 接口，封装角色的完整业务逻辑：CRUD、roleCode 唯一性校验、
 * 内置角色保护、角色-权限批量分配、跨服务名称富化。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>角色 CRUD（含 roleCode 唯一性校验）</li>
 *   <li>内置角色保护（{@code builtIn=true} 禁止删除，{@code roleCode} 不可修改）</li>
 *   <li>删除前置校验（仍有用户关联时禁止删除，避免悬挂引用）</li>
 *   <li>角色-权限分配（覆盖式：清空旧关联 + 批量插入新关联）</li>
 *   <li>跨服务名称富化（{@code batchNamesByIds}，供 NameAssembler 调用）</li>
 *   <li>数据权限隔离（{@code @DataScope} 自动追加创建人部门过滤）</li>
 * </ul>
 *
 * <p><b>事务：</b>所有写操作（{@code create/update/removeById/assignPermissions}）
 * 开启 {@code @Transactional(rollbackFor = Exception.class)}，确保任一异常触发完整回滚。
 *
 * <p><b>性能：</b>
 * <ul>
 *   <li>{@link #assignPermissions} 使用批量插入（{@code rolePermissionMapper.batchInsert}）避免 N+1</li>
 *   <li>{@link #batchNamesByIds} 使用单条 {@code IN} 查询，单次往返</li>
 *   <li>分页与列表查询均按 {@code sortOrder} 升序，匹配前端展示顺序</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see RoleService Service 接口
 * @see Role 角色实体
 * @see com.njydsz.userinfo.web.controller.RoleController 角色 Controller
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    /** 角色权限缓存 Redis key 前缀 */
    private static final String CACHE_KEY_ROLE_PERMISSIONS_PREFIX = "userinfo:role:permissions:";
    /** 角色权限缓存过期时间（秒）：10 分钟 */
    private static final long CACHE_TTL_ROLE_PERMISSIONS = 600;

    /** 角色 Mapper */
    /** 分布式 ID 生成器 */
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final RoleMapper roleMapper;
    /** 角色-权限关联 Mapper */
    private final RolePermissionMapper rolePermissionMapper;
    /** 用户-角色关联 Mapper（用于删除前检查是否有用户关联） */
    private final UserRoleMapper userRoleMapper;
    /** Redis 服务 */
    private final RedisService redisService;
    /** 领域事件发布器 */
    private final UserDomainEventPublisher eventPublisher;

    /**
     * {@inheritDoc}
     *
     * @throws BusinessException 当角色不存在或已删除时抛出
     */
    @Override
    public RoleVO getById(String id) {
        Role entity = roleMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoExceptionCode.ROLE_NOT_FOUND);
        }
        return UserInfoConverter.INSTANT.entityToVO(entity);
    }

    /**
     * {@inheritDoc}
     * <p>支持按 roleCode/roleName 模糊匹配、status 精确匹配过滤，结果按 sortOrder 升序。
     */
    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "created_by")
    public Page<RoleVO> page(RolePageQueryDTO query) {
        Page<Role> page = new Page<>(query.getEffectivePageNum(), query.getEffectivePageSize());
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        if (query.getRoleCode() != null && !query.getRoleCode().isBlank()) {
            wrapper.like(Role::getRoleCode, query.getRoleCode());
        }
        if (query.getRoleName() != null && !query.getRoleName().isBlank()) {
            wrapper.like(Role::getRoleName, query.getRoleName());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(Role::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(Role::getSortOrder);
        Page<Role> result = roleMapper.selectPage(page, wrapper);
        Page<RoleVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<RoleVO> voList = result.getRecords().stream()
                .map(UserInfoConverter.INSTANT::entityToVO)
                .collect(Collectors.toList());
        voPage.setRecords(voList);
        return voPage;
    }

    /**
     * {@inheritDoc}
     *
     * @return 全部未删除角色列表（按 sortOrder 升序）
     */
    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "created_by")
    public List<RoleVO> list() {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Role::getSortOrder);
        return roleMapper.selectList(wrapper).stream()
                .map(UserInfoConverter.INSTANT::entityToVO)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     * <p>执行 roleCode 唯一性校验后插入，status 默认 ENABLED，builtIn 默认 false。
     * <p>创建后无需失效角色-权限缓存（新角色无权限分配）。
     *
     * @throws BusinessException 当 roleCode 已存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(RolePostDTO dto) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getRoleCode, dto.getRoleCode());
        if (roleMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(UserInfoExceptionCode.ROLE_CODE_DUPLICATE);
        }

        Role entity = UserInfoConverter.INSTANT.postDtoToEntity(dto);
        if (entity.getStatus() == null) {
            entity.setStatus("ENABLED");
        }
        if (entity.getBuiltIn() == null) {
            entity.setBuiltIn(false);
        }
        roleMapper.insert(entity);
        log.info("Role created: code={}, id={}", entity.getRoleCode(), entity.getId());
        eventPublisher.publishRoleEntityChanged(entity, "CREATED");
        return entity.getId();
    }

    /**
     * {@inheritDoc}
     * <p>使用 MapStruct 转换（更新操作暂保留 BeanUtils）
     * @throws BusinessException 当角色不存在或已删除时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(RolePutDTO dto) {
        Role entity = roleMapper.selectById(dto.getId());
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoExceptionCode.ROLE_NOT_FOUND);
        }
        BeanUpdateUtil.copyNonNull(dto, entity, "id", "builtIn");
        boolean result = roleMapper.updateById(entity) > 0;

        if (result) {
            // 角色变更后失效其权限缓存
            evictRolePermissionCache(dto.getId());
            eventPublisher.publishRoleEntityChanged(entity, "UPDATED");
        }

        return result;
    }

    /**
     * {@inheritDoc}
     * <p>删除前检查：内置角色不可删除、有用户关联的角色不可删除。
     * 删除时同时清除角色-权限关联记录。
     * <p>删除成功后主动失效角色权限缓存。
     *
     * @throws BusinessException 当角色不存在、为内置角色、或仍有用户关联时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        Role entity = roleMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoExceptionCode.ROLE_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(entity.getBuiltIn())) {
            throw new BusinessException(UserInfoExceptionCode.ROLE_BUILTIN_CANNOT_DELETE);
        }

        LambdaQueryWrapper<UserRole> urWrapper = new LambdaQueryWrapper<>();
        urWrapper.eq(UserRole::getRoleId, id);
        if (userRoleMapper.selectCount(urWrapper) > 0) {
            throw new BusinessException(UserInfoExceptionCode.ROLE_HAS_USERS);
        }

        LambdaQueryWrapper<RolePermission> rpWrapper = new LambdaQueryWrapper<>();
        rpWrapper.eq(RolePermission::getRoleId, id);
        rolePermissionMapper.delete(rpWrapper);

        boolean result = roleMapper.deleteById(id) > 0;

        if (result) {
            // 角色删除后失效缓存
            evictRolePermissionCache(id);
            eventPublisher.publishRoleEntityChanged(entity, "DELETED");
        }

        return result;
    }

    /**
     * {@inheritDoc}
     * <p>先删除旧的角色-权限关联，再批量插入新关联（全量覆盖模式）。
     * <p>分配成功后主动失效角色权限缓存。
     *
     * @throws BusinessException 当角色不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean assignPermissions(String roleId, List<String> permissionIds) {
        Role role = roleMapper.selectById(roleId);
        if (role == null || role.getDeleted() == 1) {
            throw new BusinessException(UserInfoExceptionCode.ROLE_NOT_FOUND);
        }

        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermission::getRoleId, roleId);
        rolePermissionMapper.delete(wrapper);

        // 批量插入（替代 N+1 循环）
        List<RolePermission> list = new ArrayList<>(permissionIds.size());
        for (String permId : permissionIds) {
            RolePermission rp = new RolePermission();
            rp.setId(String.valueOf(snowflakeIdGenerator.nextId()));
            rp.setRoleId(roleId);
            rp.setPermissionId(permId);
            rp.setTenantId(role.getTenantId());
            list.add(rp);
        }
        if (!list.isEmpty()) {
            rolePermissionMapper.batchInsert(list);
        }
        log.info("Permissions assigned to role {}: {}", roleId, permissionIds.size());

        // 权限分配后失效缓存
        evictRolePermissionCache(roleId);

        return true;
    }

    /**
     * {@inheritDoc}
     * <p>优先从 Redis 缓存读取角色权限列表，缓存未命中时查询数据库并写入缓存。
     *
     * @param roleId 角色 ID
     * @return 权限 ID 列表
     */
    @Override
    public List<String> getRolePermissionIds(String roleId) {
        String cacheKey = CACHE_KEY_ROLE_PERMISSIONS_PREFIX + roleId;

        // 1. 尝试从缓存获取
        try {
            String cachedJson = redisService.get(cacheKey, String.class);
            if (cachedJson != null && !cachedJson.isBlank()) {
                List<String> cached = YdszJson.fromJson(cachedJson, java.util.List.class, String.class);
                if (cached != null) {
                    log.debug("Role permissions loaded from cache: roleId={}", roleId);
                    return cached;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load role permissions from cache, fallback to DB: {}", e.getMessage());
        }

        // 2. 缓存未命中，查询数据库
        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermission::getRoleId, roleId);
        List<String> permissionIds = rolePermissionMapper.selectList(wrapper).stream()
                .map(RolePermission::getPermissionId)
                .collect(Collectors.toList());

        // 3. 写入缓存（异步异常不影响业务）
        try {
            String json = YdszJson.toJson(permissionIds);
            redisService.set(cacheKey, json, Duration.ofSeconds(CACHE_TTL_ROLE_PERMISSIONS));
        } catch (Exception e) {
            log.warn("Failed to cache role permissions: {}", e.getMessage());
        }

        return permissionIds;
    }

    /**
     * 失效角色权限缓存
     *
     * <p>角色删除/更新/权限分配时调用，确保缓存数据与数据库一致。
     *
     * @param roleId 角色 ID
     */
    private void evictRolePermissionCache(String roleId) {
        if (roleId == null) {
            return;
        }
        try {
            redisService.del(CACHE_KEY_ROLE_PERMISSIONS_PREFIX + roleId);
            log.debug("Role permission cache evicted: roleId={}", roleId);
        } catch (Exception e) {
            log.warn("Failed to evict role permission cache: {}", e.getMessage());
        }
    }

    /**
     * 批量查询角色 ID → 角色名映射。
     *
     * <p>实现：{@link com.baomidou.mybatisplus.core.mapper.BaseMapper#selectBatchIds(Collection)}
     * 单条 SQL 完成（已自动追加 {@code deleted = 0} 条件，因 {@link Role#getDeleted()} 标注了 {@link com.baomidou.mybatisplus.annotation.TableLogic}）。
     */
    @Override
    public Map<String, String> batchNamesByIds(Collection<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> distinctIds = roleIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Role> roles = roleMapper.selectBatchIds(distinctIds);
        Map<String, String> result = new LinkedHashMap<>(roles.size());
        for (Role role : roles) {
            if (role.getRoleName() != null && !role.getRoleName().isBlank()) {
                result.put(role.getId(), role.getRoleName());
            }
        }
        return result;
    }
}
