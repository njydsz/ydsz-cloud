package com.njydsz.pmis.message.controller;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.message.dto.PreferenceUpsertDTO;
import com.njydsz.pmis.message.entity.MsgPreferenceDO;
import com.njydsz.pmis.message.service.PreferenceService;
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

    private final PreferenceService preferenceService;

    @Operation(summary = "新增/更新偏好")
    @PrePermission(PermissionCodes.MESSAGE_PREFERENCE_UPDATE)
    @Idempotent(key = "preference:upsert", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<MsgPreferenceDO> upsert(@Valid @RequestBody PreferenceUpsertDTO dto) {
        return Result.ok(preferenceService.upsert(dto));
    }

    @Operation(summary = "查询用户所有偏好")
    @PrePermission(PermissionCodes.MESSAGE_PREFERENCE_VIEW)
    @GetMapping("/{userId}")
    public Result<List<MsgPreferenceDO>> listByUser(@PathVariable String userId) {
        return Result.ok(preferenceService.listByUser(userId));
    }

    @Operation(summary = "按用户+通道+业务类型查询偏好")
    @PrePermission(PermissionCodes.MESSAGE_PREFERENCE_VIEW)
    @GetMapping("/{userId}/{channel}/{bizType}")
    public Result<MsgPreferenceDO> getByUser(@PathVariable String userId,
                                             @PathVariable String channel,
                                             @PathVariable String bizType) {
        return Result.ok(preferenceService.getByUser(userId, channel, bizType));
    }

    @Operation(summary = "删除偏好")
    @PrePermission(PermissionCodes.MESSAGE_PREFERENCE_DELETE)
    @Idempotent(key = "preference:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        preferenceService.delete(id);
        return Result.ok();
    }
}
