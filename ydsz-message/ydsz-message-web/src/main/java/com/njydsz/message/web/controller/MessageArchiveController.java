package com.njydsz.message.web.controller.archive;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.message.domain.converter.MessageConverter;
import com.njydsz.message.domain.entity.core.MsgLog;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.server.service.archive.MessageArchiveService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 消息归档搜索（Archive Search）Controller。
 *
 * <p>提供<b>消息发送日志的全文搜索</b>能力。
 * 基于 PostgreSQL 的全文索引（{@code to_tsvector('simple', content)} / GIN 索引）实现，
 * 支持对消息主题 / 内容 / 接收人 / 业务单据的全文检索，常用于：
 * <ul>
 *   <li>运营查询某段时间的某类通知触达情况</li>
 *   <li>客服查询某用户收到的全部消息历史</li>
 *   <li>合规审计批量导出某业务单据的全部通知记录</li>
 * </ul>
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/archive/search/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>全文搜索</b>：{@code GET /} — 按关键字 / 通道 / 状态 / 业务类型 / 时间范围分页搜索</li>
 * </ul>
 *
 * <p><b>搜索维度：</b>
 * <ul>
 *   <li>{@code keyword}：全文关键字（命中主题 / 内容 / 接收人 / 业务单据）</li>
 *   <li>{@code channel}：通道（SMS / EMAIL / IN_APP / DINGTALK / FEISHU / WECOM / WEBSOCKET）</li>
 *   <li>{@code status}：消息状态（PENDING / SENDING / SUCCESS / FAILED / DEAD 等）</li>
 *   <li>{@code bizType}：业务类型</li>
 *   <li>{@code startTime / endTime}：发送时间范围（ISO 8601）</li>
 *   <li>{@code pageNum / pageSize}：分页参数（默认 1 / 20）</li>
 * </ul>
 *
 * <p><b>多租户隔离：</b>所有查询按 {@link com.njydsz.common.security.TenantContext#getTenantId()} 当前租户过滤，
 * 跨租户日志不可见。
 *
 * <p><b>性能特性：</b>
 * <ul>
 *   <li>数据库层 GIN 索引加速全文检索</li>
 *   <li>分页使用 {@code LIMIT / OFFSET}，深分页时建议使用 {@code searchAfter} 优化（待 P2 优化）</li>
 *   <li>查询结果走 {@code MessageConverter.logListToVO} 转 VO 返回，不暴露实体内部细节</li>
 * </ul>
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>只读接口，仅启用 {@code @AuthApiPermission} 权限校验</li>
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#NOTIF_MESSAGE_LIST} 权限码</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.archive.MessageArchiveService 消息归档服务
 * @see com.njydsz.message.domain.entity.core.MsgLog 发送日志实体
 */
@Tag(name = "消息归档搜索", description = "消息发送日志全文搜索")
@RestController
@RequestMapping("/api/v1/message/archive/search")
@RequiredArgsConstructor
public class MessageArchiveController {

    private final MessageArchiveService messageArchiveService;

    /**
     * 全文搜索消息发送日志。
     *
     * <p>基于 PostgreSQL 全文索引（GIN）对主题/内容/接收人/业务单据做关键字检索，
     * 按当前租户隔离，返回分页 VO。所有查询参数均可空：全空时退化为按时间倒序的分页列举。
     * 只读接口，需 {@code NOTIF_MESSAGE_LIST} 权限。
     *
     * @param keyword  全文关键字（可空）
     * @param channel  通道过滤（SMS/EMAIL/IN_APP/DINGTALK/FEISHU/WECOM/WEBSOCKET，可空）
     * @param status   消息状态过滤（PENDING/SENDING/SUCCESS/FAILED/DEAD 等，可空）
     * @param bizType  业务类型（可空）
     * @param startTime 发送时间下界（ISO 8601，可空）
     * @param endTime   发送时间上界（ISO 8601，可空）
     * @param pageNum  页码（默认 1）
     * @param pageSize 每页大小（默认 20）
     * @return 分页的消息日志 VO（已脱敏，不暴露实体内部字段）
     */
    @Operation(summary = "全文搜索消息日志")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_LIST)
    @GetMapping
    public PageResponse<List<MsgLogVO>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        Page<MsgLog> result = messageArchiveService.search(keyword, channel, status, bizType,
                startTime, endTime, TenantContextHolder.getTenantId(), pageNum, pageSize);
        return PageResponse.success(
                result.getTotal(),
                result.getCurrent(),
                result.getSize(),
                MessageConverter.INSTANT.logListToVO(result.getRecords()));
    }
}
