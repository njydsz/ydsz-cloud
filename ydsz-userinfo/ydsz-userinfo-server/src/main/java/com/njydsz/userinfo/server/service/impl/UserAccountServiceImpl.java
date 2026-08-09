package com.njydsz.userinfo.server.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.util.bean.BeanUpdateUtil;
import com.njydsz.userinfo.domain.dto.ChangePasswordDTO;
import com.njydsz.userinfo.domain.dto.ResetPasswordDTO;
import com.njydsz.userinfo.domain.dto.UserAccountCreateDTO;
import com.njydsz.userinfo.domain.dto.UserAccountPageQueryDTO;
import com.njydsz.userinfo.domain.dto.UserAccountUpdateDTO;
import com.njydsz.userinfo.domain.entity.Role;
import com.njydsz.common.search.sync.SearchIndexEventBridge;
import com.njydsz.userinfo.domain.entity.UserAccount;
import com.njydsz.userinfo.domain.entity.UserDept;
import com.njydsz.userinfo.domain.entity.UserRole;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.infra.mapper.RoleMapper;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;
import com.njydsz.userinfo.infra.mapper.UserDeptMapper;
import com.njydsz.userinfo.infra.mapper.UserRoleMapper;
import com.njydsz.userinfo.server.auth.PasswordPolicyValidator;
import com.njydsz.userinfo.server.auth.UserPasswordHistoryService;
import com.njydsz.userinfo.server.config.UserInfoProperties;
import com.njydsz.userinfo.server.service.UserAccountService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.auth.annotation.DataScope;
import com.njydsz.userinfo.domain.converter.UserInfoConverter;

