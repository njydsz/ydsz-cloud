package com.njydsz.system.infra.converter;
import java.util.List;

import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import com.njydsz.system.domain.dto.AppInfoDTO;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.dto.DictItemDTO;
import com.njydsz.system.domain.dto.DictTypeDTO;
import com.njydsz.system.domain.dto.EntityVersionDTO;
import com.njydsz.system.domain.dto.TenantDTO;
import com.njydsz.system.domain.dto.TenantPlanDTO;
import com.njydsz.system.domain.dto.VariableDTO;
import com.njydsz.system.domain.vo.AppInfoVO;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.domain.vo.DictTypeVO;
import com.njydsz.system.domain.vo.EntityVersionVO;
import com.njydsz.system.domain.vo.TenantPlanMenuVO;
import com.njydsz.system.domain.vo.TenantPlanVO;
import com.njydsz.system.domain.vo.TenantVO;
import com.njydsz.system.domain.vo.VariableVO;
import com.njydsz.system.infra.entity.AppInfoDO;
import com.njydsz.system.infra.entity.ConfigDO;
import com.njydsz.system.infra.entity.DictItemDO;
import com.njydsz.system.infra.entity.DictTypeDO;
import com.njydsz.system.infra.entity.EntityVersionDO;
import com.njydsz.system.infra.entity.TenantDO;
import com.njydsz.system.infra.entity.TenantPlanDO;
import com.njydsz.system.infra.entity.TenantPlanMenuDO;
import com.njydsz.system.infra.entity.VariableDO;




/**
 * 系统配置模块统一 MapStruct 转换器
 *
 * <p>承担「系统模块」所有 Entity ↔ VO 的双向转换，遵循大厂标准的<b>单一转换器</b>模式： 同一业务域的转换规则集中维护，避免散落在各 Service 的 {@code
 * BeanUtils.copyProperties} 调用。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>使用 MapStruct 注解处理器，<b>编译期</b>生成实现类（{@code SystemConverterImpl.java}）， 性能优于反射（{@code
 *       BeanUtils}）
 *   <li>通过 {@link #INSTANT} 单例访问，零依赖注入，开箱即用
 *   <li>同名字段自动映射；不同名字段通过 {@code @Mapping} 显式标注
 *   <li><b>不暴露敏感字段</b>：{@code AppInfo.appSecret} 永远不会被转换到 VO
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 单个转换
 * AppInfoVO vo = SystemConverter.INSTANT.entityToVO(entity);
 *
 * // 批量转换
 * List<ConfigVO> vos = SystemConverter.INSTANT.configListToVO(entities);
 * }</pre>
 *
 * <p><b>覆盖范围（8 大实体 / 16 个方法）：</b>
 *
 * <ul>
 *   <li>{@link AppInfoDO} → {@link AppInfoVO}
 *   <li>{@link ConfigDO} → {@link ConfigVO}
 *   <li>{@link DictItemDO} → {@link DictItemVO}
 *   <li>{@link DictTypeDO} → {@link DictTypeVO}
 *   <li>{@link EntityVersionDO} → {@link EntityVersionVO}
 *   <li>{@link TenantDO} → {@link TenantVO}
 *   <li>{@link TenantPlanDO} → {@link TenantPlanVO}
 *   <li>{@link TenantPlanMenuDO} → {@link TenantPlanMenuVO}
 *   <li>{@link VariableDO} → {@link VariableVO}
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.infra.entity AppInfoDO / ConfigDO / DictItemDO / DictTypeDO / EntityVersionDO /
 *     VariableDO
 * @see com.njydsz.system.domain.vo AppInfoVO / ConfigVO / DictItemVO / DictTypeVO / EntityVersionVO /
 *     VariableVO
 */
@Mapper
public interface SystemConverter {

  /** 单例实例 */
  SystemConverter INSTANT = Mappers.getMapper(SystemConverter.class);

  // ===== AppInfo =====

