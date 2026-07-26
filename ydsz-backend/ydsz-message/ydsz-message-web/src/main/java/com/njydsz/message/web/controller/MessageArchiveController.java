package com.njydsz.message.web.controller.archive;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.security.TenantContext;
import com.njydsz.message.domain.entity.core.MsgLogDO;
import com.njydsz.message.server.service.archive.MessageArchiveService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 消息归档搜索 Controller（P0-5）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "消息归档搜索", description = "消息发送日志全文搜索")
@RestController
@RequestMapping("/archive/search")
@RequiredArgsConstructor
public class MessageArchiveController {

    private final MessageArchiveService messageArchiveService;

    @Operation(summary = "全文搜索消息日志")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_LIST)
    @GetMapping
    public BaseResponse<Page<MsgLogDO>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        Page<MsgLogDO> result = messageArchiveService.search(keyword, channel, status, bizType,
                startTime, endTime, TenantContext.getTenantId(), pageNum, pageSize);
        return BaseResponse.success(result);
    }
}
