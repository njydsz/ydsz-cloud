package com.njydsz.userinfo.infra.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.dto.SocialAccountDTO;
import com.njydsz.userinfo.domain.dto.UserAccountDTO;
import com.njydsz.userinfo.domain.dto.UserDeptDTO;
import com.njydsz.userinfo.domain.dto.UserLoginHistoryDTO;
import com.njydsz.userinfo.domain.dto.UserPasswordHistoryDTO;
import com.njydsz.userinfo.domain.dto.UserPostDTO;
import com.njydsz.userinfo.domain.dto.UserRoleDTO;
import com.njydsz.userinfo.infra.entity.SocialAccountDO;
import com.njydsz.userinfo.infra.entity.UserAccountDO;
import com.njydsz.userinfo.infra.entity.UserDeptDO;
import com.njydsz.userinfo.infra.entity.UserLoginHistoryDO;
import com.njydsz.userinfo.infra.entity.UserPasswordHistoryDO;
import com.njydsz.userinfo.infra.entity.UserPostDO;
import com.njydsz.userinfo.infra.entity.UserRoleDO;
import com.njydsz.userinfo.domain.vo.LoginVO;
import com.njydsz.userinfo.domain.vo.SocialAccountVO;
import com.njydsz.userinfo.domain.vo.UserAccountCredentialVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.domain.vo.UserDeptVO;
import com.njydsz.userinfo.domain.vo.UserLoginHistoryVO;
import com.njydsz.userinfo.domain.vo.UserPasswordHistoryVO;
import com.njydsz.userinfo.domain.vo.UserPostVO;
import com.njydsz.userinfo.domain.vo.UserRoleVO;

