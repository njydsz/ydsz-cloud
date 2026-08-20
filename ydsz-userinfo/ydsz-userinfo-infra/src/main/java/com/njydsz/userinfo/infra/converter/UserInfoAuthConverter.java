package com.njydsz.userinfo.infra.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.dto.LanguageCreateDTO;
import com.njydsz.userinfo.domain.dto.LanguageDTO;
import com.njydsz.userinfo.domain.dto.LanguageUpdateDTO;
import com.njydsz.userinfo.domain.dto.MenuCreateDTO;
import com.njydsz.userinfo.domain.dto.MenuDTO;
import com.njydsz.userinfo.domain.dto.MenuUpdateDTO;
import com.njydsz.userinfo.domain.dto.RoleCreateDTO;
import com.njydsz.userinfo.domain.dto.RoleDTO;
import com.njydsz.userinfo.domain.dto.RolePermissionDTO;
import com.njydsz.userinfo.domain.dto.RoleUpdateDTO;
import com.njydsz.userinfo.infra.entity.LanguageDO;
import com.njydsz.userinfo.infra.entity.MenuDO;
import com.njydsz.userinfo.infra.entity.RoleDO;
import com.njydsz.userinfo.infra.entity.RolePermissionDO;
import com.njydsz.userinfo.domain.vo.LanguageVO;
import com.njydsz.userinfo.domain.vo.MenuTreeVO;
import com.njydsz.userinfo.domain.vo.MenuVO;
import com.njydsz.userinfo.domain.vo.RolePermissionVO;
import com.njydsz.userinfo.domain.vo.RoleVO;

/**
 * 认证权限领域 MapStruct 转换器。
 *
 * <p>负责角色、权限、菜单、语言配置的 Entity ↔ VO / DTO → Entity 转换，涵盖：
 * Role、RolePermission、Menu、Language。
 *
 * <p>使用 Spring 注入模式（componentModel = "spring"），替代旧的静态单例 INSTANT 访问方式。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper(componentModel = "spring")
@Component
public interface UserInfoAuthConverter {

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
}
