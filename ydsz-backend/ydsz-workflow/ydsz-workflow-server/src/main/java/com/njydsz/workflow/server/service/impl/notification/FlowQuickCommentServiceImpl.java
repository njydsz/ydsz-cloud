package com.njydsz.workflow.server.service.impl.notification;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.security.TenantContext;
import com.njydsz.workflow.domain.dto.FlowQuickCommentDTO;
import com.njydsz.workflow.domain.entity.FlowQuickComment;
import com.njydsz.workflow.infra.mapper.FlowQuickCommentMapper;
import com.njydsz.workflow.server.service.FlowQuickCommentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 审批常用语服务实现
 *
 * <p>P1-2: 对标钉钉/飞书审批的"常用语"能力。
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowQuickCommentServiceImpl implements FlowQuickCommentService {

    /** 常用语 Mapper，负责 ydsz_flow_quick_comment 表的增删改查（含用户自定义 + 系统预设） */
    private final FlowQuickCommentMapper quickCommentMapper;

    /**
     * {@inheritDoc}
     * <p>查询用户自定义常用语 + 系统预设常用语（isSystem=1），
     * 结果按 sortNum 升序、useCount 降序排列。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID（为空时从 TenantContext 获取）
     * @return 常用语列表
     */
    @Override
    public List<FlowQuickComment> listByUser(String userId, String tenantId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        String tid = tenantId != null ? tenantId : TenantContext.getTenantId();
        // 查询：用户自定义 + 系统预设（isSystem=1）
        List<FlowQuickComment> list = quickCommentMapper.selectList(
                new LambdaQueryWrapper<FlowQuickComment>()
                        .eq(FlowQuickComment::getUserId, userId)
                        .eq(FlowQuickComment::getTenantId, tid)
                        .eq(FlowQuickComment::getDeleted, 0)
        );
        // 系统预设（全局）
        List<FlowQuickComment> systemList = quickCommentMapper.selectList(
                new LambdaQueryWrapper<FlowQuickComment>()
                        .eq(FlowQuickComment::getIsSystem, 1)
                        .eq(FlowQuickComment::getTenantId, tid)
                        .eq(FlowQuickComment::getDeleted, 0)
        );
        list.addAll(systemList);
        // 排序：sortNum 升序, useCount 降序
        list.sort(Comparator
                .comparingInt(FlowQuickComment::getSortNum)
                .thenComparing(Comparator.comparingInt(FlowQuickComment::getUseCount).reversed()));
        return list;
    }

    /**
     * {@inheritDoc}
     * <p>创建用户自定义常用语（isSystem=0），useCount 初始为 0。
     *
     * @throws SysException 当 userId 为空时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(FlowQuickCommentDTO dto, String userId, String tenantId) {
        if (!StringUtils.hasText(userId)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_user_required");
        }
        FlowQuickComment comment = new FlowQuickComment();
        comment.setUserId(userId);
        comment.setContent(dto.getContent());
        comment.setCommentType(dto.getCommentType());
        comment.setSortNum(dto.getSortNum() != null ? dto.getSortNum() : 0);
        comment.setUseCount(0);
        comment.setIsSystem(0);
        comment.setTenantId(tenantId != null ? tenantId : TenantContext.getTenantId());
        quickCommentMapper.insert(comment);
        log.info("[FlowQuickComment] 新增常用语: userId={} id={}", userId, comment.getId());
        return comment.getId();
    }

    /**
     * {@inheritDoc}
     * <p>仅允许创建者本人更新，系统预设不可更新。
     *
     * @throws SysException 当 id 为空、常用语不存在或无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FlowQuickCommentDTO dto, String userId) {
        if (!StringUtils.hasText(dto.getId())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_id_required");
        }
        FlowQuickComment existing = quickCommentMapper.selectById(dto.getId());
        if (existing == null || existing.getDeleted() == 1) {
            throw new SysException(BaseResultCode.NOT_FOUND, "error.workflow.msg_6541ab08", dto.getId());
        }
        if (!userId.equals(existing.getUserId())) {
            throw new SysException(BaseResultCode.FORBIDDEN, "error.workflow.msg_no_permission");
        }
        existing.setContent(dto.getContent());
        if (dto.getCommentType() != null) {
            existing.setCommentType(dto.getCommentType());
        }
        if (dto.getSortNum() != null) {
            existing.setSortNum(dto.getSortNum());
        }
        quickCommentMapper.updateById(existing);
    }

    /**
     * {@inheritDoc}
     * <p>仅允许创建者本人删除，系统预设不可删除（抛 BAD_REQUEST）。
     *
     * @throws SysException 当系统预设常用语不可删除或无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id, String userId) {
        FlowQuickComment existing = quickCommentMapper.selectById(id);
        if (existing == null || existing.getDeleted() == 1) {
            return;
        }
        // 系统预设不可删除
        if (existing.getIsSystem() != null && existing.getIsSystem() == 1) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_system_comment_cannot_delete");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new SysException(BaseResultCode.FORBIDDEN, "error.workflow.msg_no_permission");
        }
        existing.setDeleted(1);
        quickCommentMapper.updateById(existing);
    }

    /**
     * {@inheritDoc}
     * <p>异步调用，异常不传播（try-catch 吞异常记 WARN）。
     *
     * @param id 常用语 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementUseCount(String id) {
        if (!StringUtils.hasText(id)) {
            return;
        }
        try {
            FlowQuickComment existing = quickCommentMapper.selectById(id);
            if (existing != null && existing.getDeleted() == 0) {
                existing.setUseCount((existing.getUseCount() == null ? 0 : existing.getUseCount()) + 1);
                quickCommentMapper.updateById(existing);
            }
        } catch (Exception e) {
            log.warn("[FlowQuickComment] 增加使用次数失败: id={} err={}", id, e.getMessage());
        }
    }
}
