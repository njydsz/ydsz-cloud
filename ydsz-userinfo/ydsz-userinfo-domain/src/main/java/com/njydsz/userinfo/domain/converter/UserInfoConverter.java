package com.njydsz.userinfo.domain.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.njydsz.userinfo.domain.dto.UserAccountCreateDTO;
import com.njydsz.userinfo.domain.dto.create.CompanyCreateDTO;
import com.njydsz.userinfo.domain.dto.create.DepartmentCreateDTO;
import com.njydsz.userinfo.domain.dto.create.LanguageCreateDTO;
import com.njydsz.userinfo.domain.dto.create.MenuCreateDTO;
import com.njydsz.userinfo.domain.dto.create.PostCreateDTO;
import com.njydsz.userinfo.domain.dto.create.RoleCreateDTO;
import com.njydsz.userinfo.domain.dto.update.CompanyUpdateDTO;
import com.njydsz.userinfo.domain.dto.update.DepartmentUpdateDTO;
import com.njydsz.userinfo.domain.dto.update.LanguageUpdateDTO;
import com.njydsz.userinfo.domain.dto.update.MenuUpdateDTO;
import com.njydsz.userinfo.domain.dto.update.PostUpdateDTO;
import com.njydsz.userinfo.domain.dto.update.RoleUpdateDTO;
import com.njydsz.userinfo.domain.entity.Company;
import com.njydsz.userinfo.domain.entity.Department;
import com.njydsz.userinfo.domain.entity.Language;
import com.njydsz.userinfo.domain.entity.Menu;
import com.njydsz.userinfo.domain.entity.Post;
import com.njydsz.userinfo.domain.entity.Role;
import com.njydsz.userinfo.domain.entity.UserAccount;
import com.njydsz.userinfo.domain.vo.CompanyVO;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.domain.vo.LanguageVO;
import com.njydsz.userinfo.domain.vo.LoginVO;
import com.njydsz.userinfo.domain.vo.MenuTreeVO;
import com.njydsz.userinfo.domain.vo.MenuVO;
import com.njydsz.userinfo.domain.vo.PostVO;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;

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
   * <p>典型用法：{@code UserInfoConverter.INSTANT.entityToVO(company)}。
   */
  UserInfoConverter INSTANT = Mappers.getMapper(UserInfoConverter.class);

  // ===== Company =====

  /**
   * 公司实体 → 公司 VO
   *
   * @param entity 公司实体
   * @return 公司 VO（不含 deleted/createdBy 等内部字段）
   */
  CompanyVO entityToVO(Company entity);

  /**
   * 公司实体列表 → 公司 VO 列表
   *
   * @param entities 公司实体列表
   * @return 公司 VO 列表
   */
  List<CompanyVO> companyListToVO(List<Company> entities);

  /**
   * 公司新增 DTO → 公司实体
   *
   * <p>MpBaseEntity 的自动填充字段（id/deleted/revision/tenantId/createdBy/createdAt/updatedBy/updatedAt）
   * 通过 @Mapping(ignore = true) 忽略，由框架自动填充。
   *
   * @param dto 公司新增 DTO
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
  Company postDtoToEntity(CompanyCreateDTO dto);

  /**
   * 公司修改 DTO → 公司实体
   *
   * <p>保留 id 字段用于定位更新记录，自动填充字段中 updatedBy/updatedAt 由框架更新。
   *
   * @param dto 公司修改 DTO
   * @return 公司实体（含 id，用于条件更新）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  Company putDtoToEntity(CompanyUpdateDTO dto);

  // ===== Department =====

  /**
   * 部门实体 → 部门 VO（扁平结构）
   *
   * @param entity 部门实体
   * @return 部门 VO
   */
  DepartmentVO entityToVO(Department entity);

  /**
   * 部门实体列表 → 部门 VO 列表
   *
   * @param entities 部门实体列表
   * @return 部门 VO 列表
   */
  List<DepartmentVO> departmentListToVO(List<Department> entities);

  /**
   * 部门实体 → 部门树形 VO（含 children 字段）
   *
   * @param entity 部门实体
   * @return 部门树形 VO
   */
  DepartmentTreeVO entityToTreeVO(Department entity);

  /**
   * 部门实体列表 → 部门树形 VO 列表
   *
   * @param entities 部门实体列表
   * @return 部门树形 VO 列表
   */
  List<DepartmentTreeVO> departmentTreeListToVO(List<Department> entities);

  /**
   * 部门新增 DTO → 部门实体
   *
   * @param dto 部门新增 DTO
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
  Department postDtoToEntity(DepartmentCreateDTO dto);

  /**
   * 部门修改 DTO → 部门实体
   *
   * @param dto 部门修改 DTO
   * @return 部门实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  Department putDtoToEntity(DepartmentUpdateDTO dto);

  // ===== Language =====

  /**
   * 语言实体 → 语言 VO
   *
   * @param entity 语言实体
   * @return 语言 VO
   */
  LanguageVO entityToVO(Language entity);

  /**
   * 语言实体列表 → 语言 VO 列表
   *
   * @param entities 语言实体列表
   * @return 语言 VO 列表
   */
  List<LanguageVO> languageListToVO(List<Language> entities);

  /**
   * 语言新增 DTO → 语言实体
   *
   * @param dto 语言新增 DTO
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
  Language postDtoToEntity(LanguageCreateDTO dto);

  /**
   * 语言修改 DTO → 语言实体
   *
   * @param dto 语言修改 DTO
   * @return 语言实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  Language putDtoToEntity(LanguageUpdateDTO dto);

  // ===== Menu =====

  /**
   * 菜单实体 → 菜单 VO（扁平结构）
   *
   * @param entity 菜单实体
   * @return 菜单 VO
   */
  MenuVO entityToVO(Menu entity);

  /**
   * 菜单实体列表 → 菜单 VO 列表
   *
   * @param entities 菜单实体列表
   * @return 菜单 VO 列表
   */
  List<MenuVO> menuListToVO(List<Menu> entities);

  /**
   * 菜单实体 → 菜单树形 VO（含 children 字段）
   *
   * @param entity 菜单实体
   * @return 菜单树形 VO
   */
  MenuTreeVO entityToMenuTreeVO(Menu entity);

  /**
   * 菜单实体列表 → 菜单树形 VO 列表
   *
   * @param entities 菜单实体列表
   * @return 菜单树形 VO 列表
   */
  List<MenuTreeVO> menuTreeListToVO(List<Menu> entities);

  /**
   * 菜单新增 DTO → 菜单实体
   *
   * @param dto 菜单新增 DTO
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
  Menu postDtoToEntity(MenuCreateDTO dto);

  /**
   * 菜单修改 DTO → 菜单实体
   *
   * @param dto 菜单修改 DTO
   * @return 菜单实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  Menu putDtoToEntity(MenuUpdateDTO dto);

  // ===== Post =====

  /**
   * 岗位实体 → 岗位 VO
   *
   * @param entity 岗位实体
   * @return 岗位 VO
   */
  PostVO entityToVO(Post entity);

  /**
   * 岗位实体列表 → 岗位 VO 列表
   *
   * @param entities 岗位实体列表
   * @return 岗位 VO 列表
   */
  List<PostVO> postListToVO(List<Post> entities);

  /**
   * 岗位新增 DTO → 岗位实体
   *
   * @param dto 岗位新增 DTO
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
  Post postDtoToEntity(PostCreateDTO dto);

  /**
   * 岗位修改 DTO → 岗位实体
   *
   * @param dto 岗位修改 DTO
   * @return 岗位实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  Post putDtoToEntity(PostUpdateDTO dto);

  // ===== Role =====

  /**
   * 角色实体 → 角色 VO
   *
   * @param entity 角色实体
   * @return 角色 VO
   */
  RoleVO entityToVO(Role entity);

  /**
   * 角色实体列表 → 角色 VO 列表
   *
   * @param entities 角色实体列表
   * @return 角色 VO 列表
   */
  List<RoleVO> roleListToVO(List<Role> entities);

  /**
   * 角色新增 DTO → 角色实体
   *
   * @param dto 角色新增 DTO
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
  Role postDtoToEntity(RoleCreateDTO dto);

  /**
   * 角色修改 DTO → 角色实体
   *
   * @param dto 角色修改 DTO
   * @return 角色实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  Role putDtoToEntity(RoleUpdateDTO dto);

  // ===== UserAccount =====

  /**
   * 用户账号实体 → 用户账号 VO
   *
   * <p>自动排除 password、loginFailCount、lockedUntil 等敏感字段。
   *
   * @param entity 用户账号实体
   * @return 用户账号 VO（已脱敏）
   */
  UserAccountVO entityToVO(UserAccount entity);

  /**
   * 用户账号实体列表 → 用户账号 VO 列表
   *
   * @param entities 用户账号实体列表
   * @return 用户账号 VO 列表（已脱敏）
   */
  List<UserAccountVO> userAccountListToVO(List<UserAccount> entities);

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
  UserAccount createDtoToEntity(UserAccountCreateDTO dto);

  // ===== UserAccount → LoginVO.UserInfoVO =====
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
  LoginVO.UserInfoVO entityToUserInfoVO(UserAccount entity);
}
