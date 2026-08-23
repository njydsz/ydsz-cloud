package com.njydsz.userinfo.server.auth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.naming.directory.Attributes;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.userinfo.domain.dto.DepartmentDTO;
import com.njydsz.userinfo.domain.dto.UserAccountDTO;
import com.njydsz.userinfo.domain.dto.UserDeptDTO;
import com.njydsz.userinfo.domain.enums.EnableStatusEnum;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.query.DepartmentPageQuery;
import com.njydsz.userinfo.domain.query.UserAccountPageQuery;
import com.njydsz.userinfo.domain.repository.DepartmentRepository;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;
import com.njydsz.userinfo.domain.repository.UserDeptRepository;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.server.config.LdapProperties;
import com.njydsz.userinfo.server.config.LdapSyncProperties;
import com.njydsz.userinfo.server.event.UserDomainEventPublisher;

/**
 * LDAP/AD 组织架构同步服务。
 *
 * <p>提供从 LDAP/AD 自动同步部门（organizationalUnit）与用户（person）的能力，
 * 包括全量同步、增量调和（新增/更新/禁用）、部门层级关系解析。
 *
 * <p><b>同步流程：</b>
 *
 * <ol>
 *   <li>获取分布式锁（防止并发同步）
 *   <li>同步部门数据（先于用户，用户可能关联部门）
 *   <li>同步用户数据（关联已同步的部门）
 *   <li>调和用户状态（LDAP 中已不存在的用户按配置禁用或删除）
 *   <li>释放锁
 * </ol>
 *
 * <p><b>容错策略：</b>单个用户/部门同步失败不影响整体流程，失败详情记录在 {@link SyncResult#errors()} 中。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "ydsz.userinfo.ldap.sync", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class LdapOrgSyncService {
  /** LDAP 外部标识长度（32 位 UUID 去横线） */
  private static final int EXTERNAL_ID_LENGTH = 32;


  /** 分布式锁 key */
  private static final String LOCK_KEY = "ydsz:userinfo:ldap:sync:lock";

  /** 分布式锁租约时间（分钟）：30 分钟 */
  private static final long LOCK_LEASE_MINUTES = 30;

  /** 默认部门负责人 */
  private static final String DEFAULT_PARENT_ID = "0";

  private final LdapProperties ldapProperties;
  private final LdapSyncProperties syncProperties;
  private final DepartmentRepository departmentRepository;
  private final UserAccountRepository userAccountRepository;
  private final UserDeptRepository userDeptRepository;
  private final DistributedLocker distributedLocker;
  private final UserDomainEventPublisher eventPublisher;

  /** LDAP 模板（延迟初始化，从 LdapProperties 构建） */
  @Getter private LdapTemplate ldapTemplate;

  /**
   * LDAP 部门值对象。
   *
   * @param dn 完整识别名
   * @param ou 组织单元名称
   * @param description 描述
   */
  public record LdapDepartment(String dn, String ou, String description) {}

  /**
   * LDAP 用户值对象。
   *
   * @param dn 完整识别名
   * @param username 用户名（uid）
   * @param realName 真实姓名（displayName）
   * @param email 邮箱（mail）
   * @param departmentName 部门名称（department）
   */
  public record LdapUser(String dn, String username, String realName, String email, String departmentName) {}

  /**
   * 同步结果。
   *
   * @param totalProcessed 处理总数
   * @param created 新增数
   * @param updated 更新数
   * @param deactivated 停用数
   * @param failed 失败数
   * @param errors 错误列表
   */
  public record SyncResult(
      int totalProcessed,
      int created,
      int updated,
      int deactivated,
      int failed,
      List<String> errors) {

    /**
     * 合并两个同步结果（用于合并部门和用户的同步结果）。
     *
     * @param other 另一个同步结果
     * @return 合并后的新结果
         */
    public SyncResult merge(SyncResult other) {
      List<String> mergedErrors = new ArrayList<>(this.errors);
      mergedErrors.addAll(other.errors);
      return new SyncResult(
          this.totalProcessed + other.totalProcessed,
          this.created + other.created,
          this.updated + other.updated,
          this.deactivated + other.deactivated,
          this.failed + other.failed,
          mergedErrors);
    }
  }

  /**
   * 初始化 LDAP 模板。
   *
   * <p>从 {@link LdapProperties} 获取连接信息构建 {@link LdapTemplate}。
   */
  private LdapTemplate buildLdapTemplate() {
    String url = "ldap://" + ldapProperties.getHost() + ":" + ldapProperties.getPort();
    LdapContextSource contextSource = new LdapContextSource();
    contextSource.setUrl(url);
    contextSource.setBase(ldapProperties.getBaseDn());
    contextSource.setUserDn(ldapProperties.getDomain());
    contextSource.afterPropertiesSet();
    return new LdapTemplate(contextSource);
  }

  /**
   * 获取 LDAP 模板（懒加载）。
   *
   * @return LDAP 模板
   */
  private LdapTemplate getLdapTemplate() {
    if (ldapTemplate == null) {
      ldapTemplate = buildLdapTemplate();
    }
    return ldapTemplate;
  }

  /**
   * 执行完整同步（部门 + 用户）。
   *
   * <p>获取分布式锁后依次执行部门同步和用户同步，确保数据一致性。
   *
   * @return 合并后的同步结果
   */
  @Transactional(rollbackFor = Exception.class)
  public SyncResult syncAll() {
    String lockValue = distributedLocker.tryLock(LOCK_KEY, LOCK_LEASE_MINUTES, TimeUnit.MINUTES);
    if (lockValue == null) {
      log.warn("LDAP sync skipped: unable to acquire distributed lock");
      throw new BusinessException(UserInfoExceptionCode.LDAP_SYNC_IN_PROGRESS);
    }

    try {
      log.info("LDAP sync started");
      SyncResult deptResult = syncDepartments();
      SyncResult userResult = syncUsers();
      SyncResult totalResult = deptResult.merge(userResult);
      log.info(
          "LDAP sync completed: total={}, created={}, updated={}, deactivated={}, failed={}",
          totalResult.totalProcessed(),
          totalResult.created(),
          totalResult.updated(),
          totalResult.deactivated(),
          totalResult.failed());
      return totalResult;
    } catch (Exception e) {
      log.error("LDAP sync failed: {}", e.getMessage(), e);
      throw new BusinessException(UserInfoExceptionCode.LDAP_SYNC_FAILED, e);
    } finally {
      distributedLocker.unlock(LOCK_KEY, lockValue);
    }
  }

  /**
   * 同步部门数据。
   *
   * <p>从 LDAP 查询所有 organizationalUnit，按层级顺序创建或更新 ydsz 部门。
   *
   * @return 部门同步结果
   */
  public SyncResult syncDepartments() {
    List<LdapDepartment> ldapDepartments = searchDepartments();
    if (ldapDepartments.isEmpty()) {
      log.info("No LDAP departments found");
      return new SyncResult(0, 0, 0, 0, 0, List.of());
    }

    List<String> errors = new ArrayList<>();
    int created = 0;
    int updated = 0;
    int failed = 0;

    // 按 DN 深度排序，确保父部门先于子部门处理
    List<LdapDepartment> sorted = new ArrayList<>(ldapDepartments);
    sorted.sort((a, b) -> countDnComponents(a.dn()) - countDnComponents(b.dn()));

    // DN → ydsz 部门 ID 映射（用于层级关联）
    Map<String, String> dnToDeptId = new LinkedHashMap<>();

    for (LdapDepartment ldapDept : sorted) {
      try {
        String deptCode = buildDeptCode(ldapDept);
        String parentId = resolveParentId(ldapDept.dn(), dnToDeptId);

        Optional<DepartmentVO> existing = departmentRepository.findByDeptCode(deptCode);
        if (existing.isPresent()) {
          // 更新现有部门
          DepartmentDTO updateDTO = new DepartmentDTO();
          updateDTO.setId(existing.get().getId());
          updateDTO.setDeptName(ldapDept.ou());
          updateDTO.setDescription(ldapDept.description());
          updateDTO.setParentId(parentId);
          departmentRepository.save(updateDTO);
          dnToDeptId.put(ldapDept.dn(), existing.get().getId());
          updated++;
          log.debug("Department updated: code={}, id={}", deptCode, existing.get().getId());
        } else {
          // 创建新部门
          DepartmentDTO createDTO = new DepartmentDTO();
          createDTO.setDeptCode(deptCode);
          createDTO.setDeptName(ldapDept.ou());
          createDTO.setDescription(ldapDept.description());
          createDTO.setParentId(parentId);
          createDTO.setStatus(EnableStatusEnum.ENABLED.name());
          DepartmentVO vo = departmentRepository.save(createDTO);
          dnToDeptId.put(ldapDept.dn(), vo.getId());
          created++;
          log.info("Department created: code={}, id={}", deptCode, vo.getId());
          eventPublisher.publishDepartmentChanged(vo, "CREATED");
        }
      } catch (Exception e) {
        failed++;
        String error = String.format("Failed to sync department [%s]: %s", ldapDept.dn(), e.getMessage());
        errors.add(error);
        log.warn(error, e);
      }
    }

    return new SyncResult(ldapDepartments.size(), created, updated, 0, failed, errors);
  }

  /**
   * 同步用户数据。
   *
   * <p>从 LDAP 查询所有 person，创建或更新 ydsz 用户，并调和已不存在于 LDAP 的用户。
   *
   * @return 用户同步结果
   */
  public SyncResult syncUsers() {
    List<LdapUser> ldapUsers = searchUsers();
    SyncResult reconcileResult = reconcileUsers(ldapUsers);
    return reconcileResult;
  }

  /**
   * 调和用户数据（新增/更新/禁用）。
   *
   * <p>对 LDAP 中存在的用户执行创建或更新；对 ydsz 中存在但 LDAP 中不存在的用户，
   * 根据 {@link LdapSyncProperties#isDeleteOrphanedUsers()} 决定删除或禁用。
   *
   * @param ldapUsers LDAP 用户列表
   * @return 调和结果
   */
  public SyncResult reconcileUsers(List<LdapUser> ldapUsers) {
    List<String> errors = new ArrayList<>();
    int created = 0;
    int updated = 0;
    int deactivated = 0;
    int failed = 0;

    // 构建 LDAP 用户名集合（用于判断孤儿用户）
    Set<String> ldapUsernames = new HashSet<>();

    for (LdapUser user : ldapUsers) {
      try {
        ldapUsernames.add(user.username());

        Optional<UserAccountVO> existing = userAccountRepository.findByUsername(user.username());
        if (existing.isPresent()) {
          // 更新现有用户
          UserAccountDTO updateDTO = new UserAccountDTO();
          updateDTO.setId(existing.get().getId());
          updateDTO.setRealName(user.realName());
          updateDTO.setEmail(user.email());
          updateUserDepartment(existing.get().getId(), user);
          userAccountRepository.save(updateDTO);
          updated++;
          log.debug("User updated: username={}, id={}", user.username(), existing.get().getId());
        } else {
          // 创建新用户
          UserAccountDTO createDTO = buildUserCreateDTO(user);
          UserAccountVO vo = userAccountRepository.save(createDTO);
          linkUserToDepartment(vo.getId(), user);
          created++;
          log.info("User created: username={}, id={}", user.username(), vo.getId());
          eventPublisher.publishUserCreated(vo);
        }
      } catch (Exception e) {
        failed++;
        String error = String.format("Failed to sync user [%s]: %s", user.username(), e.getMessage());
        errors.add(error);
        log.warn(error, e);
      }
    }

    // 处理孤儿用户（ydsz 中存在但 LDAP 中不存在）
    if (syncProperties.isDeleteOrphanedUsers()) {
      deactivateOrphanedUsers(ldapUsernames, errors);
    }

    int totalProcessed = ldapUsers.size() + deactivated;
    return new SyncResult(totalProcessed, created, updated, deactivated, failed, errors);
  }

  /**
   * 查询 LDAP 部门。
   *
   * @return LDAP 部门列表
   */
  public List<LdapDepartment> searchDepartments() {
    try {
      String base = syncProperties.getGroupDn();
      if (base == null || base.isBlank()) {
        base = syncProperties.getBaseDn();
      }
      if (base == null || base.isBlank()) {
        log.warn("LDAP sync baseDn and groupDn are both empty, skip department search");
        return List.of();
      }

      String filter = syncProperties.getGroupSearchFilter();
      LdapTemplate template = getLdapTemplate();

      List<LdapDepartment> departments = template.search(
          LdapQueryBuilder.query().base(base).filter(filter),
          (AttributesMapper<LdapDepartment>) attrs -> {
            String dn = getAttributeValue(attrs, "distinguishedName");
            if (dn == null) {
              dn = getAttributeValue(attrs, "dn");
            }
            String ou = getAttributeValue(attrs, "ou");
            String description = getAttributeValue(attrs, "description");
            return new LdapDepartment(dn, ou, description);
          });

      log.info("LDAP department search returned {} entries", departments.size());
      return departments;
    } catch (Exception e) {
      log.error("Failed to search LDAP departments: {}", e.getMessage(), e);
      throw new BusinessException(UserInfoExceptionCode.LDAP_CONNECTION_FAILED, e);
    }
  }

  /**
   * 查询 LDAP 用户。
   *
   * @return LDAP 用户列表
   */
  public List<LdapUser> searchUsers() {
    try {
      String base = syncProperties.getBaseDn();
      if (base == null || base.isBlank()) {
        log.warn("LDAP sync baseDn is empty, skip user search");
        return List.of();
      }

      String filter = syncProperties.getUserSearchFilter();
      LdapTemplate template = getLdapTemplate();

      // 获取属性映射配置
      Map<String, String> attrMap = syncProperties.getUserAttributes();
      String usernameAttr = attrMap.getOrDefault("uid", "username");
      String realNameAttr = attrMap.getOrDefault("displayName", "realName");
      String emailAttr = attrMap.getOrDefault("mail", "email");
      String deptAttr = attrMap.getOrDefault("department", "departmentName");

      List<LdapUser> users = template.search(
          LdapQueryBuilder.query().base(base).filter(filter),
          (AttributesMapper<LdapUser>) attrs -> {
            String dn = getAttributeValue(attrs, "distinguishedName");
            if (dn == null) {
              dn = getAttributeValue(attrs, "dn");
            }
            String username = getAttributeValue(attrs, usernameAttr);
            String realName = getAttributeValue(attrs, realNameAttr);
            String email = getAttributeValue(attrs, emailAttr);
            String departmentName = getAttributeValue(attrs, deptAttr);
            return new LdapUser(dn, username, realName, email, departmentName);
          });

      log.info("LDAP user search returned {} entries", users.size());
      return users;
    } catch (Exception e) {
      log.error("Failed to search LDAP users: {}", e.getMessage(), e);
      throw new BusinessException(UserInfoExceptionCode.LDAP_CONNECTION_FAILED, e);
    }
  }

  // ==================== 私有辅助方法 ====================

  /**
   * 构建用户创建 DTO。
   *
   * @param ldapUser LDAP 用户
   * @return 用户创建 DTO
   */
  private UserAccountDTO buildUserCreateDTO(LdapUser ldapUser) {
    UserAccountDTO dto = new UserAccountDTO();
    dto.setUsername(ldapUser.username());
    dto.setRealName(ldapUser.realName());
    dto.setEmail(ldapUser.email());
    dto.setUserType("REGULAR");
    dto.setStatus(EnableStatusEnum.ENABLED);
    // LDAP 用户密码由 LDAP 管理，此处设置随机占位密码（用户通过 LDAP 认证）
    dto.setPassword(generatePlaceholderPassword());
    return dto;
  }

  /**
   * 生成占位密码。
   *
   * <p>LDAP 用户通过 LDAP 认证，DB 中的密码仅为占位，不会被用于认证。
   *
   * @return 随机密码字符串
   */
  private String generatePlaceholderPassword() {
    return "LDAP_" + UUID.randomUUID().toString().replace("-", "").substring(0, EXTERNAL_ID_LENGTH);
  }

  /**
   * 更新用户部门关联。
   *
   * @param userId 用户 ID
   * @param ldapUser LDAP 用户
   */
  private void updateUserDepartment(String userId, LdapUser ldapUser) {
    if (ldapUser.departmentName() == null || ldapUser.departmentName().isBlank()) {
      return;
    }
    try {
      Optional<DepartmentVO> dept = findDepartmentByName(ldapUser.departmentName());
      if (dept.isPresent()) {
        linkUserToDepartment(userId, ldapUser);
      }
    } catch (Exception e) {
      log.warn(
          "Failed to update user department: userId={}, dept={}, error={}",
          userId,
          ldapUser.departmentName(),
          e.getMessage());
    }
  }

  /**
   * 关联用户到部门。
   *
   * @param userId 用户 ID
   * @param ldapUser LDAP 用户
   */
  private void linkUserToDepartment(String userId, LdapUser ldapUser) {
    if (ldapUser.departmentName() == null || ldapUser.departmentName().isBlank()) {
      return;
    }
    Optional<DepartmentVO> dept = findDepartmentByName(ldapUser.departmentName());
    if (dept.isEmpty()) {
      return;
    }

    UserDeptDTO userDeptDTO = new UserDeptDTO();
    userDeptDTO.setUserId(userId);
    userDeptDTO.setDeptId(dept.get().getId());
    userDeptRepository.create(userDeptDTO);
    log.debug("User linked to department: userId={}, deptId={}", userId, dept.get().getId());
  }

  /**
   * 按部门名称查找部门。
   *
   * <p>先按 deptCode 精确查找，未命中则按 deptName 遍历匹配。
   *
   * @param deptName 部门名称
   * @return 部门 VO
   */
  private Optional<DepartmentVO> findDepartmentByName(String deptName) {
    // 先尝试按 deptCode 查找
    Optional<DepartmentVO> dept = departmentRepository.findByDeptCode(buildDeptCodeFromName(deptName));
    if (dept.isPresent()) {
      return dept;
    }
    // 按名称精确匹配（遍历所有部门）
    DepartmentPageQuery query = new DepartmentPageQuery();
    return departmentRepository.list(query).stream()
        .filter(d -> deptName.equals(d.getDeptName()))
        .findFirst();
  }

  /**
   * 构建部门编码。
   *
   * @param ldapDept LDAP 部门
   * @return 部门编码
   */
  private String buildDeptCode(LdapDepartment ldapDept) {
    return buildDeptCodeFromName(ldapDept.ou());
  }

  /**
   * 从部门名称构建部门编码。
   *
   * @param deptName 部门名称
   * @return 部门编码
   */
  private String buildDeptCodeFromName(String deptName) {
    if (deptName == null || deptName.isBlank()) {
      return "DEPT_UNKNOWN";
    }
    return "DEPT_" + deptName.toUpperCase().replaceAll("[^A-Z0-9]", "_");
  }

  /**
   * 解析父部门 ID。
   *
   * @param dn 当前部门 DN
   * @param dnToDeptId DN 到部门 ID 的映射
   * @return 父部门 ID
   */
  private String resolveParentId(String dn, Map<String, String> dnToDeptId) {
    if (dn == null || !syncProperties.isDepartmentHierarchyEnabled()) {
      return DEFAULT_PARENT_ID;
    }
    // 从 DN 中提取父 DN（去掉第一个组件）
    String parentDn = extractParentDn(dn);
    if (parentDn == null) {
      return DEFAULT_PARENT_ID;
    }
    String parentId = dnToDeptId.get(parentDn);
    return parentId != null ? parentId : DEFAULT_PARENT_ID;
  }

  /**
   * 从 DN 中提取父 DN。
   *
   * @param dn 完整 DN
   * @return 父 DN，如果不存在返回 null
   */
  private String extractParentDn(String dn) {
    if (dn == null || dn.isBlank()) {
      return null;
    }
    int commaIndex = dn.indexOf(',');
    if (commaIndex < 0 || commaIndex == dn.length() - 1) {
      return null;
    }
    return dn.substring(commaIndex + 1).trim();
  }

  /**
   * 计算 DN 组件数量。
   *
   * @param dn DN 字符串
   * @return 组件数量
   */
  private int countDnComponents(String dn) {
    if (dn == null || dn.isBlank()) {
      return 0;
    }
    int count = 1;
    for (int i = 0; i < dn.length(); i++) {
      if (dn.charAt(i) == ',') {
        count++;
      }
    }
    return count;
  }

  /**
   * 删除孤儿用户。
   *
   * <p>删除 ydsz 中存在但 LDAP 中已不存在的用户。
   *
   * @param ldapUsernames LDAP 用户名集合
   * @param errors 错误列表
   */
  private void deactivateOrphanedUsers(Set<String> ldapUsernames, List<String> errors) {
    try {
      UserAccountPageQuery query = new UserAccountPageQuery();
      List<UserAccountVO> allUsers = userAccountRepository.list(query);
      for (UserAccountVO user : allUsers) {
        if (!ldapUsernames.contains(user.getUsername())) {
          try {
            userAccountRepository.deleteById(user.getId());
            log.info("Orphaned user deleted: username={}", user.getUsername());
            eventPublisher.publishUserDeleted(user.getId(), user.getUsername());
          } catch (Exception e) {
            String error = String.format(
                "Failed to delete orphaned user [%s]: %s", user.getUsername(), e.getMessage());
            errors.add(error);
            log.warn(error, e);
          }
        }
      }
    } catch (Exception e) {
      log.warn("Failed to deactivate orphaned users: {}", e.getMessage(), e);
    }
  }

  /**
   * 获取 LDAP 属性值。
   *
   * @param attrs LDAP 属性集
   * @param attrName 属性名
   * @return 属性值，不存在返回 null
   */
  private String getAttributeValue(Attributes attrs, String attrName) {
    try {
      if (attrs.get(attrName) == null) {
        return null;
      }
      Object value = attrs.get(attrName).get();
      return value != null ? value.toString() : null;
    } catch (Exception e) {
      return null;
    }
  }
}
