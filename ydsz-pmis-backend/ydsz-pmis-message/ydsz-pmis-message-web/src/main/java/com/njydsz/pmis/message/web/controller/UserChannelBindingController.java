package com.njydsz.pmis.message.web.controller.config;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.message.domain.dto.config.UserChannelBindingDTO;
import com.njydsz.pmis.message.domain.entity.config.MsgUserChannelDO;
import com.njydsz.pmis.message.server.service.config.UserChannelBindingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户通道绑定 Controller。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Tag(name = "用户通道绑定", description = "用户通道联系方式绑定/查询/删除")
@RestController
@RequestMapping("/user-channels")
@RequiredArgsConstructor
public class UserChannelBindingController {

    private final UserChannelBindingService userChannelBindingService;

    @Operation(summary = "新增或更新通道绑定")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_SEND)
    @PostMapping
    public BaseResponse<MsgUserChannelDO> upsert(@Valid @RequestBody UserChannelBindingDTO dto) {
        return BaseResponse.ok(userChannelBindingService.upsert(dto));
    }

    @Operation(summary = "查询当前用户所有通道绑定")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_LIST)
    @GetMapping("/mine")
    public BaseResponse<List<MsgUserChannelDO>> listMine() {
        return BaseResponse.ok(userChannelBindingService.listByUser(AuthContext.getUserId()));
    }

    @Operation(summary = "按用户ID查询通道绑定")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_LIST)
    @GetMapping("/user/{userId}")
    public BaseResponse<List<MsgUserChannelDO>> listByUser(@PathVariable String userId) {
        return BaseResponse.ok(userChannelBindingService.listByUser(userId));
    }

    @Operation(summary = "删除通道绑定")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_SEND)
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        userChannelBindingService.delete(id);
        return BaseResponse.ok();
    }
}