/**
 * 用户账号 Service 实现
 *
 * <p>实现 {@link UserAccountService} 接口，封装用户账号的完整业务逻辑：CRUD、密码管理、角色分配、
 * 审批人展开查询、跨服务名称富化。集成密码 BCrypt 加密、密码策略校验、用户名唯一性校验、
 * 数据权限（{@link DataScope}）、搜索索引同步等横切关注点。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>用户 CRUD（含密码 BCrypt 加密存储）</li>
 *   <li>密码管理（用户自助修改 / 管理员重置，含 {@link PasswordPolicyValidator} 策略校验）</li>
 *   <li>角色分配（覆盖式：清空旧关联 + 批量插入新关联）</li>
 *   <li>审批人展开查询（{@code listUserIdsByRoleCode} / {@code listUserIdsByPositionCode} /
 *       {@code getLeaderByUserId} / {@code listDeptIdsByUserId}，供工作流 Feign 调用）</li>
 *   <li>跨服务名称富化（{@code batchUserNames}，供 NameAssembler 调用）</li>
 *   <li>数据权限隔离（{@code @DataScope} 自动追加部门过滤）</li>
 *   <li>搜索索引同步（{@link SearchIndexEventBridge} 异步 upsert/delete）</li>
 * </ul>
 *
 * <p><b>安全设计：</b>
 * <ul>
 *   <li>密码字段全程不进入 VO/响应（{@code UserInfoConverter} 自动脱敏）</li>
 *   <li>BCrypt cost 由 {@code ydsz.system.app.bcrypt-strength} 配置（默认 10）</li>
 *   <li>密码策略：长度、复杂度、历史密码去重由 {@link PasswordPolicyValidator} 校验</li>
 *   <li>登录失败保护：{@code loginFailCount} 达到阈值时设置 {@code lockedUntil}，定时解锁</li>
 * </ul>
 *
 * <p><b>事务：</b>所有写操作（{@code create/update/removeById/changePassword/resetPassword/assignRoles}）
 * 开启 {@code @Transactional(rollbackFor = Exception.class)}，确保任一异常触发完整回滚。
 *
 * <p><b>性能：</b>
 * <ul>
 *   <li>{@link #page} 与 {@link #list} 均启用 {@link DataScope}，数据权限自动追加 WHERE 条件</li>
 *   <li>{@link #assignRoles} 使用批量插入（{@code userRoleMapper.batchInsert}）避免 N+1</li>
 *   <li>{@link #batchUserNames} 使用单条 {@code IN} 查询，单次往返</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see UserAccountService Service 接口
 * @see UserAccount 用户实体
 * @see com.njydsz.userinfo.web.controller.UserAccountController 用户 Controller
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

    /** 用户账号 Mapper */
    /** 分布式 ID 生成器 */
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final UserAccountMapper userAccountMapper;
    /** 用户-角色关联 Mapper */
    private final UserRoleMapper userRoleMapper;
    /** 角色 Mapper（用于角色编码查询） */
    private final RoleMapper roleMapper;
    /** 用户-部门关联 Mapper */
    private final UserDeptMapper userDeptMapper;
    /** 密码编码器（BCrypt） */
    private final PasswordEncoder passwordEncoder;
    /** 密码策略校验器 */
    private final PasswordPolicyValidator passwordPolicyValidator;
    /** 密码历史服务（用于防止密码重复使用） */
    private final UserPasswordHistoryService passwordHistoryService;
    /** 用户中心配置属性 */
    private final UserInfoProperties properties;
    private final ObjectProvider<SearchIndexEventBridge> searchIndexBridgeProvider;

    /**
     * {@inheritDoc}
     *
     * @throws BusinessException 当用户不存在时抛出
     */
    @Override
    public UserAccountVO getById(String id) {
        UserAccount entity = userAccountMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(UserInfoResultCode.USER_NOT_FOUND);
        }
        return UserInfoConverter.INSTANT.entityToVO(entity);
    }

    /**
     * {@inheritDoc}
     * <p>支持按 username/realName/phone/email 模糊匹配、status/userType/companyId 精确匹配过滤。
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "id")
    public Page<UserAccountVO> page(UserAccountPageQueryDTO query) {
        Page<UserAccount> page = new Page<>(
                query.getEffectivePageNum(), query.getEffectivePageSize());
        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();

        if (query.getUsername() != null && !query.getUsername().isBlank()) {
            wrapper.like(UserAccount::getUsername, query.getUsername());
        }
        if (query.getRealName() != null && !query.getRealName().isBlank()) {
            wrapper.like(UserAccount::getRealName, query.getRealName());
        }
        if (query.getPhone() != null && !query.getPhone().isBlank()) {
            wrapper.like(UserAccount::getPhone, query.getPhone());
        }
        if (query.getEmail() != null && !query.getEmail().isBlank()) {
            wrapper.like(UserAccount::getEmail, query.getEmail());
        }
        if (query.getStatus() != null) {
            wrapper.eq(UserAccount::getStatus, String.valueOf(query.getStatus()));
        }
        if (query.getUserType() != null && !query.getUserType().isBlank()) {
            wrapper.eq(UserAccount::getUserType, query.getUserType());
        }
        if (query.getCompanyId() != null && !query.getCompanyId().isBlank()) {
            wrapper.eq(UserAccount::getCompanyId, query.getCompanyId());
        }
        wrapper.orderByDesc(UserAccount::getCreatedAt);

        Page<UserAccount> result = userAccountMapper.selectPage(page, wrapper);
        Page<UserAccountVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<UserAccountVO> voList = result.getRecords().stream()
                .map(UserInfoConverter.INSTANT::entityToVO)
                .collect(Collectors.toList());
        voPage.setRecords(voList);
        return voPage;
    }

    /**
     * {@inheritDoc}
     *
     * @return 全部未删除用户列表（按创建时间降序）
     */
    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "id")
    public List<UserAccountVO> list() {
        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(UserAccount::getCreatedAt);
        return userAccountMapper.selectList(wrapper).stream()
                .map(UserInfoConverter.INSTANT::entityToVO)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     * <p>执行 username 唯一性校验 + 密码策略校验，BCrypt 加密存储密码，
     * status 默认 "1"（启用），tenantId 为空时默认 "1"。
     * <p>创建成功后记录初始密码到密码历史表。
     *
     * @throws BusinessException 当 username 已存在或密码不符合策略时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(UserAccountCreateDTO dto) {
        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAccount::getUsername, dto.getUsername());
        if (userAccountMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(UserInfoResultCode.USERNAME_DUPLICATE);
        }

        // 密码策略校验
        passwordPolicyValidator.validate(dto.getPassword(), dto.getUsername());

        UserAccount entity = UserInfoConverter.INSTANT.createDtoToEntity(dto);
        String passwordHash = passwordEncoder.encode(dto.getPassword());
        entity.setPassword(passwordHash);
        entity.setStatus("1");
        entity.setLoginFailCount(0);
        if (dto.getTenantId() == null || dto.getTenantId().isBlank()) {
            entity.setTenantId("1");
        }
        userAccountMapper.insert(entity);
        log.info("User created: username={}, id={}", entity.getUsername(), entity.getId());

        // 记录初始密码到密码历史
        passwordHistoryService.recordPasswordHistory(
                entity.getId(), passwordHash, properties.getPasswordHistoryCount());

        indexUpsert(entity);
        return entity.getId();
    }

    /**
     * {@inheritDoc}
     * <p>使用 MapStruct 转换（更新操作使用 BeanUpdateUtil 动态复制非 null 字段）
     * status 字段从 Integer 转为 String 存储。
     *
     * @throws BusinessException 当用户不存在或已删除时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(UserAccountUpdateDTO dto) {
        UserAccount entity = userAccountMapper.selectById(dto.getId());
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.USER_NOT_FOUND);
        }
        // 仅复制非 null 属性，避免覆盖已有值；额外忽略 id（主键不可变）
        BeanUpdateUtil.copyNonNull(dto, entity, "id");
        if (dto.getStatus() != null) {
            entity.setStatus(String.valueOf(dto.getStatus()));
        }
        boolean result = userAccountMapper.updateById(entity) > 0;
        if (result) {
            indexUpsert(entity);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * @throws BusinessException 当用户不存在或已删除时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        UserAccount entity = userAccountMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.USER_NOT_FOUND);
        }
        boolean result = userAccountMapper.deleteById(id) > 0;
        if (result) {
            indexDelete(id);
            // 清理密码历史记录（避免敏感数据残留）
            passwordHistoryService.clearHistoryByUserId(id);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     * <p>校验旧密码 → 新旧密码不能相同 → 密码策略校验（含历史密码校验）→ BCrypt 加密存储。
     * <p>修改成功后将新密码记录到历史表。
     *
     * @throws BusinessException 当用户不存在、旧密码错误、新旧密码相同、密码不符合策略或与历史密码重复时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changePassword(ChangePasswordDTO dto) {
        UserAccount entity = userAccountMapper.selectById(dto.getUserId());
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), entity.getPassword())) {
            throw new BusinessException(UserInfoResultCode.OLD_PASSWORD_INCORRECT);
        }
        if (passwordEncoder.matches(dto.getNewPassword(), entity.getPassword())) {
            throw new BusinessException(UserInfoResultCode.PASSWORD_SAME_AS_OLD);
        }

        // 密码策略校验（含历史密码校验）
        passwordPolicyValidator.validate(
                dto.getNewPassword(), entity.getUsername(), dto.getUserId(), passwordHistoryService);

        String newPasswordHash = passwordEncoder.encode(dto.getNewPassword());
        entity.setPassword(newPasswordHash);

        boolean result = userAccountMapper.updateById(entity) > 0;
        if (result) {
            // 记录新密码到历史
            passwordHistoryService.recordPasswordHistory(
                    dto.getUserId(), newPasswordHash, properties.getPasswordHistoryCount());
        }
        return result;
    }

    /**
     * {@inheritDoc}
     * <p>密码策略校验 → BCrypt 加密存储 → 重置失败计数和锁定状态。
     * <p>重置成功后将新密码记录到历史表。
     *
     * @throws BusinessException 当用户不存在、密码不符合策略或与历史密码重复时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resetPassword(ResetPasswordDTO dto) {
        UserAccount entity = userAccountMapper.selectById(dto.getUserId());
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.USER_NOT_FOUND);
        }

        // 密码策略校验（含历史密码校验）
        passwordPolicyValidator.validate(
                dto.getNewPassword(), entity.getUsername(), dto.getUserId(), passwordHistoryService);

        String newPasswordHash = passwordEncoder.encode(dto.getNewPassword());
        entity.setPassword(newPasswordHash);
        entity.setLoginFailCount(0);
        entity.setLockedUntil(null);

        boolean result = userAccountMapper.updateById(entity) > 0;
        if (result) {
            // 记录新密码到历史
            passwordHistoryService.recordPasswordHistory(
                    dto.getUserId(), newPasswordHash, properties.getPasswordHistoryCount());
        }
        return result;
    }

    /**
     * {@inheritDoc}
     * <p>先删除旧的用户-角色关联，再批量插入新关联（全量覆盖模式）。
     *
     * @throws BusinessException 当用户不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean assignRoles(String userId, List<String> roleIds) {
        UserAccount entity = userAccountMapper.selectById(userId);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.USER_NOT_FOUND);
        }

        LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRole::getUserId, userId);
        userRoleMapper.delete(wrapper);

        // 批量插入（替代 N+1 循环）
        List<UserRole> list = new ArrayList<>(roleIds.size());
        for (String roleId : roleIds) {
            UserRole ur = new UserRole();
            ur.setId(String.valueOf(snowflakeIdGenerator.nextId()));
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            ur.setTenantId(entity.getTenantId());
            list.add(ur);
        }
        if (!list.isEmpty()) {
            userRoleMapper.batchInsert(list);
        }
        log.info("Roles assigned to user {}: {}", userId, roleIds);
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表
     */
    @Override
    public List<String> getUserRoleIds(String userId) {
        LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRole::getUserId, userId);
        return userRoleMapper.selectList(wrapper).stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toList());
    }

    /**
     * 按角色编码查询用户 ID 列表。
     *
     * <p>实现：先按 role_code 查 ydsz_role 获取 role_id，再按 role_id 查 ydsz_user_role 获取 user_id 列表。
     * 因单次查询数据量可控（单角色关联用户通常 ≤ 千级），未做缓存。
     */
    @Override
    public List<String> listUserIdsByRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Role> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.eq(Role::getRoleCode, roleCode);
        Role role = roleMapper.selectOne(roleWrapper);
        if (role == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<UserRole> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.eq(UserRole::getRoleId, role.getId());
        return userRoleMapper.selectList(userRoleWrapper).stream()
                .map(UserRole::getUserId)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 查询用户拥有的角色编码列表。
     *
     * <p>实现：先按 user_id 查 ydsz_user_role 获取 role_id 列表，再按 role_id IN(...) 查 ydsz_role 获取 role_code。
     */
    @Override
    public List<String> listRoleCodesByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<UserRole> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.eq(UserRole::getUserId, userId);
        List<String> roleIds = userRoleMapper.selectList(userRoleWrapper).stream()
                .map(UserRole::getRoleId)
                .distinct()
                .collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Role> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.in(Role::getId, roleIds);
        return roleMapper.selectList(roleWrapper).stream()
                .map(Role::getRoleCode)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 查询用户所属部门 ID 列表（支持多部门）。
     */
    @Override
    public List<String> listDeptIdsByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<UserDept> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDept::getUserId, userId);
        return userDeptMapper.selectList(wrapper).stream()
                .map(UserDept::getDeptId)
                .filter(d -> d != null && !d.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 查询用户的直属上级 ID。
     *
     * <p>实现：直接读 ydsz_user_account.leader_id 字段。
     */
    @Override
    public String getLeaderByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        UserAccount entity = userAccountMapper.selectById(userId);
        if (entity == null || entity.getDeleted() == 1) {
            return null;
        }
        return entity.getLeaderId();
    }

    /**
     * 按岗位编码查询用户 ID 列表。
     *
     * <p>实现：直接按 position_code 查 ydsz_user_account。
     */
    @Override
    public List<String> listUserIdsByPositionCode(String positionCode) {
        if (positionCode == null || positionCode.isBlank()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAccount::getPositionCode, positionCode);
        return userAccountMapper.selectList(wrapper).stream()
                .map(UserAccount::getId)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 批量查询用户 ID → 用户真实姓名映射。
     *
     * <p>实现：{@link com.baomidou.mybatisplus.core.mapper.BaseMapper#selectBatchIds(Collection)}
     * 单条 SQL 完成（已自动追加 {@code deleted = 0} 条件，因 {@link UserAccount#getDeleted()} 标注了 {@link com.baomidou.mybatisplus.annotation.TableLogic}）。
     *
     * <p>返回 realName（而非 username）：富化场景需要展示给人看的是真实姓名。
     * 若 realName 为空则该 userId 不出现在结果中（让 NameAssembler 兜底用 userId 顶替）。
     */
    @Override
    public Map<String, String> batchUserNames(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> distinctIds = userIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UserAccount> users = userAccountMapper.selectBatchIds(distinctIds);
        Map<String, String> result = new LinkedHashMap<>(users.size());
        for (UserAccount user : users) {
            if (user.getRealName() != null && !user.getRealName().isBlank()) {
                result.put(user.getId(), user.getRealName());
            }
        }
        return result;
    }

    private void indexUpsert(UserAccount entity) {
        SearchIndexEventBridge bridge = searchIndexBridgeProvider.getIfAvailable();
        if (bridge != null) {
            bridge.indexUpsert("user", entity);
        }
    }

    private void indexDelete(String id) {
        SearchIndexEventBridge bridge = searchIndexBridgeProvider.getIfAvailable();
        if (bridge != null) {
            bridge.indexDelete("user", id);
        }
    }

}
