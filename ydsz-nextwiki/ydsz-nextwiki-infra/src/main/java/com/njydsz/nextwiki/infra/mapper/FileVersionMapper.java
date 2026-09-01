package com.njydsz.nextwiki.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.njydsz.nextwiki.infra.entity.FileVersion;

/**
 * 文件版本 Mapper
 *
 * <p>对应数据表 <code>ydsz_wiki_file_version</code>。
 *
 * <p>文件每次编辑保存新版本（content + 元数据），支持回滚、对比、审计。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_version_id — 版本 ID 唯一索引
 *   <li>idx_file_id — 文件维度查询索引
 *   <li>idx_version_no — 版本号排序索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.nextwiki.infra.entity.FileVersion 文件版本实体
 * @see com.njydsz.nextwiki.server.service.FileVersionService 文件版本 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FileVersionMapper extends BaseMapper<FileVersion> {

  /**
   * 查询文件的版本历史。
   *
   * @param fileNodeId 文件节点 ID
   * @return 版本列表（按版本号降序）
   */
  List<FileVersion> selectByFileNodeId(@Param("fileNodeId") String fileNodeId);

  /**
   * 查询指定版本。
   *
   * @param fileNodeId 文件节点 ID
   * @param versionNumber 版本号
   * @return 版本实体（不存在时为 null）
   */
  FileVersion selectByVersion(
      @Param("fileNodeId") String fileNodeId, @Param("versionNumber") Integer versionNumber);

  /**
   * 查询活跃版本。
   *
   * @param fileNodeId 文件节点 ID
   * @return 活跃版本实体（不存在时为 null）
   */
  FileVersion selectActiveVersion(@Param("fileNodeId") String fileNodeId);

  /**
   * 设置活跃版本（-1 表示全部设为非活跃）。
   *
   * @param fileNodeId 文件节点 ID
   * @param versionNumber 目标版本号
   * @return 受影响行数
   */
  @Update(
      "UPDATE nw_file_version SET is_active = CASE WHEN version_number = #{versionNumber} THEN true ELSE false END "
          + "WHERE file_node_id = #{fileNodeId}")
  int setActiveVersion(
      @Param("fileNodeId") String fileNodeId, @Param("versionNumber") Integer versionNumber);

  /**
   * 统计版本数。
   *
   * @param fileNodeId 文件节点 ID
   * @return 版本数量
   */
  int countByFileNodeId(@Param("fileNodeId") String fileNodeId);

  /**
   * 查询最旧版本（按版本号升序）。
   *
   * @param fileNodeId 文件节点 ID
   * @param limit 返回数量上限
   * @return 最旧版本列表
   */
  List<FileVersion> selectOldestVersions(
      @Param("fileNodeId") String fileNodeId, @Param("limit") int limit);

  /**
   * 批量删除指定文件节点中除保留版本外的所有旧版本
   *
   * <p>保留最近 {@code keepCount} 个版本（按 version_number DESC），删除其余。
   *
   * @param fileNodeId 文件节点ID
   * @param keepCount 保留的版本数量
   * @return 受影响行数
   */
  @Delete(
      "DELETE FROM nw_file_version WHERE file_node_id = #{fileNodeId} AND deleted = 0 "
          + "AND id NOT IN ("
          + "  SELECT id FROM ("
          + "    SELECT id FROM nw_file_version "
          + "    WHERE file_node_id = #{fileNodeId} AND deleted = 0 "
          + "    ORDER BY version_number DESC LIMIT #{keepCount}"
          + "  ) AS keep_ids"
          + ")")
  int deleteExcessVersions(
      @Param("fileNodeId") String fileNodeId, @Param("keepCount") int keepCount);

  /**
   * 带 revision 乐观锁的更新（更新失败返回 0）。
   *
   * @param version 待更新的版本实体（含 revision）
   * @return 受影响行数
   */
  int updateWithRevision(@Param("version") FileVersion version);
}
