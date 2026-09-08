package com.njydsz.nextwiki.infra.repository;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.converter.NextwikiStructMapper;
import com.njydsz.nextwiki.domain.dto.FileAclDTO;
import com.njydsz.nextwiki.domain.entity.FileAcl;
import com.njydsz.nextwiki.domain.query.FileAclQuery;
import com.njydsz.nextwiki.domain.repository.FileAclRepository;
import com.njydsz.nextwiki.domain.vo.FileAclVO;
import com.njydsz.nextwiki.infra.mapper.FileAclMapper;

/**
 * 文件 ACL 仓储实现
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 * <li>通过 {@link NextwikiStructMapper} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link NextwikiStructMapper} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class FileAclRepositoryImpl implements FileAclRepository {

  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final FileAclMapper fileAclMapper;
  private final NextwikiStructMapper mapper;

  /**
   * 保存文件 ACL 条目（新增或更新）。
   *
   * @param dto ACL 数据传输对象
   * @return 保存后的 ACL 视图对象
   */
  @Override
  public FileAclVO save(FileAclDTO dto) {
    FileAcl entity = mapper.fileAclToEntity(dto);
    if (entity.getId() == null || entity.getId().isEmpty()) {
      entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    fileAclMapper.insert(entity);
    return mapper.fileAclToVO(entity);
  }

  /**
   * 按文件节点 ID 查询所有 ACL 记录。
   *
   * @param fileNodeId 文件节点 ID
   * @return ACL 视图对象列表
   */
  @Override
  public List<FileAclVO> findByFileNodeId(String fileNodeId) {
    return mapper.fileAclListToVO(fileAclMapper.selectByFileNodeId(fileNodeId));
  }

  /**
   * 按文件节点与受权人（用户/角色/租户）查询 ACL 记录。
   *
   * @param query ACL 查询条件
   * @return ACL 视图对象列表
   */
  @Override
  public List<FileAclVO> findByFileNodeIdAndGrantee(FileAclQuery query) {
    // 语义为"查询用户对文件的有效权限"（含用户/角色/租户维度），
    // 对应 Mapper 的 selectEffectivePermissions（构建修复：原误调用 3 参数的精确匹配查询）
    return mapper.fileAclListToVO(
        fileAclMapper.selectEffectivePermissions(
            query.getFileNodeId(), query.getUserId(), query.getRoleIds()));
  }

  /**
   * 根据文件节点 ID 逻辑删除所有 ACL 记录（文件删除时级联清理）。
   *
   * @param fileNodeId 文件节点 ID
   */
  @Override
  public void deleteByFileNodeId(String fileNodeId) {
    fileAclMapper.deleteByFileNodeId(fileNodeId);
  }

  /**
   * 查询用户在指定文件上的有效权限（综合用户/角色/租户维度）。
   *
   * @param query ACL 查询条件（含文件 ID、用户 ID、角色 ID）
   * @return 有效 ACL 视图对象列表
   */
  @Override
  public List<FileAclVO> findEffectivePermissions(FileAclQuery query) {
    return mapper.fileAclListToVO(
        fileAclMapper.selectEffectivePermissions(
            query.getFileNodeId(),
            query.getUserId(),
            query.getRoleIds()));
  }

  /**
   * 批量新增 ACL 条目（文件权限初始化时使用）。
   *
   * @param dtos ACL 数据传输对象列表
   */
  @Override
  public void batchSave(List<FileAclDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return;
    }
    List<FileAcl> entities = mapper.fileAclListToEntity(dtos);
    for (FileAcl entity : entities) {
      if (entity.getId() == null || entity.getId().isEmpty()) {
        entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
      }
      fileAclMapper.insert(entity);
    }
  }
}
