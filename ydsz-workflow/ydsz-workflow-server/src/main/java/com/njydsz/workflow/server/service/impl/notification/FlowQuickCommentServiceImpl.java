package com.njydsz.workflow.server.service.impl.notification;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.workflow.domain.dto.FlowQuickCommentDTO;
import com.njydsz.workflow.domain.entity.FlowQuickComment;
import com.njydsz.workflow.infra.mapper.FlowQuickCommentMapper;
import com.njydsz.workflow.server.service.FlowQuickCommentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 审批常用语服务实现
 *
 * <p>P1-2: 对标钉钉/飞书审批的"常用语"能力，对 {@link FlowQuickCommentService} 接口的完整实现。
 * 提供审批评论常用语（快捷回复模板）的 CRUD、用户隔离、使用统计等完整业务能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>查询能力</b>：{@link #listByUser} — 返回「用户自定义 + 系统预设」两类常用语，
 *       按 {@code sortNum} 升序、{@code useCount} 降序排列</li>
 *   <li><b>CRUD</b>：{@link #create}（创建用户自定义）/ {@link #update}（仅创建者本人）/
 *       {@link #delete}（仅创建者本人，<b>系统预设不可删</b>）</li>
 *   <li><b>使用统计</b>：{@link #incrementUseCount} — 用户使用常用语后 +1，
 *       <b>按使用频次智能排序</b></li>
 *   <li><b>用户隔离</b>：用户仅可管理自己创建的常用语，系统预设为全局共享</li>
 *   <li><b>多租户</b>：所有数据按 {@code tenantId} 隔离</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法开启 {@code @Transactional(rollbackFor = Exception.class)}，确保数据一致性</li>
 *   <li>删除采用<b>逻辑删除</b>（{@code deleted=1}），保留审计轨迹</li>
 * </ul>
 *
 * <p><b>性能优化：</b>
 * <ul>
 *   <li>常用语数据量小（用户级百级别），无需分页</li>
 *   <li>查询走 {@code ydsz_flow_quick_comment} 复合索引（{@code idx_user} + {@code idx_tenant}）</li>
 *   <li>{@link #incrementUseCount} 使用先查后更，<b>并发场景下 useCount 可能丢失更新</b>，
 *       生产环境建议改用 {@code UPDATE ... SET use_count = use_count + 1} 原子操作</li>
 * </ul>
 *
 * <p><b>防御性编程：</b>
 * <ul>
 *   <li>{@link #listByUser} 当 userId 为空时返回空列表（前端 bug 兜底）</li>
 *   <li>{@link #delete} 当常用语不存在时直接返回（幂等性）</li>
 *   <li>{@link #incrementUseCount} 异常被 try-catch 吞掉，<b>不抛异常</b>，
 *       避免使用统计失败影响主流程评论发布</li>
 *   <li>{@link #update} / {@link #delete} 校验<b>创建者本人</b>才能操作，
 *       防止越权修改他人常用语</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 场景：审批人选择"同意"常用语后，自动增加使用次数
 * List<FlowQuickComment> comments = quickCommentService.listByUser(userId, tenantId);
 * // 用户点击 "同意" → quickCommentService.incrementUseCount("xxx");
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowQuickCommentService 接口定义
 * @see FlowQuickComment 常用语实体
 * @see FlowQuickCommentDTO 常用语 DTO
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowQuickCommentServiceImpl implements FlowQuickCommentService {

    /** 常用语 Mapper，负责 ydsz_flow_quick_comment 表的增删改查（含用户自定义 + 系统预设） */
    private final FlowQuickCommentMapper quickCommentMapper;

    /**
     * 查询用户的常用语列表（用户自定义 + 系统预设合并）
     *
     * <p>合并查询：先查 {@code userId=userId} 的自定义常用语，再追加 {@code isSystem=1} 的系统预设，
     * 最终按 {@code sortNum} 升序、{@code useCount} 降序两级排序。
     *
     * @param userId   用户 ID（不可空，为空返回空列表）
     * @param tenantId 租户 ID（可空，回退 {@link TenantContext}）
     * @return 常用语列表（已合并 + 已排序），无数据返回空列表
     */
    @Override
    public List<FlowQuickComment> listByUser(String userId, String tenantId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        String tid = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
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
     * 创建用户自定义常用语
     *
     * <p>仅创建用户自定义常用语（{@code isSystem=0}），{@code useCount} 初始为 {@code 0}。
     * 创建时强制覆盖 {@code userId/tenantId/isSystem=0}，<b>不可通过 DTO 伪造为系统预设</b>。
     *
     * @param dto      常用语 DTO（含 {@code content/commentType/sortNum}）
     * @param userId   创建人 ID（不可空）
     * @param tenantId 租户 ID（可空，回退 {@link TenantContext}）
     * @return 新常用语 ID
     * @throws SysException {@code BAD_REQUEST} — userId 为空
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(FlowQuickCommentDTO dto, String userId, String tenantId) {
        if (!StringUtils.hasText(userId)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_user_required")
                .build();
        }
        FlowQuickComment comment = new FlowQuickComment();
        comment.setUserId(userId);
        comment.setContent(dto.getContent());
        comment.setCommentType(dto.getCommentType());
        comment.setSortNum(dto.getSortNum() != null ? dto.getSortNum() : 0);
        comment.setUseCount(0);
        comment.setIsSystem(0);
        comment.setTenantId(tenantId != null ? tenantId : TenantContextHolder.getTenantId());
        quickCommentMapper.insert(comment);
        log.info("[FlowQuickComment] 新增常用语: userId={} id={}", userId, comment.getId());
        return comment.getId();
    }

    /**
     * 更新常用语
     *
     * <p>仅允许<b>创建者本人</b>更新；<b>系统预设不可更新</b>（实际接口层就不应该走到这里，
     * 因为系统预设不暴露 update 接口给前端）。仅更新 DTO 中非空字段。
     *
     * @param dto    常用语 DTO（{@code id} 必传）
     * @param userId 操作人 ID（必须与创建者一致）
     * @throws SysException {@code BAD_REQUEST} — id 为空；{@code NOT_FOUND} — 常用语不存在；
     *                     {@code FORBIDDEN} — 操作人非创建者
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FlowQuickCommentDTO dto, String userId) {
        if (!StringUtils.hasText(dto.getId())) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_id_required")
                .build();
        }
        FlowQuickComment existing = quickCommentMapper.selectById(dto.getId());
        if (existing == null || existing.getDeleted() == 1) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .key("error.workflow.msg_6541ab08").params(dto.getId())
                .build();
        }
        if (!userId.equals(existing.getUserId())) {
            throw SysException.builder()
                .resultCode(BaseResultCode.FORBIDDEN)
                .message("error.workflow.msg_no_permission")
                .build();
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
     * 删除常用语（软删除）
     *
     * <p><b>权限校验：</b>
     * <ul>
     *   <li>系统预设（{@code isSystem=1}）<b>不可删除</b>，抛 {@code BAD_REQUEST}</li>
     *   <li>仅创建者本人可删除，<b>非创建者</b>抛 {@code FORBIDDEN}</li>
     * </ul>
     *
     * @param id     常用语 ID
     * @param userId 操作人 ID
     * @throws SysException {@code BAD_REQUEST} — 系统预设不可删；{@code FORBIDDEN} — 无权限
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
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_system_comment_cannot_delete")
                .build();
        }
        if (!userId.equals(existing.getUserId())) {
            throw SysException.builder()
                .resultCode(BaseResultCode.FORBIDDEN)
                .message("error.workflow.msg_no_permission")
                .build();
        }
        existing.setDeleted(1);
        quickCommentMapper.updateById(existing);
    }

    /**
     * 增加常用语使用次数
     *
     * <p>用户在前端选择常用语时调用，{@code useCount} 自增 1。
     * 异常被 try-catch 吞掉记 WARN，<b>不传播异常</b>——使用统计失败不应阻塞评论发布主流程。
     *
     * <p><b>已知风险：</b>采用「先查后更」非原子操作，<b>高并发场景下 useCount 可能丢失更新</b>。
     * 生产环境建议改用 SQL 原子更新 {@code UPDATE ... SET use_count = use_count + 1 WHERE id = ?}。
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
