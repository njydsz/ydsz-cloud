package com.njydsz.nextwiki.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.njydsz.nextwiki.domain.query.SearchQuery;
import com.njydsz.nextwiki.infra.entity.SearchIndexDO;

/**
 * 搜索索引 Mapper
 *
 * <p>对应数据表 <code>ydsz_search_index</code>。
 *
 * <p>索引按文件版本同步（ES/PG 全文索引），支持全文检索/高亮/排序/聚合。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_index_id — 索引 ID 唯一索引
 *   <li>idx_file_version — (文件+版本) 索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.nextwiki.infra.entity.SearchIndexDO 搜索索引实体
 * @see com.njydsz.nextwiki.server.service.SearchIndexService 搜索 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface SearchIndexMapper extends BaseMapper<SearchIndexDO> {

  /** 新增或更新索引（PostgreSQL ON CONFLICT 语义） */
  int upsert(@Param("index") SearchIndexDO index);

  /** 根据文件节点ID删除索引（物理删除） */
  int deleteByFileNodeId(@Param("fileNodeId") String fileNodeId);

  /** 根据文件节点ID查询索引 */
  SearchIndexDO selectByFileNodeId(@Param("fileNodeId") String fileNodeId);

  /**
   * 查询所有未删除的文件节点ID（用于索引重建）
   *
   * @param createdBy 创建人，传 null 查询全部
   */
  @Select({
    "<script>",
    "SELECT id FROM nw_file_node WHERE deleted = 0 AND node_type = 'file'",
    "<if test='createdBy != null and createdBy != \"\"'>",
    "AND created_by = #{createdBy}",
    "</if>",
    "ORDER BY created_at ASC",
    "</script>"
  })
  List<String> selectAllFileNodeIds(@Param("createdBy") String createdBy);

  /**
   * 数据库分页搜索索引（支持 name/path/content/tags 多维度 LIKE 搜索）
   *
   * <p>使用 LIMIT/OFFSET 在 SQL 层面分页，避免全量加载后内存分页。
   *
   * @param page MyBatis-Plus 分页对象
   * @param keyword 搜索关键词
   * @param createdBy 创建人（权限过滤）
   * @param scope 搜索范围：all / filename / content / TagDO
   * @return 分页结果
   */
  IPage<SearchIndexDO> searchPage(
      IPage<SearchIndexDO> page,
      @Param("keyword") String keyword,
      @Param("createdBy") String createdBy,
      @Param("scope") String scope);

  /**
   * 统计搜索结果总数（不分页）
   *
   * @param keyword 搜索关键词
   * @param createdBy 创建人
   * @param scope 搜索范围
   * @return 匹配总数
   */
  long countSearchResults(
      @Param("keyword") String keyword,
      @Param("createdBy") String createdBy,
      @Param("scope") String scope);

  /**
   * 高级语法分页搜索（支持字段限定、布尔运算、短语精确匹配、通配符）。
   *
   * <p>由 SearchIndexRepositoryImpl.searchAdvanced 调用，直接传入 {@link SearchQuery} 结构化查询对象。
   *
   * @param page MyBatis-Plus 分页对象
   * @param query 解析后的搜索查询对象（含全文词、包含/排除词、字段限定、短语）
   * @return 分页结果
   */
  IPage<SearchIndexDO> searchAdvanced(
      IPage<SearchIndexDO> page,
      @Param("query") SearchQuery query);
}
