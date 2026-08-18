package com.njydsz.system.infra.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.njydsz.system.domain.vo.AppInfoVO;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.domain.vo.DictTypeVO;
import com.njydsz.system.domain.vo.EntityVersionVO;
import com.njydsz.system.domain.vo.TenantPlanMenuVO;
import com.njydsz.system.domain.vo.TenantPlanVO;
import com.njydsz.system.domain.vo.TenantVO;
import com.njydsz.system.domain.vo.VariableVO;

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
 *   <li>{@link AppInfo} → {@link AppInfoVO}
 *   <li>{@link Config} → {@link ConfigVO}
 *   <li>{@link DictItem} → {@link DictItemVO}
 *   <li>{@link DictType} → {@link DictTypeVO}
 *   <li>{@link EntityVersion} → {@link EntityVersionVO}
 *   <li>{@link Tenant} → {@link TenantVO}
 *   <li>{@link TenantPlan} → {@link TenantPlanVO}
 *   <li>{@link TenantPlanMenu} → {@link TenantPlanMenuVO}
 *   <li>{@link Variable} → {@link VariableVO}
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.domain.entity AppInfo / Config / DictItem / DictType / EntityVersion /
 *     Variable
 * @see com.njydsz.system.domain.vo AppInfoVO / ConfigVO / DictItemVO / DictTypeVO / EntityVersionVO /
 *     VariableVO
 */
@Mapper
public interface SystemConverter {

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
  AppInfoVO entityToVO(AppInfo entity);

  /**
   * 应用信息实体列表 → 应用信息 VO 列表
   *
   * @param entities 应用信息实体列表
   * @return 应用信息 VO 列表
   */
  List<AppInfoVO> appInfoListToVO(List<AppInfo> entities);

  // ===== Config =====

  /**
   * 系统配置实体 → 系统配置 VO
   *
   * @param entity 系统配置实体
   * @return 系统配置 VO
   */
  ConfigVO entityToVO(Config entity);

  /**
   * 系统配置实体列表 → 系统配置 VO 列表
   *
   * @param entities 系统配置实体列表
   * @return 系统配置 VO 列表
   */
  List<ConfigVO> configListToVO(List<Config> entities);

  // ===== DictItem =====

  /**
   * 字典项实体 → 字典项 VO
   *
   * @param entity 字典项实体
   * @return 字典项 VO
   */
  DictItemVO entityToVO(DictItem entity);

  /**
   * 字典项实体列表 → 字典项 VO 列表
   *
   * @param entities 字典项实体列表
   * @return 字典项 VO 列表
   */
  List<DictItemVO> dictItemListToVO(List<DictItem> entities);

  // ===== DictType =====

  /**
   * 字典类型实体 → 字典类型 VO
   *
   * @param entity 字典类型实体
   * @return 字典类型 VO
   */
  DictTypeVO entityToVO(DictType entity);

  /**
   * 字典类型实体列表 → 字典类型 VO 列表
   *
   * @param entities 字典类型实体列表
   * @return 字典类型 VO 列表
   */
  List<DictTypeVO> dictTypeListToVO(List<DictType> entities);

  // ===== EntityVersion =====

  /**
   * 实体版本 → 实体版本 VO（不含 snapshotJson）
   *
   * @param entity 实体版本
   * @return 实体版本 VO
   */
  EntityVersionVO entityVersionToVO(EntityVersion entity);

  /**
   * 实体版本列表 → 实体版本 VO 列表
   *
   * @param entities 实体版本列表
   * @return 实体版本 VO 列表
   */
  List<EntityVersionVO> entityVersionListToVO(List<EntityVersion> entities);

  // ===== Tenant =====

  /**
   * 租户实体 → 租户 VO
   *
   * @param entity 租户实体
   * @return 租户 VO
   */
  TenantVO entityToVO(Tenant entity);

  /**
   * 租户实体列表 → 租户 VO 列表
   *
   * @param entities 租户实体列表
   * @return 租户 VO 列表
   */
  List<TenantVO> tenantListToVO(List<Tenant> entities);

  // ===== TenantPlan =====

  /**
   * 套餐实体 → 套餐 VO
   *
   * @param entity 套餐实体
   * @return 套餐 VO
   */
  TenantPlanVO entityToVO(TenantPlan entity);

  /**
   * 套餐实体列表 → 套餐 VO 列表
   *
   * @param entities 套餐实体列表
   * @return 套餐 VO 列表
   */
  List<TenantPlanVO> planListToVO(List<TenantPlan> entities);

  // ===== TenantPlanMenu =====

  /**
   * 套餐-菜单关联实体 → 套餐-菜单关联 VO
   *
   * @param entity 套餐-菜单关联实体
   * @return 套餐-菜单关联 VO
   */
  TenantPlanMenuVO entityToVO(TenantPlanMenu entity);

  /**
   * 套餐-菜单关联实体列表 → 套餐-菜单关联 VO 列表
   *
   * @param entities 套餐-菜单关联实体列表
   * @return 套餐-菜单关联 VO 列表
   */
  List<TenantPlanMenuVO> planMenuListToVO(List<TenantPlanMenu> entities);

  // ===== Variable =====

  /**
   * 系统变量实体 → 系统变量 VO
   *
   * @param entity 系统变量实体
   * @return 系统变量 VO
   */
  VariableVO entityToVO(Variable entity);

  /**
   * 系统变量实体列表 → 系统变量 VO 列表
   *
   * @param entities 系统变量实体列表
   * @return 系统变量 VO 列表
   */
  List<VariableVO> variableListToVO(List<Variable> entities);
}
