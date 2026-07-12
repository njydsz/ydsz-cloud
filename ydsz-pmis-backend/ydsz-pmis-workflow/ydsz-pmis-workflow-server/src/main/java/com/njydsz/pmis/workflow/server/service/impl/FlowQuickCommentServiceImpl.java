package com.njydsz.pmis.workflow.server.service.impl.notification;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.SysException;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.workflow.domain.dto.notification.FlowQuickCommentDTO;
import com.njydsz.pmis.workflow.domain.entity.notification.FlowQuickCommentDO;
import com.njydsz.pmis.workflow.infra.mapper.notification.FlowQuickCommentMapper;
import com.njydsz.pmis.workflow.server.service.notification.FlowQuickCommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 审批常用语服务实现
 *
 * <p>P1-2: 对标钉钉/飞书审批的"常用语"能力。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowQuickCommentServiceImpl implements FlowQuickCommentService {

    /** 常用语 Mapper，负责 pmis_flow_quick_comment 表的增删改查（含用户自定义 + 系统预设） */
    private final FlowQuickCommentMapper quickCommentMapper;

    @Override
    public List<FlowQuickCommentDO> listByUser(String userId, String tenantId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        String tid = tenantId != null ? tenantId : TenantContext.getTenantId();
        // 查询：用户自定义 + 系统预设（isSystem=1）
        List<FlowQuickCommentDO> list = quickCommentMapper.selectList(
                new LambdaQueryWrapper<FlowQuickCommentDO>()
                        .eq(FlowQuickCommentDO::getUserId, userId)
                        .eq(FlowQuickCommentDO::getTenantId, tid)
                        .eq(FlowQuickCommentDO::getDeleted, 0)
        );
        // 系统预设（全局）
        List<FlowQuickCommentDO> systemList = quickCommentMapper.selectList(
                new LambdaQueryWrapper<FlowQuickCommentDO>()
                        .eq(FlowQuickCommentDO::getIsSystem, 1)
                        .eq(FlowQuickCommentDO::getTenantId, tid)
                        .eq(FlowQuickCommentDO::getDeleted, 0)
        );
        list.addAll(systemList);
        // 排序：sortNum 升序, useCount 降序
        list.sort(Comparator
                .comparingInt(FlowQuickCommentDO::getSortNum)
                .thenComparing(Comparator.comparingInt(FlowQuickCommentDO::getUseCount).reversed()));
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(FlowQuickCommentDTO dto, String userId, String tenantId) {
        if (!StringUtils.hasText(userId)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_user_required");
        }
        FlowQuickCommentDO comment = new FlowQuickCommentDO();
        comment.setUserId(userId);
        comment.setContent(dto.getContent());
        comment.setCommentType(dto.getCommentType());
        comment.setSortNum(dto.getSortNum() != null ? dto.getSortNum() : 0);
        comment.setUseCount(0);
        comment.setIsSystem(0);
        comment.setTenantId(tenantId != null ? tenantId : TenantContext.getTenantId());
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        quickCommentMapper.insert(comment);
        log.info("[FlowQuickComment] 新增常用语: userId={} id={}", userId, comment.getId());
        return comment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FlowQuickCommentDTO dto, String userId) {
        if (!StringUtils.hasText(dto.getId())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_id_required");
        }
        FlowQuickCommentDO existing = quickCommentMapper.selectById(dto.getId());
        if (existing == null || existing.getDeleted() == 1) {
            throw new SysException(StandardResultCode.NOT_FOUND, "error.workflow.msg_6541ab08", dto.getId());
        }
        if (!userId.equals(existing.getUserId())) {
            throw new SysException(StandardResultCode.FORBIDDEN, "error.workflow.msg_no_permission");
        }
        existing.setContent(dto.getContent());
        if (dto.getCommentType() != null) {
            existing.setCommentType(dto.getCommentType());
        }
        if (dto.getSortNum() != null) {
            existing.setSortNum(dto.getSortNum());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        quickCommentMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id, String userId) {
        FlowQuickCommentDO existing = quickCommentMapper.selectById(id);
        if (existing == null || existing.getDeleted() == 1) {
            return;
        }
        // 系统预设不可删除
        if (existing.getIsSystem() != null && existing.getIsSystem() == 1) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_system_comment_cannot_delete");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new SysException(StandardResultCode.FORBIDDEN, "error.workflow.msg_no_permission");
        }
        existing.setDeleted(1);
        existing.setUpdatedAt(LocalDateTime.now());
        quickCommentMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementUseCount(String id) {
        if (!StringUtils.hasText(id)) {
            return;
        }
        try {
            FlowQuickCommentDO existing = quickCommentMapper.selectById(id);
            if (existing != null && existing.getDeleted() == 0) {
                existing.setUseCount((existing.getUseCount() == null ? 0 : existing.getUseCount()) + 1);
                existing.setUpdatedAt(LocalDateTime.now());
                quickCommentMapper.updateById(existing);
            }
        } catch (Exception e) {
            log.warn("[FlowQuickComment] 增加使用次数失败: id={} err={}", id, e.getMessage());
        }
    }
}
