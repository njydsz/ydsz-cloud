package com.njydsz.nextwiki.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.nextwiki.domain.dto.SearchIndexDTO;
import com.njydsz.nextwiki.domain.query.SearchIndexQuery;
import com.njydsz.nextwiki.domain.query.SearchQuery;
import com.njydsz.nextwiki.domain.vo.SearchIndexVO;

/**
 * 搜索索引仓储接口
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>返回领域 VO（{@link SearchIndexVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link SearchIndexQuery}）或具体字段
 *   <li>CUD 入参使用领域 DTO（{@link SearchIndexDTO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface SearchIndexRepository {

  /**
   * 新增或更新索引（以 fileNodeId 为唯一键）
   *
   * @param dto 搜索索引 DTO
   */
  void upsert(SearchIndexDTO dto);

  /**
   * 根据文件节点ID删除索引
   *
   * @param fileNodeId 文件节点ID
   */
  void deleteByFileNodeId(String fileNodeId);

  /**
   * 根据文件节点ID查询索引
   *
   * @param fileNodeId 文件节点ID
   * @return 搜索索引 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<SearchIndexVO> findByFileNodeId(String fileNodeId);

  /**
   * 查询所有未删除的文件节点ID（用于索引重建）
   *
   * @param createdBy 创建人，传 null 查询全部
   * @return 文件节点ID列表
   */
  List<String> findAllFileNodeIds(String createdBy);

  /**
   * 数据库分页搜索索引
   *
   * @param query 搜索查询参数
   * @return 分页搜索结果
   */
  PageResponse<List<SearchIndexVO>> searchPage(SearchIndexQuery query);

  /**
   * 高级语法分页搜索（支持字段限定、布尔运算、短语精确匹配）。
   *
   * <p>当统一搜索引擎不可用时作为高级查询的 DB 降级入口，直接基于解析后的结构化查询构建 SQL。
   *
   * @param query 解析后的高级搜索查询（由 {@link com.njydsz.nextwiki.domain.service.SearchQueryParser} 生成）
   * @return 分页搜索结果
   */
  PageResponse<List<SearchIndexVO>> searchAdvanced(SearchQuery query);
}
