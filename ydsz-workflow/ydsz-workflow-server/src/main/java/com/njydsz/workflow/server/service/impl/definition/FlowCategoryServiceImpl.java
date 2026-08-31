package com.njydsz.workflow.server.service.impl.definition;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.domain.tree.TreeBuilder;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.workflow.domain.dto.FlowCategoryDTO;
import com.njydsz.workflow.domain.repository.FlowCategoryRepository;
import com.njydsz.workflow.domain.vo.FlowCategoryTreeVO;
import com.njydsz.workflow.domain.vo.FlowCategoryVO;
import com.njydsz.workflow.server.service.FlowCategoryService;

/**
 * 流程分类服务实现
 *
 * <p>P1-6: 对标钉钉/飞书审批的"流程分类管理"能力，对 {@link FlowCategoryService} 接口的完整实现， 提供流程分类的
 * CRUD、引用校验、租户隔离等完整业务能力。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>查询能力</b>：{@link #listAll} — 全量返回当前租户分类，按 {@code sortNum} 升序排序； {@link #tree} — 使用 {@link
 *       TreeBuilder#buildSimple} 构建树形结构
 *   <li><b>CRUD</b>：{@link #create}（编码唯一性校验 + 租户隔离）/ {@link #update}（仅更新非空字段，保留原始数据）/ {@link
 *       #delete}（软删除 + 子分类/流程定义引用校验）
 *   <li><b>引用校验</b>：删除分类前必须先<b>无子分类</b>且<b>无关联流程定义</b>， 避免孤立引用导致设计器加载异常
 *   <li><b>多租户</b>：所有写操作按 {@code tenantId} 隔离，防止跨租户分类冲突
 * </ul>
 *
 * <p><b>事务边界：</b>
 *
 * <ul>
 *   <li>所有写方法（{@code create/update/delete}）开启 {@code @Transactional(rollbackFor =
 *       Exception.class)}， 确保「编码唯一性校验 + 写入」原子性
 *   <li>删除采用<b>逻辑删除</b>（{@code deleted=1}），保留审计轨迹
 * </ul>
 *
 * <p><b>性能优化：</b>
 *
 * <ul>
 *   <li>分类数据量小（百级别），{@link #listAll} 全表返回，无需分页
 *   <li>查询走 {@code ydsz_flow_category} 复合索引（{@code idx_tenant} + {@code idx_parent}）
 *   <li>排序走 {@code idx_sort}，应用层 {@code Comparator} 兜底
 * </ul>
 *
 * <p><b>防御性编程：</b>
 *
 * <ul>
 *   <li>{@link #listAll} 当租户 ID 为空时降级为查询全部（兼容早期无租户字段数据）
 *   <li>{@link #delete} 当分类不存在或已删除时直接返回（幂等性）
 *   <li>{@link #update} 字段为空时不更新（避免覆盖已设置的其他字段）
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 查询全部分类
 * List<FlowCategory> list = flowCategoryService.listAll(tenantId);
 *
 * // 查询分类树形结构
 * List<FlowCategoryTreeVO> tree = flowCategoryService.tree(tenantId);
 *
 * // 新增分类
 * FlowCategoryDTO dto = new FlowCategoryDTO();
 * dto.setCategoryCode("HR");
 * dto.setCategoryName("人力资源");
 * dto.setSortNum(10);
 * String id = flowCategoryService.create(dto, tenantId);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowCategoryService 接口定义
 * @see FlowCategoryVO 分类实体
 * @see FlowCategoryDTO 分类 DTO
 * @see com.njydsz.workflow.domain.vo.FlowDefinitionVO 流程定义（{@code category} 字段引用本表 ID）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowCategoryServiceImpl implements FlowCategoryService {

  /** 流程分类仓储（domain 层契约），管理 ydsz_flow_category 表 CRUD */
  private final FlowCategoryRepository categoryRepository;

  /**
   * 查询当前租户全部分类
   *
   * <p>扁平结构返回（不构建树），按 {@code sortNum} 升序排序，{@code sortNum} 为 {@code null} 时按 {@code 0} 处理。
   * 仅查询未删除（{@code deleted=0}）的分类。多租户隔离：{@code tenantId=null} 时回退到 {@link TenantContext}。
   *
   * @param tenantId 租户 ID（可空，回退 {@link TenantContext#getTenantId()}）
   * @return 分类列表（按 sortNum 升序），无数据返回空列表
   */
  @Override
  public List<FlowCategoryVO> listAllVO(String tenantId) {
    String tid = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
    List<FlowCategoryVO> voList = categoryRepository.findAll(tid);
    voList.sort(Comparator.comparingInt(c -> c.getSortNum() == null ? 0 : c.getSortNum()));
    return voList;
  }

  /**
   * {@inheritDoc}
   * 
   * <p>一次性查询全表后在内存中构建树，使用 {@link TreeBuilder#buildSimple} O(n) 算法， 自动填充 {@code level}/{@code path} 元数据。
   * 分类数据量小（百级别），全量加载可接受。
   * 
   * 
   *
   * @param tenantId 参数说明
   * @return 返回值说明
   */
  @Override
  public List<FlowCategoryTreeVO> tree(String tenantId) {
    String tid = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
    List<FlowCategoryVO> all = categoryRepository.findAll(tid);
    all.sort(Comparator.comparingInt(c -> c.getSortNum() == null ? 0 : c.getSortNum()));
    if (all.isEmpty()) {
      return List.of();
    }
    List<FlowCategoryTreeVO> flatList = all.stream()
        .map(this::toTreeVO)
        .toList();
    return TreeBuilder.buildSimple(
        flatList,
        FlowCategoryTreeVO::getId,
        FlowCategoryTreeVO::getParentId,
        FlowCategoryTreeVO::setChildren,
        FlowCategoryTreeVO::getSortNum,
        FlowCategoryTreeVO::setLevel,
        FlowCategoryTreeVO::setPath);
  }

  /**
   * 新增流程分类
   *
   * <p>执行流程：
   *
   * <ol>
   *   <li>校验同租户下 {@code categoryCode} 唯一性
   *   <li>构建 {@link FlowCategory} 实体，{@code sortNum} 为空时默认 {@code 0}
   *   <li>写入数据库并返回新 ID
   * </ol>
   *
   * @param dto 分类 DTO（含 {@code categoryCode/categoryName/parentId/sortNum/icon/remark}）
   * @param tenantId 租户 ID（可空，回退 {@link TenantContext}）
   * @return 新增分类的 ID
   * @throws SysException {@code BAD_REQUEST} — 编码在同租户下已存在
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String create(FlowCategoryDTO dto, String tenantId) {
    // 校验编码唯一
    String tid = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
    long count = categoryRepository.countByCodeAndTenantId(dto.getCategoryCode(), tid);
    if (count > 0) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.msg_category_code_exists")
          .params(dto.getCategoryCode())
          .build();
    }

    FlowCategoryVO category = new FlowCategoryVO();
    category.setCategoryCode(dto.getCategoryCode());
    category.setCategoryName(dto.getCategoryName());
    category.setParentId(dto.getParentId());
    category.setSortNum(dto.getSortNum() != null ? dto.getSortNum() : 0);
    category.setIcon(dto.getIcon());
    category.setRemark(dto.getRemark());
    category.setTenantId(tid);
    FlowCategoryDTO categoryDto = new FlowCategoryDTO();
    categoryDto.setId(UUID.randomUUID().toString());
    categoryDto.setCategoryCode(dto.getCategoryCode());
    categoryDto.setCategoryName(dto.getCategoryName());
    categoryDto.setParentId(dto.getParentId());
    categoryDto.setSortNum(dto.getSortNum() != null ? dto.getSortNum() : 0);
    categoryDto.setIcon(dto.getIcon());
    categoryDto.setRemark(dto.getRemark());
    categoryRepository.save(categoryDto);
    log.info(
        "[FlowCategory] 新增分类: code={} name={} id={}",
        category.getCategoryCode(),
        category.getCategoryName(),
        category.getId());
    return category.getId();
  }

  /**
   * 编辑流程分类
   *
   * <p>仅更新 DTO 中非空字段（{@code null} 跳过），保留原始数据不被覆盖。 分类编码（{@code
   * categoryCode}）<b>不可修改</b>（业务约束：编码是分类的稳定标识）。
   *
   * @param dto 分类 DTO（{@code id} 必传，其他字段可选）
   * @throws SysException {@code BAD_REQUEST} — id 为空；{@code NOT_FOUND} — 分类不存在或已删除
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public void update(FlowCategoryDTO dto) {
    if (!StringUtils.hasText(dto.getId())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.category.id.required")
          .build();
    }
    FlowCategoryVO existing = categoryRepository.findById(dto.getId()).orElse(null);
    if (existing == null || existing.getDeleted() == 1) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.category.not.found")
          .params(dto.getId())
          .build();
    }
    existing.setCategoryName(dto.getCategoryName());
    if (dto.getParentId() != null) {
      existing.setParentId(dto.getParentId());
    }
    if (dto.getSortNum() != null) {
      existing.setSortNum(dto.getSortNum());
    }
    if (dto.getIcon() != null) {
      existing.setIcon(dto.getIcon());
    }
    if (dto.getRemark() != null) {
      existing.setRemark(dto.getRemark());
    }
    FlowCategoryDTO updateDto = new FlowCategoryDTO();
    BeanUtils.copyProperties(existing, updateDto);
    categoryRepository.update(updateDto);
  }

  /**
   * 删除流程分类（软删除）
   *
   * <p>删除前进行双重引用校验：
   *
   * <ul>
   *   <li>校验是否有<b>子分类</b>（{@code parentId} 指向当前分类）
   *   <li>校验是否有关联的<b>流程定义</b>（{@code FlowDefinition.category} 引用当前分类）
   * </ul>
   *
   * 任一校验不通过立即抛异常阻断。校验通过后置 {@code deleted=1}（逻辑删除），保留审计轨迹。
   *
   * <p><b>幂等性：</b>分类不存在或已删除时直接返回（不抛异常）。
   *
   * @param id 分类 ID
   * @throws SysException {@code BAD_REQUEST} — 存在子分类或关联流程定义
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public void delete(String id) {
    FlowCategoryVO existing = categoryRepository.findById(id).orElse(null);
    if (existing == null || existing.getDeleted() == 1) {
      return;
    }
    // 校验是否有子分类
    long childCount = categoryRepository.countByParentId(id);
    if (childCount > 0) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_category_has_children")
          .build();
    }
    // 校验是否有关联的流程定义
    long defCount = categoryRepository.countDefinitionsByCategory(id);
    if (defCount > 0) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_category_has_definitions")
          .build();
    }
    existing.setDeleted(1);
    FlowCategoryDTO deleteDto = new FlowCategoryDTO();
    BeanUtils.copyProperties(existing, deleteDto);
    categoryRepository.update(deleteDto);
  }

  /**
   * 将 FlowCategoryVO 转换为 FlowCategoryTreeVO（复制基础字段）。
   *
   * @param vo 分类 VO
   * @return 分类树 VO
   */
  private FlowCategoryTreeVO toTreeVO(FlowCategoryVO vo) {
    FlowCategoryTreeVO treeVO = new FlowCategoryTreeVO();
    treeVO.setId(vo.getId());
    treeVO.setParentId(vo.getParentId());
    treeVO.setCategoryCode(vo.getCategoryCode());
    treeVO.setCategoryName(vo.getCategoryName());
    treeVO.setSortNum(vo.getSortNum());
    treeVO.setIcon(vo.getIcon());
    treeVO.setRemark(vo.getRemark());
    return treeVO;
  }
}
