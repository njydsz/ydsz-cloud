package com.njydsz.pmis.message.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.message.entity.MessageTemplateDO;
import com.njydsz.pmis.message.service.MessageTemplateServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
public class MessageTemplateController {

    private final MessageTemplateServiceImpl templateService;

    @Operation(summary = "创建模板")
    @PostMapping
    public R<Long> create(@RequestBody MessageTemplateDTO dto) {
        return R.ok(templateService.create(dto.toDO()));
    }

    @Operation(summary = "更新模板")
    @PutMapping
    public R<Void> update(@RequestBody MessageTemplateDTO dto) {
        MessageTemplateDO t = dto.toDO();
        t.setId(dto.getId());
        templateService.update(t);
        return R.ok();
    }

    @Operation(summary = "删除模板")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return R.ok();
    }

    @Operation(summary = "模板详情")
    @GetMapping("/{id}")
    public R<MessageTemplateDO> get(@PathVariable Long id) {
        return R.ok(templateService.getById(id));
    }

    @Operation(summary = "模板分页")
    @GetMapping("/page")
    public R<Page<MessageTemplateDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String keyword) {
        return R.ok(templateService.page(page, size, channel, keyword));
    }

    @Operation(summary = "按通道列出模板")
    @GetMapping("/list")
    public R<List<MessageTemplateDO>> listByChannel(@RequestParam String channel) {
        return R.ok(templateService.listByChannel(channel));
    }

    @lombok.Data
    public static class MessageTemplateDTO {
        private Long id;
        private String templateCode;
        private String channel;
        private String subject;
        private String content;
        private String provider;
        private String providerKey;
        private String signName;
        private String status;
        private String description;

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
