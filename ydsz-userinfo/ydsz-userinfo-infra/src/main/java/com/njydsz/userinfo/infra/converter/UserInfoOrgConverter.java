package com.njydsz.userinfo.infra.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.dto.CompanyDTO;
import com.njydsz.userinfo.domain.dto.CompanyDeptDTO;
import com.njydsz.userinfo.domain.dto.DepartmentDTO;
import com.njydsz.userinfo.domain.dto.PostDTO;
import com.njydsz.userinfo.domain.vo.CompanyDeptVO;
import com.njydsz.userinfo.domain.vo.CompanyTreeVO;
import com.njydsz.userinfo.domain.vo.CompanyVO;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.domain.vo.PostVO;
import com.njydsz.userinfo.infra.entity.Company;
import com.njydsz.userinfo.infra.entity.CompanyDept;
import com.njydsz.userinfo.infra.entity.Department;
import com.njydsz.userinfo.infra.entity.Post;

/**
 * 组织机构领域 MapStruct 转换器。
 *
 * <p>负责公司及下属组织机构的 Entity ↔ VO / DTO → Entity 转换，涵盖：
 * Company、CompanyDept、Department、Post。
 *
 * <p>使用 Spring 注入模式（componentModel = "spring"），替代旧的静态单例 INSTANT 访问方式。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Mapper(componentModel = "spring")
@Component
public interface UserInfoOrgConverter {

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
   * 公司实体 → 公司树形 VO（含 children 字段）
   *
   * <p>children / level / path 由 TreeBuilder 在 Service 层填充，此处忽略避免 MapStruct 告警。
   *
   * @param entity 公司实体
   * @return 公司树形 VO
   */
  @Mapping(target = "children", ignore = true)
  @Mapping(target = "level", ignore = true)
  @Mapping(target = "path", ignore = true)
  CompanyTreeVO entityToTreeVO(Company entity);

  /**
   * 公司实体列表 → 公司树形 VO 列表
   *
   * @param entities 公司实体列表
   * @return 公司树形 VO 列表
   */
  List<CompanyTreeVO> companyTreeListToVO(List<Company> entities);

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
  Company dtoToEntity(CompanyDTO dto);

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
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  Company dtoToEntityWithId(CompanyDTO dto);

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
   * <p>deptPath / children 由 Service 层构建树时填充，此处忽略避免 MapStruct 告警。
   *
   * @param entity 部门实体
   * @return 部门树形 VO
   */
  @Mapping(target = "deptPath", ignore = true)
  @Mapping(target = "children", ignore = true)
  DepartmentTreeVO entityToTreeVO(Department entity);

  /**
   * 部门实体列表 → 部门树形 VO 列表
   *
   * @param entities 部门实体列表
   * @return 部门树形 VO 列表
   */
  List<DepartmentTreeVO> departmentTreeListToVO(List<Department> entities);

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
  @Mapping(target = "leaderId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  Department dtoToEntity(DepartmentDTO dto);

  /**
   * 部门 DTO → 部门实体（更新场景）
   *
   * @param dto 部门 DTO（含 id）
   * @return 部门实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "leaderId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  Department dtoToEntityWithId(DepartmentDTO dto);

  // ===== CompanyDept =====

  /**
   * 公司-部门关联实体 → VO
   *
   * @param entity 公司-部门关联实体
   * @return 公司-部门关联 VO
   */
  CompanyDeptVO entityToVO(CompanyDept entity);

  /**
   * 公司-部门关联实体列表 → VO 列表
   *
   * @param entities 公司-部门关联实体列表
   * @return 公司-部门关联 VO 列表
   */
  List<CompanyDeptVO> companyDeptListToVO(List<CompanyDept> entities);

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
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  CompanyDept dtoToEntity(CompanyDeptDTO dto);

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
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  CompanyDept dtoToEntityWithId(CompanyDeptDTO dto);

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
  Post dtoToEntity(PostDTO dto);

  /**
   * 岗位 DTO → 岗位实体（更新场景）
   *
   * @param dto 岗位 DTO（含 id）
   * @return 岗位实体（含 id）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  Post dtoToEntityWithId(PostDTO dto);
}