  /**
   * 应用信息实体 → 应用信息 VO
   *
   * <p>自动排除 appSecret 等敏感字段。
   *
   * @param entity 应用信息实体
   * @return 应用信息 VO
   */
  AppInfoVO entityToVO(AppInfoDO entity);

  /**
   * 应用信息实体列表 → 应用信息 VO 列表
   *
   * @param entities 应用信息实体列表
   * @return 应用信息 VO 列表
   */
  List<AppInfoVO> appInfoListToVO(List<AppInfoDO> entities);

  /**
   * 应用信息 DTO → 应用信息实体
   *
   * <p>用于创建应用信息场景。MpBaseEntity 的自动填充字段通过 @Mapping(ignore = true) 忽略。
   *
   * @param dto 应用信息 DTO
   * @return 应用信息实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  AppInfoDO dtoToEntity(AppInfoDTO dto);

  /**
   * 应用信息 DTO（含 ID）→ 应用信息实体
   *
   * <p>用于更新应用信息场景，保留 id 字段用于定位更新记录。
   *
   * @param dto 应用信息 DTO（含 id）
   * @return 应用信息实体（含 id，用于条件更新）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  AppInfoDO dtoToEntityWithId(AppInfoDTO dto);

  /**
   * 应用信息 VO → 应用信息 DTO
   *
   * @param vo 应用信息 VO
   * @return 应用信息 DTO
   */
  AppInfoDTO voToDto(AppInfoVO vo);

  // ===== Config =====

  /**
   * 系统配置实体 → 系统配置 VO
   *
   * @param entity 系统配置实体
   * @return 系统配置 VO
   */
  ConfigVO entityToVO(ConfigDO entity);

  /**
   * 系统配置实体列表 → 系统配置 VO 列表
   *
   * @param entities 系统配置实体列表
   * @return 系统配置 VO 列表
   */
  List<ConfigVO> configListToVO(List<ConfigDO> entities);

  /**
   * 系统配置 DTO → 系统配置实体
   *
   * <p>用于创建系统配置场景。MpBaseEntity 的自动填充字段通过 @Mapping(ignore = true) 忽略。
   *
   * @param dto 系统配置 DTO
   * @return 系统配置实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  ConfigDO dtoToEntity(ConfigDTO dto);

  /**
   * 系统配置 VO → 系统配置 DTO
   *
   * @param vo 系统配置 VO
   * @return 系统配置 DTO
   */
  ConfigDTO voToDto(ConfigVO vo);

  /**
   * 系统配置 DTO（含 ID）→ 系统配置实体
   *
   * <p>用于更新系统配置场景，保留 id 字段用于定位更新记录。
   *
   * @param dto 系统配置 DTO（含 id）
   * @return 系统配置实体（含 id，用于条件更新）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  ConfigDO dtoToEntityWithId(ConfigDTO dto);

  /**
   * 系统配置 DTO 列表 → 系统配置实体列表
   *
   * @param dtos 系统配置 DTO 列表
   * @return 系统配置实体列表
   */
  @IterableMapping(qualifiedByName = "configDtoToEntity")
  List<ConfigDO> configDtosToEntities(List<ConfigDTO> dtos);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Named("configDtoToEntity")
  ConfigDO configDtoToEntityInternal(ConfigDTO dto);

  // ===== DictItem =====

  /**
   * 字典项实体 → 字典项 VO
   *
   * @param entity 字典项实体
   * @return 字典项 VO
   */
  @Mapping(target = "children", ignore = true)
  @Mapping(target = "level", ignore = true)
  @Mapping(target = "path", ignore = true)
  DictItemVO entityToVO(DictItemDO entity);

  /**
   * 字典项实体列表 → 字典项 VO 列表
   *
   * @param entities 字典项实体列表
   * @return 字典项 VO 列表
   */
  List<DictItemVO> dictItemListToVO(List<DictItemDO> entities);

