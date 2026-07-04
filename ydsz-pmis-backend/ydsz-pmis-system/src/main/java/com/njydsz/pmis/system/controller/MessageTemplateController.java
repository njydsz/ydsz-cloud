package com.njydsz.pmis.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.system.entity.MessageTemplateDO;
import com.njydsz.pmis.system.service.MessageTemplateServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 消息模板管理 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "消息模板")
@RestController
@RequestMapping("/api/v1/message/template")
@RequiredArgsConstructor
@Validated
public class MessageTemplateController {

    /** 消息模板服务 */
    private final MessageTemplateServiceImpl templateService;

    /**
     * 创建消息模板
     *
     * @param dto 模板参数
     * @return 统一响应结果，包含新建模板 ID
     */
    @Operation(summary = "创建模板")
    @PrePermission("notif:message:send")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody MessageTemplateDTO dto) {
        return Result.ok(templateService.create(dto.toDO()));
    }

    /**
     * 更新消息模板
     *
     * @param dto 模板参数
     * @return 统一响应结果
     */
    @Operation(summary = "更新模板")
    @PrePermission("notif:message:send")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody MessageTemplateDTO dto) {
        MessageTemplateDO t = dto.toDO();
        t.setId(dto.getId());
        templateService.update(t);
        return Result.ok();
    }

    /**
     * 删除消息模板
     *
     * @param id 模板 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除模板")
    @PrePermission("notif:message:send")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(1) Long id) {
        templateService.delete(id);
        return Result.ok();
    }

    /**
     * 查询模板详情
     *
     * @param id 模板 ID
     * @return 统一响应结果，包含模板信息
     */
    @Operation(summary = "模板详情")
    @PrePermission("notif:message:send")
    @GetMapping("/{id}")
    public Result<MessageTemplateDO> get(@PathVariable @Min(1) Long id) {
        return Result.ok(templateService.getById(id));
    }

    /**
     * 模板分页查询
     *
     * @param page    页码
     * @param size    每页大小
     * @param channel 通道（可选）
     * @param keyword 关键字（可选）
     * @return 统一响应结果，包含分页数据
     */
    @Operation(summary = "模板分页")
    @PrePermission("notif:message:send")
    @GetMapping("/page")
    public Result<Page<MessageTemplateDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String keyword) {
        return Result.ok(templateService.page(page, size, channel, keyword));
    }

    /**
     * 按通道列出模板
     *
     * @param channel 通道
     * @return 统一响应结果，包含模板列表
     */
    @Operation(summary = "按通道列出模板")
    @PrePermission("notif:message:send")
    @GetMapping("/list")
    public Result<List<MessageTemplateDO>> listByChannel(@RequestParam String channel) {
        return Result.ok(templateService.listByChannel(channel));
    }

    /**
     * 消息模板表单 DTO
     */
    @lombok.Data
    public static class MessageTemplateDTO {
        /** 模板 ID */
        private Long id;
        /** 模板编码 */
        private String templateCode;
        /** 通道 */
        private String channel;
        /** 主题 */
        private String subject;
        /** 内容 */
        private String content;
        /** 供应商 */
        private String provider;
        /** 供应商密钥 */
        private String providerKey;
        /** 签名 */
        private String signName;
        /** 状态 */
        private String status;
        /** 描述 */
        private String description;

        /**
         * 将 DTO 转换为持久化对象。
         *
         * @return 消息模板持久化对象
         */
        public MessageTemplateDO toDO() {
            MessageTemplateDO t = new MessageTemplateDO();
            t.setTemplateCode(templateCode);
            t.setChannel(channel);
            t.setSubject(subject);
            t.setContent(content);
            t.setProvider(provider);
            t.setProviderKey(providerKey);
            t.setSignName(signName);
            t.setStatus(status);
            t.setDescription(description);
            return t;
        }
    }
}
