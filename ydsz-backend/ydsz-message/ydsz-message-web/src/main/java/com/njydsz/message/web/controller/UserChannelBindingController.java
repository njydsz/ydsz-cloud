package com.njydsz.message.web.controller.config;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.domain.converter.MessageConverter;
import com.njydsz.message.domain.dto.config.UserChannelBindingDTO;
import com.njydsz.message.domain.entity.config.MsgUserChannel;
import com.njydsz.message.domain.vo.MsgUserChannelVO;
import com.njydsz.message.server.service.config.UserChannelBindingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 用户通道绑定 Controller。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "用户通道绑定", description = "用户通道联系方式绑定/查询/删除")
@RestController
@RequestMapping("/api/v1/message/user-channels")
@RequiredArgsConstructor
public class UserChannelBindingController {

    private final UserChannelBindingService userChannelBindingService;

    @Operation(summary = "新增或更新通道绑定")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
    @Audit(module = "通道绑定", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'upsert'")
    @RateLimit(resource = "message.userchannelbinding.upsert", threshold = 50)
    @Idempotent(key = "ydsz:message:UserChannelBindingController:upsert:lock", ttlSeconds = 5)
    @PostMapping
    public BaseResponse<MsgUserChannelVO> upsert(@Valid @RequestBody UserChannelBindingDTO dto) {
        return BaseResponse.success(MessageConverter.INSTANT.entityToVO(userChannelBindingService.upsert(dto)));
    }

    @Operation(summary = "查询当前用户所有通道绑定")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_LIST)
    @GetMapping("/mine")
    public BaseResponse<List<MsgUserChannelVO>> listMine() {
        return BaseResponse.success(MessageConverter.INSTANT.userChannelListToVO(userChannelBindingService.listByUser(AuthContext.getUserId())));
    }

    @Operation(summary = "按用户ID查询通道绑定")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_LIST)
    @GetMapping("/user/{userId}")
    public BaseResponse<List<MsgUserChannelVO>> listByUser(@PathVariable String userId) {
        return BaseResponse.success(MessageConverter.INSTANT.userChannelListToVO(userChannelBindingService.listByUser(userId)));
    }

    @Operation(summary = "删除通道绑定")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
    @Audit(module = "通道绑定", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'delete'")
    @RateLimit(resource = "message.userchannelbinding.delete", threshold = 50)
    @Idempotent(key = "ydsz:message:UserChannelBindingController:delete:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        userChannelBindingService.delete(id);
        return BaseResponse.success();
    }
}
