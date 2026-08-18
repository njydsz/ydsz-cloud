package com.njydsz.userinfo.infra.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.njydsz.userinfo.domain.dto.UserAccountCreateDTO;
import com.njydsz.userinfo.domain.dto.CompanyCreateDTO;
import com.njydsz.userinfo.domain.dto.CompanyDTO;
import com.njydsz.userinfo.domain.dto.CompanyDeptDTO;
import com.njydsz.userinfo.domain.dto.CompanyUpdateDTO;
import com.njydsz.userinfo.domain.dto.DepartmentCreateDTO;
import com.njydsz.userinfo.domain.dto.DepartmentDTO;
import com.njydsz.userinfo.domain.dto.DepartmentUpdateDTO;
import com.njydsz.userinfo.domain.dto.LanguageCreateDTO;
import com.njydsz.userinfo.domain.dto.LanguageDTO;
import com.njydsz.userinfo.domain.dto.LanguageUpdateDTO;
import com.njydsz.userinfo.domain.dto.MenuCreateDTO;
import com.njydsz.userinfo.domain.dto.MenuDTO;
import com.njydsz.userinfo.domain.dto.MenuUpdateDTO;
import com.njydsz.userinfo.domain.dto.PostCreateDTO;
import com.njydsz.userinfo.domain.dto.PostDTO;
import com.njydsz.userinfo.domain.dto.PostUpdateDTO;
import com.njydsz.userinfo.domain.dto.RoleCreateDTO;
import com.njydsz.userinfo.domain.dto.RoleDTO;
import com.njydsz.userinfo.domain.dto.RolePermissionDTO;
import com.njydsz.userinfo.domain.dto.RoleUpdateDTO;
import com.njydsz.userinfo.domain.dto.UserDeptDTO;
import com.njydsz.userinfo.domain.dto.UserLoginHistoryDTO;
import com.njydsz.userinfo.domain.dto.UserPasswordHistoryDTO;
import com.njydsz.userinfo.domain.dto.UserPostDTO;
import com.njydsz.userinfo.domain.dto.UserRoleDTO;
import com.njydsz.userinfo.infra.entity.CompanyDeptDO;
import com.njydsz.userinfo.infra.entity.CompanyDO;
import com.njydsz.userinfo.infra.entity.DepartmentDO;
import com.njydsz.userinfo.infra.entity.LanguageDO;
import com.njydsz.userinfo.infra.entity.MenuDO;
import com.njydsz.userinfo.infra.entity.PostDO;
import com.njydsz.userinfo.infra.entity.RoleDO;
import com.njydsz.userinfo.infra.entity.RolePermissionDO;
import com.njydsz.userinfo.infra.entity.UserAccountDO;
import com.njydsz.userinfo.infra.entity.UserDeptDO;
import com.njydsz.userinfo.infra.entity.UserLoginHistoryDO;
import com.njydsz.userinfo.infra.entity.UserPasswordHistoryDO;
import com.njydsz.userinfo.infra.entity.UserPostDO;
import com.njydsz.userinfo.infra.entity.UserRoleDO;
import com.njydsz.userinfo.domain.vo.CompanyDeptVO;
import com.njydsz.userinfo.domain.vo.CompanyTreeVO;
import com.njydsz.userinfo.domain.vo.CompanyVO;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.domain.vo.LanguageVO;
import com.njydsz.userinfo.domain.vo.LoginVO;
import com.njydsz.userinfo.domain.vo.MenuTreeVO;
import com.njydsz.userinfo.domain.vo.MenuVO;
import com.njydsz.userinfo.domain.vo.PostVO;
import com.njydsz.userinfo.domain.vo.RolePermissionVO;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.domain.vo.UserAccountCredentialVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.domain.vo.UserDeptVO;
import com.njydsz.userinfo.domain.vo.UserLoginHistoryVO;
import com.njydsz.userinfo.domain.vo.UserPasswordHistoryVO;
import com.njydsz.userinfo.domain.vo.UserPostVO;
import com.njydsz.userinfo.domain.vo.UserRoleVO;

