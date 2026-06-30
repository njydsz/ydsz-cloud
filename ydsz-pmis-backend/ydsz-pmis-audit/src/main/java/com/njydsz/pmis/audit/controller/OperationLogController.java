package com.njydsz.pmis.audit.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.audit.entity.OperationLogDO;
import com.njydsz.pmis.audit.service.OperationLogServiceImpl;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 操作日志查询 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "操作日志")
@RestController
@RequestMapping("/api/v1/audit/operation")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogServiceImpl service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<PageResult<OperationLogDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String module) {
        return R.ok(PageResult.ofPage(service.page(page, size, userId, bizType, status, module)));
    }

    @Operation(summary = "按用户查询")
    @GetMapping("/by-user")
    public R<List<OperationLogDO>> byUser(@RequestParam Long userId,
                                          @RequestParam(defaultValue = "50") int limit) {
        return R.ok(service.listByUser(userId, limit));
    }

    @Operation(summary = "按业务查询")
    @GetMapping("/by-biz")
    public R<List<OperationLogDO>> byBiz(@RequestParam String bizType,
                                         @RequestParam String bizId,
                                         @RequestParam(defaultValue = "50") int limit) {
        return R.ok(service.listByBiz(bizType, bizId, limit));
    }

    @Operation(summary = "清理 N 天前日志")
    @PostMapping("/clean")
    public R<Integer> clean(@RequestParam(defaultValue = "90") int days) {
        return R.ok(service.cleanBefore(days));
    }
}
