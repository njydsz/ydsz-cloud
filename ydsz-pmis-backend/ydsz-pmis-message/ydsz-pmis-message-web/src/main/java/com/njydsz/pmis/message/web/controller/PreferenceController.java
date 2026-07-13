package com.njydsz.pmis.message.web.controller.config;

import com.njydsz.pmis.common.lock.annotation.Idempotent;

import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.message.domain.dto.config.PreferenceUpsertDTO;
import com.njydsz.pmis.message.domain.entity.config.MsgPreferenceDO;
import com.njydsz.pmis.message.server.service.config.PreferenceService;
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
 * 用户消息偏好 Controller。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "消息偏好", description = "用户消息偏好管理")
@RestController
@RequestMapping("/message/preference")
@RequiredArgsConstructor
public class PreferenceController {

    /** 用户消息偏好服务 */
    private final PreferenceService preferenceService;

    /**
     * 新增或更新用户消息偏好。
     *
     * @param dto 偏好保存请求体
     * @return 统一响应结果，包含偏好记录
     */
    @Operation(summary = "新增/更新偏好")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_PREFERENCE_UPDATE)
    @Idempotent(key = "preference:upsert", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<MsgPreferenceDO> upsert(@Valid @RequestBody PreferenceUpsertDTO dto) {
        return BaseResponse.ok(preferenceService.upsert(dto));
    }

    /**
     * 查询用户全部消息偏好。
     *
     * @param userId 用户 ID
     * @return 统一响应结果，包含偏好列表
     */
    @Operation(summary = "查询用户所有偏好")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_PREFERENCE_VIEW)
    @GetMapping("/{userId}")
    public BaseResponse<List<MsgPreferenceDO>> listByUser(@PathVariable String userId) {
        return BaseResponse.ok(preferenceService.listByUser(userId));
    }

    /**
     * 按用户、通道和业务类型查询偏好。
     *
     * @param userId   用户 ID
     * @param channel  通道
     * @param bizType  业务类型
     * @return 统一响应结果，包含偏好记录
     */
    @Operation(summary = "按用户+通道+业务类型查询偏好")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_PREFERENCE_VIEW)
    @GetMapping("/{userId}/{channel}/{bizType}")
    public BaseResponse<MsgPreferenceDO> getByUser(@PathVariable String userId,
                                             @PathVariable String channel,
                                             @PathVariable String bizType) {
        return BaseResponse.ok(preferenceService.getByUser(userId, channel, bizType));
    }

    /**
     * 删除用户消息偏好。
     *
     * @param id 偏好记录 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除偏好")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_PREFERENCE_DELETE)
    @Idempotent(key = "preference:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        preferenceService.delete(id);
        return BaseResponse.ok();
    }
}