  /**
   * 字典项 DTO → 字典项实体
   *
   * <p>用于创建/更新字典项场景。MpBaseEntity 的自动填充字段通过 @Mapping(ignore = true) 忽略。
   *
   * @param dto 字典项 DTO
   * @return 字典项实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  DictItemDO dtoToEntity(DictItemDTO dto);

  /**
   * 字典项 DTO（含 ID）→ 字典项实体
   *
   * <p>用于更新字典项场景，保留 id 字段用于定位更新记录。
   *
   * @param dto 字典项 DTO（含 id）
   * @return 字典项实体（含 id，用于条件更新）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  DictItemDO dtoToEntityWithId(DictItemDTO dto);

  /**
   * 字典项 DTO 列表 → 字典项实体列表
   *
   * @param dtos 字典项 DTO 列表
   * @return 字典项实体列表
   */
  @IterableMapping(qualifiedByName = "dictItemDtoToEntity")
  List<DictItemDO> dictItemDtosToEntities(List<DictItemDTO> dtos);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Named("dictItemDtoToEntity")
  DictItemDO dictItemDtoToEntityInternal(DictItemDTO dto);

  // ===== DictType =====

  /**
   * 字典类型实体 → 字典类型 VO
   *
   * @param entity 字典类型实体
   * @return 字典类型 VO
   */
  DictTypeVO entityToVO(DictTypeDO entity);

  /**
   * 字典类型实体列表 → 字典类型 VO 列表
   *
   * @param entities 字典类型实体列表
   * @return 字典类型 VO 列表
   */
  List<DictTypeVO> dictTypeListToVO(List<DictTypeDO> entities);

  /**
   * 字典类型 DTO → 字典类型实体
   *
   * <p>用于创建字典类型场景。MpBaseEntity 的自动填充字段通过 @Mapping(ignore = true) 忽略。
   *
   * @param dto 字典类型 DTO
   * @return 字典类型实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  DictTypeDO dtoToEntity(DictTypeDTO dto);

  /**
   * 字典类型 DTO（含 ID）→ 字典类型实体
   *
   * <p>用于更新字典类型场景，保留 id 字段用于定位更新记录。
   *
   * @param dto 字典类型 DTO（含 id）
   * @return 字典类型实体（含 id，用于条件更新）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  DictTypeDO dtoToEntityWithId(DictTypeDTO dto);

  // ===== EntityVersion =====

  /**
   * 实体版本 → 实体版本 VO（不含 snapshotJson）
   *
   * @param entity 实体版本
   * @return 实体版本 VO
   */
  EntityVersionVO entityVersionToVO(EntityVersionDO entity);

  /**
   * 实体版本列表 → 实体版本 VO 列表
   *
   * @param entities 实体版本列表
   * @return 实体版本 VO 列表
   */
  List<EntityVersionVO> entityVersionListToVO(List<EntityVersionDO> entities);

  /**
   * 实体版本创建 DTO → 实体版本实体
   *
   * <p>用于创建实体版本场景。MpBaseEntity 的自动填充字段通过 @Mapping(ignore = true) 忽略。
   *
   * @param dto 实体版本创建 DTO
   * @return 实体版本实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "effectiveDate", ignore = true)
  EntityVersionDO dtoToEntity(EntityVersionDTO dto);

  // ===== Tenant =====

  /**
   * 租户实体 → 租户 VO
   *
   * @param entity 租户实体
   * @return 租户 VO
   */
  TenantVO entityToVO(TenantDO entity);

  /**
   * 租户实体列表 → 租户 VO 列表
   *
   * @param entities 租户实体列表
   * @return 租户 VO 列表
   */
  List<TenantVO> tenantListToVO(List<TenantDO> entities);

