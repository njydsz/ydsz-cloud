package com.njydsz.nextwiki.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.nextwiki.domain.entity.FileAcl;

/**
 * 文件权限 ACL Mapper
 *
 * <p>对应数据表 <code>ydsz_file_acl</code>。
 *
 * <p>ACL 按 (主体, 文件, 权限) 三元组定义访问规则（读/写/管理），是 NextWiki 安全模型的核心。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_file_principal — (文件+主体类型+主体 ID+权限) 唯一索引
 *   <li>idx_file_id — 文件维度查询索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.nextwiki.domain.entity.FileAcl 文件权限实体
 * @see com.njydsz.nextwiki.server.service.FileAclService 文件权限 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FileAclMapper extends BaseMapper<FileAcl> {

  /**
   * 查询指定文件节点下的全部 ACL 规则。
   *
   * @param fileNodeId 文件节点 ID
   * @return 该节点下的 ACL 规则列表（可能为空；已逻辑删除记录由拦截器自动过滤）
   */
  List<FileAcl> selectByFileNodeId(@Param("fileNodeId") String fileNodeId);

  /**
   * 精确查询某文件节点下、某授权主体（用户/角色/组）的 ACL 规则，供鉴权判定使用。
   *
   * @param fileNodeId 文件节点 ID
   * @param granteeType 主体类型：user / role / group
   * @param granteeId 主体 ID
   * @return 命中的 ACL 规则列表
   */
  List<FileAcl> selectByFileNodeIdAndGrantee(
      @Param("fileNodeId") String fileNodeId,
      @Param("granteeType") String granteeType,
      @Param("granteeId") String granteeId);

  /**
   * 删除指定文件节点下的所有 ACL 规则（随节点删除级联清理，避免残留越权规则）。
   *
   * @param fileNodeId 文件节点 ID
   * @return 受影响行数
   */
  @Delete("DELETE FROM nw_file_acl WHERE file_node_id = #{fileNodeId}")
  int deleteByFileNodeId(@Param("fileNodeId") String fileNodeId);

  /** 查询有效权限（含继承自父目录的权限） */
  List<FileAcl> selectEffectivePermissions(
      @Param("fileNodeId") String fileNodeId,
      @Param("userId") String userId,
      @Param("roleIds") List<String> roleIds);

  /**
   * 批量插入 ACL 规则（一次文件的批量授权场景）。
   *
   * @param acls 待插入的 ACL 列表
   * @return 受影响行数
   */
  int batchInsert(@Param("acls") List<FileAcl> acls);
}
