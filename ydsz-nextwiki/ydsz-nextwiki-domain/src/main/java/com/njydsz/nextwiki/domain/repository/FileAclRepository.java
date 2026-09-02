package com.njydsz.nextwiki.domain.repository;

import java.util.List;

import com.njydsz.nextwiki.domain.dto.FileAclDTO;
import com.njydsz.nextwiki.domain.query.FileAclQuery;
import com.njydsz.nextwiki.domain.vo.FileAclVO;

/**
 * 文件 ACL 仓储接口
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>返回领域 VO（{@link FileAclVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link FileAclQuery}）或具体字段
 *   <li>CUD 入参使用领域 DTO（{@link FileAclDTO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FileAclRepository {

  /**
   * 保存单条 ACL 记录（新增或更新）
   *
   * @param dto 文件 ACL DTO
   * @return 持久化后的 ACL VO
   */
  FileAclVO save(FileAclDTO dto);

  /**
   * 查询某文件节点上配置的全部 ACL 记录
   *
   * @param fileNodeId 文件节点ID
   * @return ACL VO 列表
   */
  List<FileAclVO> findByFileNodeId(String fileNodeId);

  /**
   * 精确查询某文件节点上授予给指定授权对象的 ACL 记录
   *
   * @param query ACL 查询参数
   * @return 匹配的 ACL VO 列表
   */
  List<FileAclVO> findByFileNodeIdAndGrantee(FileAclQuery query);

  /**
   * 删除某文件节点上的全部 ACL 记录
   *
   * @param fileNodeId 文件节点ID
   */
  void deleteByFileNodeId(String fileNodeId);

  /**
   * 查询用户对某文件的权限（含继承）
   *
   * @param query ACL 查询参数（含 fileNodeId、userId、roleIds）
   * @return 有效权限 ACL VO 列表
   */
  List<FileAclVO> findEffectivePermissions(FileAclQuery query);

  /**
   * 批量插入继承的 ACL
   *
   * @param dtos 文件 ACL DTO 列表
   */
  void batchSave(List<FileAclDTO> dtos);
}
