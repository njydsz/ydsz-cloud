package com.njydsz.nextwiki.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.nextwiki.domain.entity.FileAcl;

/**
 * 文件 ACL MyBatis Mapper
 *
 * @author ydsz-team
 * @since 1.4.0
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
