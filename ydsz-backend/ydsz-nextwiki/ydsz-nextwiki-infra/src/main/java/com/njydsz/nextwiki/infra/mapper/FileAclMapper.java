package com.njydsz.nextwiki.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.nextwiki.domain.entity.FileAcl;

/**
 * 文件权限 ACL Mapper
 *
 * <p>对应数据表 <code>ydsz_file_acl</code>。
 * <p>ACL 按 (主体, 文件, 权限) 三元组定义访问规则（读/写/管理），是 NextWiki 安全模型的核心。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_file_principal — (文件+主体类型+主体 ID+权限) 唯一索引</li>
 *   <li>idx_file_id — 文件维度查询索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.nextwiki.domain.entity.FileAcl 文件权限实体
 * @see com.njydsz.nextwiki.server.service.FileAclService 文件权限 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FileAclMapper extends BaseMapper<FileAcl> {

    List<FileAcl> selectByFileNodeId(@Param("fileNodeId") String fileNodeId);

    List<FileAcl> selectByFileNodeIdAndGrantee(@Param("fileNodeId") String fileNodeId,
                                                @Param("granteeType") String granteeType,
                                                @Param("granteeId") String granteeId);

    @Delete("DELETE FROM nw_file_acl WHERE file_node_id = #{fileNodeId}")
    int deleteByFileNodeId(@Param("fileNodeId") String fileNodeId);

    /**
     * 查询有效权限（含继承自父目录的权限）
     */
    List<FileAcl> selectEffectivePermissions(@Param("fileNodeId") String fileNodeId,
                                              @Param("userId") String userId,
                                              @Param("roleIds") List<String> roleIds);

    int batchInsert(@Param("acls") List<FileAcl> acls);
}
