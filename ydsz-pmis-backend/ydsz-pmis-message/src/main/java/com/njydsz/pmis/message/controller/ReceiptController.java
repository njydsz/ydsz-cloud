package com.njydsz.pmis.message.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.message.dto.ReceiptCallbackDTO;
import com.njydsz.pmis.message.entity.MsgReceiptDO;
import com.njydsz.pmis.message.service.ReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 消息回执 Controller。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "消息回执", description = "服务商回执回调与查询")
@RestController
@RequestMapping("/message/receipt")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    @Operation(summary = "回执回调")
    @PostMapping("/callback")
    public Result<Void> callback(@RequestBody ReceiptCallbackDTO dto) {
        // TODO 权限码
        receiptService.callback(dto);
        return Result.ok();
    }

    @Operation(summary = "按日志 ID 查询回执列表")
    @GetMapping("/{logId}")
    public Result<List<MsgReceiptDO>> listByLogId(@PathVariable String logId) {
        // TODO 权限码
        return Result.ok(receiptService.listByLogId(logId));
    }
}