  /**
   * 租户 DTO → 租户实体
   *
   * <p>用于创建租户场景。MpBaseEntity 的自动填充字段通过 @Mapping(ignore = true) 忽略。
   *
   * @param dto 租户 DTO
   * @return 租户实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  TenantDO dtoToEntity(TenantDTO dto);

  /**
   * 租户 DTO（含 ID）→ 租户实体
   *
   * <p>用于更新租户场景，保留 id 字段用于定位更新记录。
   *
   * @param dto 租户 DTO（含 id）
   * @return 租户实体（含 id，用于条件更新）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  TenantDO dtoToEntityWithId(TenantDTO dto);

  // ===== TenantPlan =====

  /**
   * 套餐实体 → 套餐 VO
   *
   * @param entity 套餐实体
   * @return 套餐 VO
   */
  TenantPlanVO entityToVO(TenantPlanDO entity);

  /**
   * 套餐实体列表 → 套餐 VO 列表
   *
   * @param entities 套餐实体列表
   * @return 套餐 VO 列表
   */
  List<TenantPlanVO> planListToVO(List<TenantPlanDO> entities);

  /**
   * 套餐 DTO → 套餐实体
   *
   * <p>用于创建套餐场景。MpBaseEntity 的自动填充字段通过 @Mapping(ignore = true) 忽略。
   *
   * @param dto 套餐 DTO
   * @return 套餐实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  TenantPlanDO dtoToEntity(TenantPlanDTO dto);

  /**
   * 套餐 DTO（含 ID）→ 套餐实体
   *
   * <p>用于更新套餐场景，保留 id 字段用于定位更新记录。
   *
   * @param dto 套餐 DTO（含 id）
   * @return 套餐实体（含 id，用于条件更新）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  TenantPlanDO dtoToEntityWithId(TenantPlanDTO dto);

  // ===== TenantPlanMenu =====

  /**
   * 套餐-菜单关联实体 → 套餐-菜单关联 VO
   *
   * @param entity 套餐-菜单关联实体
   * @return 套餐-菜单关联 VO
   */
  TenantPlanMenuVO entityToVO(TenantPlanMenuDO entity);

  /**
   * 套餐-菜单关联实体列表 → 套餐-菜单关联 VO 列表
   *
   * @param entities 套餐-菜单关联实体列表
   * @return 套餐-菜单关联 VO 列表
   */
  List<TenantPlanMenuVO> planMenuListToVO(List<TenantPlanMenuDO> entities);

  /**
   * 套餐 ID + 菜单 ID → 套餐-菜单关联实体
   *
   * <p>用于创建套餐-菜单关联场景。MpBaseEntity 的自动填充字段通过 @Mapping(ignore = true) 忽略。
   *
   * @param planId 套餐 ID
   * @param menuId 菜单 ID
   * @return 套餐-菜单关联实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "status", ignore = true)
  TenantPlanMenuDO dtoToEntity(String planId, String menuId);

  // ===== Variable =====

  /**
   * 系统变量实体 → 系统变量 VO
   *
   * @param entity 系统变量实体
   * @return 系统变量 VO
   */
  @Mapping(target = "status", ignore = true)
  VariableVO entityToVO(VariableDO entity);

  /**
   * 系统变量实体列表 → 系统变量 VO 列表
   *
   * @param entities 系统变量实体列表
   * @return 系统变量 VO 列表
   */
  List<VariableVO> variableListToVO(List<VariableDO> entities);

  /**
   * 系统变量 DTO → 系统变量实体
   *
   * <p>用于创建系统变量场景。MpBaseEntity 的自动填充字段通过 @Mapping(ignore = true) 忽略。
   *
   * @param dto 系统变量 DTO
   * @return 系统变量实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  VariableDO dtoToEntity(VariableDTO dto);

  /**
   * 系统变量 DTO（含 ID）→ 系统变量实体
   *
   * <p>用于更新系统变量场景，保留 id 字段用于定位更新记录。
   *
   * @param dto 系统变量 DTO（含 id）
   * @return 系统变量实体（含 id，用于条件更新）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  VariableDO dtoToEntityWithId(VariableDTO dto);
}