/**
 * 用户领域 MapStruct 转换器。
 *
 * <p>负责人实体及相关关联表的 Entity ↔ VO / DTO → Entity 转换，涵盖：
 * UserAccount、UserRole、UserPost、UserDept、UserLoginHistory、
 * UserPasswordHistory、SocialAccount、LoginVO.UserInfoVO。
 *
 * <p>使用 Spring 注入模式（componentModel = "spring"），替代旧的静态单例 INSTANT 访问方式。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper(componentModel = "spring")
@Component
public interface UserInfoUserConverter {

  // ===== UserAccountDO =====

  /**
   * 用户账号实体 → 用户账号 VO
   *
   * <p>自动排除 password、loginFailCount、lockedUntil 等敏感字段。
   *
   * @param entity 用户账号实体
   * @return 用户账号 VO（已脱敏）
   */
  UserAccountVO entityToVO(UserAccountDO entity);

  /**
   * 用户账号实体列表 → 用户账号 VO 列表
   *
   * @param entities 用户账号实体列表
   * @return 用户账号 VO 列表（已脱敏）
   */
  List<UserAccountVO> userAccountListToVO(List<UserAccountDO> entities);

  /**
   * 用户统一 DTO → 用户账号实体（创建场景）
   *
   * <p>用于创建用户场景，id 由数据库生成，password 字段由 Service 层加密后设置。
   *
   * @param dto 用户统一 DTO
   * @return 用户账号实体（未持久化，id 为 null，password 为 null 需 Service 层填充）
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  UserAccountDO dtoToEntity(UserAccountDTO dto);

  /**
   * 用户统一 DTO → 用户账号实体（更新场景）
   *
   * <p>用于更新用户场景，保留 id 字段用于定位更新记录。
   *
   * <p>P1-6: revision 不再 ignore —— 由 DTO 携带的版本号参与乐观锁冲突检测；
   * DTO 未传（null）时由 Service 层回填当前版本，保持兼容。
   *
   * @param dto 用户统一 DTO（含 id）
   * @return 用户账号实体（含 id，用于条件更新）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  UserAccountDO dtoToEntityWithId(UserAccountDTO dto);

  /**
   * 用户实体 → 用户认证凭据 VO
   *
   * <p>专用于认证场景，包含密码哈希、锁定状态等敏感字段。
   *
   * @param entity 用户账号实体
   * @return 用户认证凭据 VO
   */
  UserAccountCredentialVO entityToCredentialVO(UserAccountDO entity);

  // ===== UserRoleDO =====

  /**
   * 用户-角色关联实体 → VO
   *
   * @param entity 用户-角色关联实体
   * @return 用户-角色关联 VO
   */
  UserRoleVO entityToVO(UserRoleDO entity);

  /**
   * 用户-角色关联实体列表 → VO 列表
   *
   * @param entities 用户-角色关联实体列表
   * @return 用户-角色关联 VO 列表
   */
  List<UserRoleVO> userRoleListToVO(List<UserRoleDO> entities);

  /**
   * 用户-角色关联 DTO → 实体
   *
   * @param dto 用户-角色关联 DTO
   * @return 用户-角色关联实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  UserRoleDO dtoToEntity(UserRoleDTO dto);

  // ===== UserPostDO =====

  /**
   * 用户-岗位关联实体 → VO
   *
   * @param entity 用户-岗位关联实体
   * @return 用户-岗位关联 VO
   */
  UserPostVO entityToVO(UserPostDO entity);

  /**
   * 用户-岗位关联实体列表 → VO 列表
   *
   * @param entities 用户-岗位关联实体列表
   * @return 用户-岗位关联 VO 列表
   */
  List<UserPostVO> userPostListToVO(List<UserPostDO> entities);

  /**
   * 用户-岗位关联 DTO → 实体
   *
   * @param dto 用户-岗位关联 DTO
   * @return 用户-岗位关联实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  UserPostDO dtoToEntity(UserPostDTO dto);

  // ===== UserDeptDO =====

  /**
   * 用户-部门关联实体 → VO
   *
   * @param entity 用户-部门关联实体
   * @return 用户-部门关联 VO
   */
  UserDeptVO entityToVO(UserDeptDO entity);

  /**
   * 用户-部门关联实体列表 → VO 列表
   *
   * @param entities 用户-部门关联实体列表
   * @return 用户-部门关联 VO 列表
   */
  List<UserDeptVO> userDeptListToVO(List<UserDeptDO> entities);

  /**
   * 用户-部门关联 DTO → 实体
   *
   * @param dto 用户-部门关联 DTO
   * @return 用户-部门关联实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  UserDeptDO dtoToEntity(UserDeptDTO dto);

  /**
   * 用户-部门关联 DTO → 实体（更新场景）
   *
   * @param dto 用户-部门关联 DTO（含 id）
   * @return 用户-部门关联实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  UserDeptDO userDeptDtoToEntityWithId(UserDeptDTO dto);

  // ===== UserPasswordHistoryDO =====

  /**
   * 密码历史实体 → VO
   *
   * @param entity 密码历史实体
   * @return 密码历史 VO
   */
  UserPasswordHistoryVO entityToVO(UserPasswordHistoryDO entity);

  /**
   * 密码历史实体列表 → VO 列表
   *
   * @param entities 密码历史实体列表
   * @return 密码历史 VO 列表
   */
  List<UserPasswordHistoryVO> userPasswordHistoryListToVO(List<UserPasswordHistoryDO> entities);

  /**
   * 密码历史 DTO → 实体
   *
   * @param dto 密码历史 DTO
   * @return 密码历史实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  UserPasswordHistoryDO dtoToEntity(UserPasswordHistoryDTO dto);

  // ===== UserLoginHistoryDO =====

  /**
   * 登录历史实体 → VO
   *
   * @param entity 登录历史实体
   * @return 登录历史 VO
   */
  UserLoginHistoryVO entityToVO(UserLoginHistoryDO entity);

  /**
   * 登录历史实体列表 → VO 列表
   *
   * @param entities 登录历史实体列表
   * @return 登录历史 VO 列表
   */
  List<UserLoginHistoryVO> userLoginHistoryListToVO(List<UserLoginHistoryDO> entities);

  /**
   * 登录历史 DTO → 实体
   *
   * @param dto 登录历史 DTO
   * @return 登录历史实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  UserLoginHistoryDO dtoToEntity(UserLoginHistoryDTO dto);

  // ===== SocialAccountDO =====

  /**
   * 社交账号绑定创建 DTO → 社交账号绑定实体。
   *
   * <p>用于绑定保存场景，MpBaseEntity 的自动填充字段通过 @Mapping(ignore = true) 忽略。
   *
   * @param dto 社交账号绑定创建 DTO
   * @return 社交账号绑定实体（未持久化）
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  SocialAccountDO dtoToEntity(SocialAccountDTO dto);

  /**
   * 社交账号绑定实体 → 社交账号绑定 VO。
   *
   * <p>自动排除 accessToken、refreshToken 等敏感字段。
   *
   * @param entity 社交账号绑定实体
   * @return 社会交账号绑定 VO（已脱敏）
   */
  SocialAccountVO entityToVO(SocialAccountDO entity);

  /**
   * 社交账号绑定实体列表 → 社交账号绑定 VO 列表。
   *
   * @param entities 社交账号绑定实体列表
   * @return 社交账号绑定 VO 列表（已脱敏）
   */
  List<SocialAccountVO> socialAccountListToVO(List<SocialAccountDO> entities);

  // ===== UserAccountDO → LoginVO.UserInfoVO =====

  /**
   * 用户实体 → 登录响应中的用户基本信息 VO。
   *
   * <p>仅映射实体上可直接对应的字段：
   *
   * <ul>
   *   <li>{@code id} → {@code userId}
   *   <li>{@code username} / {@code realName} / {@code tenantId} / {@code avatar} 同名映射
   * </ul>
   *
   * 派生字段 {@code roleCode} / {@code roleName} 由调用方从角色列表拼接后设置， 此处通过 {@code @Mapping(ignore = true)}
   * 隔离，避免 MapStruct 报未映射属性告警。
   *
   * @param entity 用户账号实体
   * @return 登录响应中的用户基本信息 VO（roleCode/roleName 为 null，需调用方填充）
   */
  @Mapping(target = "userId", source = "id")
  @Mapping(target = "roleCode", ignore = true)
  @Mapping(target = "roleName", ignore = true)
  LoginVO.UserInfoVO entityToUserInfoVO(UserAccountDO entity);
}
