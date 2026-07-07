package com.njydsz.pmis.message.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.entity.PageQuery;
import com.njydsz.pmis.message.entity.MsgAggregateDO;
import com.njydsz.pmis.message.service.AggregateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 聚合批次 Controller。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "聚合批次", description = "消息聚合批次查询与刷新")
@RestController
@RequestMapping("/message/aggregate")
@RequiredArgsConstructor
public class AggregateController {

    private final AggregateService aggregateService;

    @Operation(summary = "聚合批次分页")
    @GetMapping("/page")
    public Result<Page<MsgAggregateDO>> page(PageQuery query) {
        // TODO 权限码
        return Result.ok(aggregateService.page(query));
    }

    @Operation(summary = "按聚合组+接收人强制刷新")
    @PostMapping("/flush")
    public Result<Integer> flushByGroup(@RequestParam String group, @RequestParam String receiver) {
        // TODO 权限码
        return Result.ok(aggregateService.flushByGroup(group, receiver));
    }

    @Operation(summary = "刷新到期批次")
    @PostMapping("/flush-due")
    public Result<Integer> flushDue() {
        // TODO 权限码
        return Result.ok(aggregateService.flushDue());
    }
}
