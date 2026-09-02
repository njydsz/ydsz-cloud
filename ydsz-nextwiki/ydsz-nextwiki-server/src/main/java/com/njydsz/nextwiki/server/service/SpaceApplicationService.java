package com.njydsz.nextwiki.server.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.nextwiki.domain.dto.SpaceDTO;
import com.njydsz.nextwiki.domain.dto.SpaceMemberDTO;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.repository.SpaceMemberRepository;
import com.njydsz.nextwiki.domain.repository.SpaceRepository;
import com.njydsz.nextwiki.domain.service.SpaceDomainService;
import com.njydsz.nextwiki.domain.vo.SpaceVO;
import com.njydsz.nextwiki.server.converter.NextwikiConverter;
import com.njydsz.nextwiki.server.security.SpacePermission;
import com.njydsz.nextwiki.server.security.SpacePermission.Level;

/**
 * 知识库空间应用服务
 *
 * <p><b>S3-P2-01：空间管理聚合根</b>
 *
 * <p>提供空间管理的完整业务编排：创建、更新、删除、成员管理。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpaceApplicationService {

  /** 空间数据仓储（空间表的 CRUD 操作） */
  private final SpaceRepository spaceRepository;

  /** 空间成员数据仓储（成员表的 CRUD 操作） */
  private final SpaceMemberRepository spaceMemberRepository;

  /** 空间领域服务（名称校验、排序等业务规则） */
  private final SpaceDomainService spaceDomainService;

  /** DTO/VO 转换器 */
  private final NextwikiConverter nextwikiConverter;

  /** 默认查询数量限制 */
  private static final int DEFAULT_LIMIT = 50;

  /**
   * 创建知识库空间。
   *
   * @param name 空间名称（非空，长度 1-100）
   * @param description 空间描述（可为空，最长 500 字符）
   * @param visibility 可见性（public/private，默认 private）
   * @param userId 创建者 ID
   * @return 空间视图对象 {@link SpaceVO}
   * @throws BusinessException 名称重复（SPACE_NAME_DUPLICATE）、名称非法时抛出
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   */
  @Transactional(rollbackFor = Exception.class)
  public SpaceVO createSpace(String name, String description, String visibility, String userId) {
    String tenantId = TenantContextHolder.getTenantId();

    // 校验参数（名称非空由领域服务校验）
    spaceDomainService.validateSpaceName(name);

    // 校验名称唯一性
    spaceRepository.findByTenantIdAndName(tenantId, name).ifPresent(existing -> {
      throw BusinessException.of(NextwikiExceptionCode.SPACE_NAME_DUPLICATE).data("name", name);
    });

    // 计算排序号
    int maxSort = spaceRepository.countByTenantId(tenantId);

    // 创建空间
    LocalDateTime now = LocalDateTime.now();
    SpaceDTO space = SpaceDTO.builder()
        .name(name)
        .description(description)
        .visibility(visibility != null ? visibility : "private")
        .status("active")
        .tenantId(tenantId)
        .ownerId(userId)
        .sortOrder(maxSort)
        .memberCount(1)
        .nodeCount(0)
        .quotaUsed(0L)
        .createdBy(userId)
        .updatedBy(userId)
        .createdAt(now)
        .updatedAt(now)
        .build();

    spaceRepository.save(space);

    // 自动添加创建者为所有者
    SpaceMemberDTO ownerMember = SpaceMemberDTO.builder()
        .spaceId(space.getId())
        .userId(userId)
        .role("owner")
        .tenantId(tenantId)
        .joinedAt(now)
        .createdBy(userId)
        .updatedBy(userId)
        .updatedAt(now)
        .build();
    spaceMemberRepository.save(ownerMember);

    log.info("[SpaceApplicationService] 创建空间: spaceId={}, name={}, userId={}", space.getId(), name, userId);

    // 返回新创建的 VO
    return spaceRepository.findById(space.getId()).orElseThrow();
  }

  /**
   * 更新空间信息。
   *
   * @param spaceId 空间 ID
   * @param name 新名称（为空则不更新）
   * @param description 新描述（为空则不更新）
   * @param visibility 新可见性（为空则不更新）
   * @param userId 操作人 ID
   * @return 更新后的空间视图 {@link SpaceVO}
   * @throws BusinessException 空间不存在（SPACE_NOT_FOUND）、名称非法时抛出
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   */
  @Transactional(rollbackFor = Exception.class)
  @SpacePermission(level = Level.ADMIN)
  public SpaceVO updateSpace(String spaceId, String name, String description, String visibility, String userId) {
    SpaceVO space = spaceRepository.findById(spaceId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.SPACE_NOT_FOUND).data("spaceId", spaceId));

    // 更新字段
    if (name != null) {
      spaceDomainService.validateSpaceName(name);
      space.setName(name);
    }
    if (description != null) {
      space.setDescription(description);
    }
    if (visibility != null) {
      space.setVisibility(visibility);
    }
    space.setUpdatedAt(LocalDateTime.now());

    // 转换为 DTO 进行更新
    SpaceDTO updateDTO = SpaceDTO.builder()
        .id(space.getId())
        .name(space.getName())
        .description(space.getDescription())
        .visibility(space.getVisibility())
        .status(space.getStatus())
        .ownerId(space.getOwnerId())
        .sortOrder(space.getSortOrder())
        .memberCount(space.getMemberCount())
        .nodeCount(space.getNodeCount())
        .quotaLimit(space.getQuotaLimit())
        .quotaUsed(space.getQuotaUsed())
        .createdAt(space.getCreatedAt())
        .updatedAt(space.getUpdatedAt())
        .updatedBy(userId)
        .build();
    spaceRepository.update(updateDTO);

    log.info("[SpaceApplicationService] 更新空间: spaceId={}, userId={}", spaceId, userId);
    return spaceRepository.findById(spaceId).orElseThrow();
  }

  /**
   * 归档空间。
   *
   * @param spaceId 空间 ID
   * @param userId 操作人 ID（需具备 ADMIN 权限）
   * @throws BusinessException 空间不存在（SPACE_NOT_FOUND）、权限不足（PERMISSION_DENIED）时抛出
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   */
  @Transactional(rollbackFor = Exception.class)
  @SpacePermission(level = Level.ADMIN)
  public void archiveSpace(String spaceId, String userId) {
    SpaceVO space = spaceRepository.findById(spaceId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.SPACE_NOT_FOUND).data("spaceId", spaceId));

    space.setStatus("archived");
    space.setUpdatedAt(LocalDateTime.now());

    // 转换为 DTO 进行更新
    SpaceDTO updateDTO = SpaceDTO.builder()
        .id(space.getId())
        .name(space.getName())
        .description(space.getDescription())
        .visibility(space.getVisibility())
        .status(space.getStatus())
        .ownerId(space.getOwnerId())
        .sortOrder(space.getSortOrder())
        .memberCount(space.getMemberCount())
        .nodeCount(space.getNodeCount())
        .quotaLimit(space.getQuotaLimit())
        .quotaUsed(space.getQuotaUsed())
        .createdAt(space.getCreatedAt())
        .updatedAt(space.getUpdatedAt())
        .updatedBy(userId)
        .build();
    spaceRepository.update(updateDTO);

    log.info("[SpaceApplicationService] 归档空间: spaceId={}, userId={}", spaceId, userId);
  }

  /**
   * 删除空间（逻辑删除）。
   *
   * @param spaceId 空间 ID
   * @param userId 操作人 ID（需具备 OWNER 权限）
   * @throws BusinessException 空间不存在（SPACE_NOT_FOUND）、权限不足（PERMISSION_DENIED）时抛出
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   */
  @Transactional(rollbackFor = Exception.class)
  @SpacePermission(level = Level.OWNER)
  public void deleteSpace(String spaceId, String userId) {
    spaceRepository.findById(spaceId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.SPACE_NOT_FOUND).data("spaceId", spaceId));

    spaceRepository.deleteById(spaceId);

    log.info("[SpaceApplicationService] 删除空间: spaceId={}, userId={}", spaceId, userId);
  }

  /**
   * 查询租户下的空间列表。
   *
   * @param userId 用户 ID（用于权限过滤：仅返回用户可见的空间）
   * @return 空间视图列表（可能为空，非 {@code null}）
   */
  public List<SpaceVO> listSpaces(String userId) {
    String tenantId = TenantContextHolder.getTenantId();
    List<SpaceVO> spaces = spaceRepository.findByTenantId(tenantId);
    return spaces.stream()
        .filter(s -> hasSpaceReadPermission(s.getId(), userId))
        .collect(Collectors.toList());
  }

  /**
   * 获取空间详情。
   *
   * @param spaceId 空间 ID
   * @param userId 请求用户 ID（需具备 VIEWER 及以上权限）
   * @return 空间视图 {@link SpaceVO}
   * @throws BusinessException 空间不存在（SPACE_NOT_FOUND）、权限不足（PERMISSION_DENIED）时抛出
   */
  @SpacePermission(level = Level.VIEWER)
  public SpaceVO getSpace(String spaceId, String userId) {
    return spaceRepository.findById(spaceId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.SPACE_NOT_FOUND).data("spaceId", spaceId));
  }

  /**
   * 添加空间成员。
   *
   * @param spaceId 空间 ID
   * @param targetUserId 目标用户 ID
   * @param role 角色（owner/admin/editor/viewer）
   * @param operatorId 操作人 ID（需具备 ADMIN 权限）
   * @throws BusinessException 空间不存在（SPACE_NOT_FOUND）、角色非法（SPACE_MEMBER_ROLE_INVALID）、权限不足（PERMISSION_DENIED）时抛出
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   */
  @Transactional(rollbackFor = Exception.class)
  @SpacePermission(level = Level.ADMIN)
  public void addMember(String spaceId, String targetUserId, String role, String operatorId) {
    // 校验空间存在
    SpaceVO space = spaceRepository.findById(spaceId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.SPACE_NOT_FOUND).data("spaceId", spaceId));

    // 校验角色合法性
    if (!isValidRole(role)) {
      throw BusinessException.of(NextwikiExceptionCode.SPACE_MEMBER_ROLE_INVALID).data("role", role);
    }

    String tenantId = TenantContextHolder.getTenantId();
    LocalDateTime now = LocalDateTime.now();

    // 检查是否已是成员
    spaceMemberRepository.findBySpaceIdAndUserId(spaceId, targetUserId).ifPresent(existing -> {
      // 已存在则更新角色
      spaceMemberRepository.updateRole(spaceId, targetUserId, role);
    });

    // 不存在则新增
    if (!spaceMemberRepository.existsBySpaceIdAndUserId(spaceId, targetUserId)) {
      SpaceMemberDTO member = SpaceMemberDTO.builder()
          .spaceId(spaceId)
          .userId(targetUserId)
          .role(role)
          .tenantId(tenantId)
          .joinedAt(now)
          .createdBy(operatorId)
          .updatedBy(operatorId)
          .updatedAt(now)
          .build();
      spaceMemberRepository.save(member);

      // 更新成员数量
      int count = spaceMemberRepository.countBySpaceId(spaceId);
      space.setMemberCount(count);

      // 转换为 DTO 进行更新
      SpaceDTO updateDTO = SpaceDTO.builder()
          .id(space.getId())
          .name(space.getName())
          .description(space.getDescription())
          .visibility(space.getVisibility())
          .status(space.getStatus())
          .ownerId(space.getOwnerId())
          .sortOrder(space.getSortOrder())
          .memberCount(space.getMemberCount())
          .nodeCount(space.getNodeCount())
          .quotaLimit(space.getQuotaLimit())
          .quotaUsed(space.getQuotaUsed())
          .createdAt(space.getCreatedAt())
          .updatedAt(space.getUpdatedAt())
          .updatedBy(operatorId)
          .build();
      spaceRepository.update(updateDTO);
    }

    log.info("[SpaceApplicationService] 添加空间成员: spaceId={}, userId={}, role={}", spaceId, targetUserId, role);
  }

  /**
   * 移除空间成员。
   *
   * @param spaceId 空间 ID
   * @param targetUserId 目标用户 ID
   * @param operatorId 操作人 ID（需具备 ADMIN 权限）
   * @throws BusinessException 空间不存在（SPACE_NOT_FOUND）、成员不存在（SPACE_MEMBER_NOT_FOUND）、
   *     尝试移除所有者（SPACE_MEMBER_ROLE_INVALID）、权限不足（PERMISSION_DENIED）时抛出
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   */
  @Transactional(rollbackFor = Exception.class)
  @SpacePermission(level = Level.ADMIN)
  public void removeMember(String spaceId, String targetUserId, String operatorId) {
    // 不能移除所有者
    SpaceMemberDTO member = spaceMemberRepository.findBySpaceIdAndUserId(spaceId, targetUserId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.SPACE_MEMBER_NOT_FOUND).data("userId", targetUserId));

    if ("owner".equals(member.getRole())) {
      throw BusinessException.of(NextwikiExceptionCode.SPACE_MEMBER_ROLE_INVALID).data("msg", "不能移除空间所有者");
    }

    spaceMemberRepository.deleteBySpaceIdAndUserId(spaceId, targetUserId);

    // 更新成员数量
    SpaceVO space = spaceRepository.findById(spaceId).orElseThrow();
    int count = spaceMemberRepository.countBySpaceId(spaceId);
    space.setMemberCount(count);

    // 转换为 DTO 进行更新
    SpaceDTO updateDTO = SpaceDTO.builder()
        .id(space.getId())
        .name(space.getName())
        .description(space.getDescription())
        .visibility(space.getVisibility())
        .status(space.getStatus())
        .ownerId(space.getOwnerId())
        .sortOrder(space.getSortOrder())
        .memberCount(space.getMemberCount())
        .nodeCount(space.getNodeCount())
        .quotaLimit(space.getQuotaLimit())
        .quotaUsed(space.getQuotaUsed())
        .createdAt(space.getCreatedAt())
        .updatedAt(space.getUpdatedAt())
        .updatedBy(operatorId)
        .build();
    spaceRepository.update(updateDTO);

    log.info("[SpaceApplicationService] 移除空间成员: spaceId={}, userId={}", spaceId, targetUserId);
  }

  /**
   * 查询空间成员列表。
   *
   * @param spaceId 空间 ID
   * @param userId 请求用户 ID（需具备 VIEWER 及以上权限）
   * @return 成员 DTO 列表（可能为空，非 {@code null}）
   */
  @SpacePermission(level = Level.VIEWER)
  public List<SpaceMemberDTO> listMembers(String spaceId, String userId) {
    return spaceMemberRepository.findBySpaceId(spaceId);
  }

  // ==================== 私有方法 ====================

  /**
   * 检查用户是否有空间管理权限（所有者或管理员）。
   */
  private void checkSpaceAdminPermission(String spaceId, String userId) {
    SpaceMemberDTO member = spaceMemberRepository.findBySpaceIdAndUserId(spaceId, userId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.PERMISSION_DENIED));
    if (!"owner".equals(member.getRole()) && !"admin".equals(member.getRole())) {
      throw BusinessException.of(NextwikiExceptionCode.PERMISSION_DENIED);
    }
  }

  /**
   * 检查用户是否有空间读取权限。
   */
  private boolean hasSpaceReadPermission(String spaceId, String userId) {
    // 公开空间所有人可读
    SpaceVO space = spaceRepository.findById(spaceId).orElse(null);
    if (space != null && "public".equals(space.getVisibility())) {
      return true;
    }
    // 成员可读
    return spaceMemberRepository.existsBySpaceIdAndUserId(spaceId, userId);
  }

  /**
   * 校验角色是否合法。
   */
  private boolean isValidRole(String role) {
    return "owner".equals(role) || "admin".equals(role) || "editor".equals(role) || "viewer".equals(role);
  }
}
