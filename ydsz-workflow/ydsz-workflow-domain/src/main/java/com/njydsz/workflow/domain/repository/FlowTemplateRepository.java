package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.vo.FlowTemplateVO;


/**
 * 流程模板仓储接口（domain 层契约）。
 *
 * <p>定义流程模板（ydsz_flow_template）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作模板聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowTemplateVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（code / categoryId / tenantId 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FlowTemplateRepository {

  /**
   * 保存流程模板（新增）。
   *
   * @param vo 流程模板 VO
   * @return 保存后的流程模板 VO（含生成的 id 与审计字段）
   */
  FlowTemplateVO save(FlowTemplateVO vo);

  /**
   * 根据 ID 查询流程模板。
   *
   * @param id 模板 ID
   * @return 流程模板 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowTemplateVO> findById(String id);

  /**
   * 根据模板编码查询流程模板。
   *
   * @param code 模板编码
   * @return 流程模板 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowTemplateVO> findByCode(String code);

  /**
   * 查询所有流程模板列表。
   *
   * @param tenantId 租户 ID（可选）
   * @return 流程模板 VO 列表
   */
  List<FlowTemplateVO> findAll(String tenantId);

  /**
   * 根据分类 ID 查询模板列表。
   *
   * @param categoryId 分类 ID
   * @return 流程模板 VO 列表
   */
  List<FlowTemplateVO> findByCategoryId(String categoryId);

  /**
   * 根据 ID 删除流程模板。
   *
   * @param id 模板 ID
   */
  void deleteById(String id);

  /**
   * 更新流程模板。
   *
   * @param vo 流程模板 VO（含 id）
   * @return 更新后的流程模板 VO
   */
  FlowTemplateVO update(FlowTemplateVO vo);

  /**
   * 查询分类下的默认模板（最新版本）。
   *
   * <p>返回 {@code category = ? AND tenantId = ? AND isLatest = 1} 的模板，
   * 每个分类下只有一个默认模板（最新版本）。用于流程发起时按分类获取推荐模板。
   *
   * @param businessType 业务类型（模板分类）
   * @param tenantId 租户 ID
   * @return 默认模板 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowTemplateVO> findDefaultByCategory(String businessType, String tenantId);

  /**
   * 按模板编码查询最新版本模板。
   *
   * @param templateCode 模板编码
   * @return 模板 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowTemplateVO> findByTemplateCode(String templateCode);

  /**
   * 按分类查询所有最新版本模板。
   *
   * <p>返回 {@code category = ? AND isLatest = 1} 的模板列表，{@code null} 或空表示全部分类。
   *
   * @param category 模板分类（可为 null，表示全部分类）
   * @return 模板 VO 列表
   */
  List<FlowTemplateVO> findLatestByCategory(String category);

  /**
   * 递增模板使用次数。
   *
   * @param templateCode 模板编码
   */
  void incrementUseCount(String templateCode);

  /**
   * 将指定模板编码下所有版本标记为非最新版本。
   *
   * @param templateCode 模板编码
   */
  void markAsNotLatest(String templateCode);

  /**
   * 查询模板编码下的最大版本号。
   *
   * @param templateCode 模板编码
   * @return 最大版本号，无版本时返回 {@code Optional.empty()}
   */
  Optional<Integer> selectMaxVersion(String templateCode);

  /**
   * 按模板编码查询所有版本列表。
   *
   * @param templateCode 模板编码
   * @return 模板 VO 列表（按版本号降序）
   */
  List<FlowTemplateVO> findVersionsByTemplateCode(String templateCode);

  /**
   * 按父模板 ID 查询所有子模板。
   *
   * @param parentTemplateId 父模板 ID
   * @return 模板 VO 列表
   */
  List<FlowTemplateVO> findByParentTemplateId(String parentTemplateId);
}
