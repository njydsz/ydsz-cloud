package com.njydsz.userinfo.infra.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.dto.CompanyDTO;
import com.njydsz.userinfo.domain.dto.CompanyDeptDTO;
import com.njydsz.userinfo.domain.dto.DepartmentDTO;
import com.njydsz.userinfo.domain.dto.PostDTO;
import com.njydsz.userinfo.infra.entity.CompanyDeptDO;
import com.njydsz.userinfo.infra.entity.CompanyDO;
import com.njydsz.userinfo.infra.entity.DepartmentDO;
import com.njydsz.userinfo.infra.entity.PostDO;
import com.njydsz.userinfo.domain.vo.CompanyDeptVO;
import com.njydsz.userinfo.domain.vo.CompanyTreeVO;
import com.njydsz.userinfo.domain.vo.CompanyVO;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.domain.vo.PostVO;

/**
 * 组织机构领域 MapStruct 转换器。
 *
 * <p>负责公司及下属组织机构的 Entity ↔ VO / DTO → Entity 转换，涵盖：
 * Company、CompanyDept、Department、Post。
 *
 * <p>使用 Spring 注入模式（componentModel = "spring"），替代旧的静态单例 INSTANT 访问方式。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper(componentModel = "spring")
@Component
public interface UserInfoOrgConverter {

  // ===== CompanyDO =====

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
   * 公司-部门关联 DTO → 实体（创建场景）
   *
   * @param dto 公司-部门关联 DTO
   * @return 公司-部门关联实体（未持久化）
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

  /**
   * 公司-部门关联 DTO → 实体（更新场景）
   *
   * <p>保留 id 字段用于定位更新记录，自动填充字段中 updatedBy/updatedAt 由框架更新。
   *
   * @param dto 公司-部门关联 DTO（含 id）
   * @return 公司-部门关联实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  CompanyDeptDO dtoToEntityWithId(CompanyDeptDTO dto);

  // ===== PostDO =====

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
}