/**
 * 用户中心模块统一 MapStruct 转换器。
 *
 * <p>提供 Entity ↔ VO / DTO → Entity 的转换方法，替代 BeanUtils.copyProperties 反射方式。 MpBaseEntity 的自动填充字段在
 * DTO→Entity 方向通过 @Mapping(ignore = true) 忽略。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface UserInfoConverter {

  /**
   * MapStruct 生成的转换器单例。
   *
   * <p>在类加载时通过 {@link org.mapstruct.factory.Mappers#getMapper(Class)} 创建并缓存， 全局共享同一实例。MapStruct
   * 编译期生成的实现为<b>无状态、线程安全</b>， 可被 Controller / Service 多线程并发复用，无需每次 new。
   *
   * <p>典型用法：{@code UserInfoConverter.INSTANT.entityToVO(CompanyDO)}。
   */
  UserInfoConverter INSTANT = Mappers.getMapper(UserInfoConverter.class);

  // ===== CompanyDO =====

  /**
   * 公司创建 DTO → 公司实体
   *
   * @param dto 公司创建 DTO
   * @return 公司实体（未持久化）
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  CompanyDO createDtoToEntity(CompanyCreateDTO dto);

  /**
   * 公司更新 DTO → 公司实体
   *
   * @param dto 公司更新 DTO（含 id）
   * @return 公司实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  CompanyDO updateDtoToEntity(CompanyUpdateDTO dto);

  /**
   * 公司实体 → 公司 VO
   *
   * @param entity 公司实体
   * @return 公司 VO（不含 deleted/createdBy 等内部字段）
   */
  CompanyVO entityToVO(CompanyDO entity);

  /**
   * 公司实体列表 → 公司 VO 列表
   *
   * @param entities 公司实体列表
   * @return 公司 VO 列表
   */
  List<CompanyVO> companyListToVO(List<CompanyDO> entities);

  /**
   * 公司实体 → 公司树形 VO（含 children 字段）
   *
   * @param entity 公司实体
   * @return 公司树形 VO
   */
  CompanyTreeVO entityToTreeVO(CompanyDO entity);

  /**
   * 公司实体列表 → 公司树形 VO 列表
   *
   * @param entities 公司实体列表
   * @return 公司树形 VO 列表
   */
  List<CompanyTreeVO> companyTreeListToVO(List<CompanyDO> entities);

  /**
   * 公司 DTO → 公司实体（创建场景）
   *
   * <p>MpBaseEntity 的自动填充字段（id/deleted/revision/tenantId/createdBy/createdAt/updatedBy/updatedAt）
   * 通过 @Mapping(ignore = true) 忽略，由框架自动填充。
   *
   * @param dto 公司 DTO
   * @return 公司实体（未持久化）
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  CompanyDO dtoToEntity(CompanyDTO dto);

  /**
   * 公司 DTO → 公司实体（更新场景）
   *
   * <p>保留 id 字段用于定位更新记录，自动填充字段中 updatedBy/updatedAt 由框架更新。
   *
   * @param dto 公司 DTO（含 id）
   * @return 公司实体（含 id，用于条件更新）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  CompanyDO dtoToEntityWithId(CompanyDTO dto);

  // ===== DepartmentDO =====

  /**
   * 部门创建 DTO → 部门实体
   *
   * @param dto 部门创建 DTO
   * @return 部门实体（未持久化）
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  DepartmentDO createDtoToEntity(DepartmentCreateDTO dto);

  /**
   * 部门更新 DTO → 部门实体
   *
   * @param dto 部门更新 DTO（含 id）
   * @return 部门实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  DepartmentDO updateDtoToEntity(DepartmentUpdateDTO dto);

  /**
   * 部门实体 → 部门 VO（扁平结构）
   *
   * @param entity 部门实体
   * @return 部门 VO
   */
  DepartmentVO entityToVO(DepartmentDO entity);

  /**
   * 部门实体列表 → 部门 VO 列表
   *
   * @param entities 部门实体列表
   * @return 部门 VO 列表
   */
  List<DepartmentVO> departmentListToVO(List<DepartmentDO> entities);

  /**
   * 部门实体 → 部门树形 VO（含 children 字段）
   *
   * @param entity 部门实体
   * @return 部门树形 VO
   */
  DepartmentTreeVO entityToTreeVO(DepartmentDO entity);

  /**
   * 部门实体列表 → 部门树形 VO 列表
   *
   * @param entities 部门实体列表
   * @return 部门树形 VO 列表
   */
  List<DepartmentTreeVO> departmentTreeListToVO(List<DepartmentDO> entities);

  /**
   * 部门 DTO → 部门实体（创建场景）
   *
   * @param dto 部门 DTO
   * @return 部门实体（未持久化）
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  DepartmentDO dtoToEntity(DepartmentDTO dto);

  /**
   * 部门 DTO → 部门实体（更新场景）
   *
   * @param dto 部门 DTO（含 id）
   * @return 部门实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  DepartmentDO dtoToEntityWithId(DepartmentDTO dto);

  // ===== LanguageDO =====

  /**
   * 语言创建 DTO → 语言实体
   *
   * @param dto 语言创建 DTO
   * @return 语言实体（未持久化）
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  LanguageDO createDtoToEntity(LanguageCreateDTO dto);

  /**
   * 语言更新 DTO → 语言实体
   *
   * @param dto 语言更新 DTO（含 id）
   * @return 语言实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  LanguageDO updateDtoToEntity(LanguageUpdateDTO dto);

  /**
   * 语言实体 → 语言 VO
   *
   * @param entity 语言实体
   * @return 语言 VO
   */
  LanguageVO entityToVO(LanguageDO entity);

  /**
   * 语言实体列表 → 语言 VO 列表
   *
   * @param entities 语言实体列表
   * @return 语言 VO 列表
   */
  List<LanguageVO> languageListToVO(List<LanguageDO> entities);

  /**
   * 语言 DTO → 语言实体（创建场景）
   *
   * @param dto 语言 DTO
   * @return 语言实体（未持久化）
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  LanguageDO dtoToEntity(LanguageDTO dto);

  /**
   * 语言 DTO → 语言实体（更新场景）
   *
   * @param dto 语言 DTO（含 id）
   * @return 语言实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  LanguageDO dtoToEntityWithId(LanguageDTO dto);

  // ===== MenuDO =====

  /**
   * 菜单创建 DTO → 菜单实体
   *
   * @param dto 菜单创建 DTO
   * @return 菜单实体（未持久化）
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  MenuDO createDtoToEntity(MenuCreateDTO dto);

  /**
   * 菜单更新 DTO → 菜单实体
   *
   * @param dto 菜单更新 DTO（含 id）
   * @return 菜单实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  MenuDO updateDtoToEntity(MenuUpdateDTO dto);

  /**
   * 菜单实体 → 菜单 VO（扁平结构）
   *
   * @param entity 菜单实体
   * @return 菜单 VO
   */
  MenuVO entityToVO(MenuDO entity);

  /**
   * 菜单实体列表 → 菜单 VO 列表
   *
   * @param entities 菜单实体列表
   * @return 菜单 VO 列表
   */
  List<MenuVO> menuListToVO(List<MenuDO> entities);

  /**
   * 菜单实体 → 菜单树形 VO（含 children 字段）
   *
   * @param entity 菜单实体
   * @return 菜单树形 VO
   */
  MenuTreeVO entityToMenuTreeVO(MenuDO entity);

  /**
   * 菜单实体列表 → 菜单树形 VO 列表
   *
   * @param entities 菜单实体列表
   * @return 菜单树形 VO 列表
   */
  List<MenuTreeVO> menuTreeListToVO(List<MenuDO> entities);

  /**
   * 菜单 DTO → 菜单实体（创建场景）
   *
   * @param dto 菜单 DTO
   * @return 菜单实体（未持久化）
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  MenuDO dtoToEntity(MenuDTO dto);

  /**
   * 菜单 DTO → 菜单实体（更新场景）
   *
   * @param dto 菜单 DTO（含 id）
   * @return 菜单实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  MenuDO dtoToEntityWithId(MenuDTO dto);

  // ===== PostDO =====

  /**
   * 岗位创建 DTO → 岗位实体
   *
   * @param dto 岗位创建 DTO
   * @return 岗位实体（未持久化）
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  PostDO createDtoToEntity(PostCreateDTO dto);

  /**
   * 岗位更新 DTO → 岗位实体
   *
   * @param dto 岗位更新 DTO（含 id）
   * @return 岗位实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  PostDO updateDtoToEntity(PostUpdateDTO dto);

  /**
   * 岗位实体 → 岗位 VO
   *
   * @param entity 岗位实体
   * @return 岗位 VO
   */
  PostVO entityToVO(PostDO entity);

  /**
   * 岗位实体列表 → 岗位 VO 列表
   *
   * @param entities 岗位实体列表
   * @return 岗位 VO 列表
   */
  List<PostVO> postListToVO(List<PostDO> entities);

  /**
   * 岗位 DTO → 岗位实体（创建场景）
   *
   * @param dto 岗位 DTO
   * @return 岗位实体（未持久化）
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  PostDO dtoToEntity(PostDTO dto);

  /**
   * 岗位 DTO → 岗位实体（更新场景）
   *
   * @param dto 岗位 DTO（含 id）
   * @return 岗位实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  PostDO dtoToEntityWithId(PostDTO dto);

  // ===== RoleDO =====

  /**
   * 角色创建 DTO → 角色实体
   *
   * @param dto 角色创建 DTO
   * @return 角色实体（未持久化）
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  RoleDO createDtoToEntity(RoleCreateDTO dto);

  /**
   * 角色更新 DTO → 角色实体
   *
   * @param dto 角色更新 DTO（含 id）
   * @return 角色实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  RoleDO updateDtoToEntity(RoleUpdateDTO dto);

  /**
   * 角色实体 → 角色 VO
   *
   * @param entity 角色实体
   * @return 角色 VO
   */
  RoleVO entityToVO(RoleDO entity);

  /**
   * 角色实体列表 → 角色 VO 列表
   *
   * @param entities 角色实体列表
   * @return 角色 VO 列表
   */
  List<RoleVO> roleListToVO(List<RoleDO> entities);

  /**
   * 角色 DTO → 角色实体（创建场景）
   *
   * @param dto 角色 DTO
   * @return 角色实体（未持久化）
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  RoleDO dtoToEntity(RoleDTO dto);

  /**
   * 角色 DTO → 角色实体（更新场景）
   *
   * @param dto 角色 DTO（含 id）
   * @return 角色实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  RoleDO dtoToEntityWithId(RoleDTO dto);

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
   * 用户创建 DTO → 用户账号实体
   *
   * <p>用于创建用户场景，password 字段由 Service 层加密后设置。
   *
   * @param dto 用户创建 DTO
   * @return 用户账号实体（未持久化，password 为 null 需 Service 层填充）
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  UserAccountDO createDtoToEntity(UserAccountCreateDTO dto);

  /**
   * 用户更新 DTO → 用户账号实体
   *
   * <p>用于更新用户场景，保留 id 字段用于定位更新记录。
   *
   * @param dto 用户更新 DTO
   * @return 用户账号实体（含 id，用于条件更新）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  UserAccountDO updateDtoToEntity(UserAccountUpdateDTO dto);

  /**
   * 用户实体 → 用户认证凭据 VO
   *
   * <p>专用于认证场景，包含密码哈希、锁定状态等敏感字段。
   *
   * @param entity 用户账号实体
   * @return 用户认证凭据 VO
   */
  UserAccountCredentialVO entityToCredentialVO(UserAccountDO entity);

  // ===== CompanyDeptDO =====

  /**
   * 公司-部门关联实体 → VO
   *
   * @param entity 公司-部门关联实体
   * @return 公司-部门关联 VO
   */
  CompanyDeptVO entityToVO(CompanyDeptDO entity);

  /**
   * 公司-部门关联实体列表 → VO 列表
   *
   * @param entities 公司-部门关联实体列表
   * @return 公司-部门关联 VO 列表
   */
  List<CompanyDeptVO> companyDeptListToVO(List<CompanyDeptDO> entities);

  /**
   * 公司-部门关联 DTO → 实体
   *
   * @param dto 公司-部门关联 DTO
   * @return 公司-部门关联实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  CompanyDeptDO dtoToEntity(CompanyDeptDTO dto);

  // ===== RolePermissionDO =====

  /**
   * 角色-权限关联实体 → VO
   *
   * @param entity 角色-权限关联实体
   * @return 角色-权限关联 VO
   */
  RolePermissionVO entityToVO(RolePermissionDO entity);

  /**
   * 角色-权限关联实体列表 → VO 列表
   *
   * @param entities 角色-权限关联实体列表
   * @return 角色-权限关联 VO 列表
   */
  List<RolePermissionVO> rolePermissionListToVO(List<RolePermissionDO> entities);

  /**
   * 角色-权限关联 DTO → 实体
   *
   * @param dto 角色-权限关联 DTO
   * @return 角色-权限关联实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  RolePermissionDO dtoToEntity(RolePermissionDTO dto);

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
