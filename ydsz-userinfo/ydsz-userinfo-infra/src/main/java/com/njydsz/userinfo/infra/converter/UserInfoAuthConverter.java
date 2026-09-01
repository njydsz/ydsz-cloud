package com.njydsz.userinfo.infra.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.dto.LanguageDTO;
import com.njydsz.userinfo.domain.dto.MenuDTO;
import com.njydsz.userinfo.domain.dto.RoleDTO;
import com.njydsz.userinfo.domain.dto.RolePermissionDTO;
import com.njydsz.userinfo.domain.vo.LanguageVO;
import com.njydsz.userinfo.domain.vo.MenuTreeVO;
import com.njydsz.userinfo.domain.vo.MenuVO;
import com.njydsz.userinfo.domain.vo.RolePermissionVO;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.infra.entity.Language;
import com.njydsz.userinfo.infra.entity.Menu;
import com.njydsz.userinfo.infra.entity.Role;
import com.njydsz.userinfo.infra.entity.RolePermission;

/**
 * 认证权限领域 MapStruct 转换器。
 *
 * <p>负责角色、权限、菜单、语言配置的 Entity ↔ VO / DTO → Entity 转换，涵盖：
 * Role、RolePermission、Menu、Language。
 *
 * <p>使用 Spring 注入模式（componentModel = "spring"），替代旧的静态单例 INSTANT 访问方式。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Mapper(componentModel = "spring")
@Component
public interface UserInfoAuthConverter {

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
  Role dtoToEntity(RoleDTO dto);

  /**
   * 角色 DTO → 角色实体（更新场景）
   *
   * @param dto 角色 DTO（含 id）
   * @return 角色实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  Role dtoToEntityWithId(RoleDTO dto);

  // ===== RolePermission =====

  /**
   * 角色-权限关联实体 → VO
   *
   * @param entity 角色-权限关联实体
   * @return 角色-权限关联 VO
   */
  RolePermissionVO entityToVO(RolePermission entity);

  /**
   * 角色-权限关联实体列表 → VO 列表
   *
   * @param entities 角色-权限关联实体列表
   * @return 角色-权限关联 VO 列表
   */
  List<RolePermissionVO> rolePermissionListToVO(List<RolePermission> entities);

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
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  RolePermission dtoToEntity(RolePermissionDTO dto);

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
   * <p>children 由 Service 层构建树时填充，此处忽略避免 MapStruct 告警。
   *
   * @param entity 菜单实体
   * @return 菜单树形 VO
   */
  @Mapping(target = "children", ignore = true)
  MenuTreeVO entityToMenuTreeVO(Menu entity);

  /**
   * 菜单实体列表 → 菜单树形 VO 列表
   *
   * @param entities 菜单实体列表
   * @return 菜单树形 VO 列表
   */
  List<MenuTreeVO> menuTreeListToVO(List<Menu> entities);

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
  Menu dtoToEntity(MenuDTO dto);

  /**
   * 菜单 DTO → 菜单实体（更新场景）
   *
   * @param dto 菜单 DTO（含 id）
   * @return 菜单实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  Menu dtoToEntityWithId(MenuDTO dto);

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
  Language dtoToEntity(LanguageDTO dto);

  /**
   * 语言 DTO → 语言实体（更新场景）
   *
   * @param dto 语言 DTO（含 id）
   * @return 语言实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  Language dtoToEntityWithId(LanguageDTO dto);
}
