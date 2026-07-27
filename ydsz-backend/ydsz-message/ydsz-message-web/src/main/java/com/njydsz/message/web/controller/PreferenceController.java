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
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.domain.converter.MessageConverter;
import com.njydsz.message.domain.dto.config.PreferenceUpsertDTO;
import com.njydsz.message.domain.entity.config.MsgPreference;
import com.njydsz.message.domain.vo.MsgPreferenceVO;
import com.njydsz.message.server.service.config.PreferenceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;

/**
 * 用户消息偏好 Controller。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "消息偏好", description = "用户消息偏好管理")
@RestController
@RequestMapping("/api/v1/message/preference")
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
    @Idempotent(key = "ydsz:message:PreferenceController:upsert:lock", ttlSeconds = 5)
    @Audit(module = "偏好设置", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'upsert'")
    @RateLimit(resource = "message.preference.upsert", threshold = 50)
    @PostMapping
    public BaseResponse<MsgPreferenceVO> upsert(@Valid @RequestBody PreferenceUpsertDTO dto) {
        return BaseResponse.success(MessageConverter.INSTANT.entityToVO(preferenceService.upsert(dto)));
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
    public BaseResponse<List<MsgPreferenceVO>> listByUser(@PathVariable String userId) {
        return BaseResponse.success(MessageConverter.INSTANT.preferenceListToVO(preferenceService.listByUser(userId)));
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
    public BaseResponse<MsgPreferenceVO> getByUser(@PathVariable String userId,
                                             @PathVariable String channel,
                                             @PathVariable String bizType) {
        return BaseResponse.success(MessageConverter.INSTANT.entityToVO(preferenceService.getByUser(userId, channel, bizType)));
    }

    /**
     * 删除用户消息偏好。
     *
     * @param id 偏好记录 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除偏好")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_PREFERENCE_DELETE)
    @Idempotent(key = "ydsz:message:PreferenceController:delete:lock", ttlSeconds = 5)
    @Audit(module = "偏好设置", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'delete'")
    @RateLimit(resource = "message.preference.delete", threshold = 50)
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        preferenceService.delete(id);
        return BaseResponse.success();
    }
}
