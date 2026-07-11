package com.njydsz.pmis.message.web.controller.batch;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.entity.PageQuery;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.message.domain.entity.batch.MsgAggregateDO;
import com.njydsz.pmis.message.server.service.batch.AggregateService;
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

    /** 聚合批次服务 */
    private final AggregateService aggregateService;

    /**
     * 分页查询聚合批次列表。
     *
     * @param query 分页查询参数
     * @return 统一响应结果，包含聚合批次分页数据
     */
    @Operation(summary = "聚合批次分页")
    @PrePermission(PermissionCodes.MESSAGE_AGGREGATE_LIST)
    @GetMapping("/page")
    public Result<Page<MsgAggregateDO>> page(PageQuery query) {
        return Result.ok(aggregateService.page(query));
    }

    /**
     * 按聚合组和接收人强制刷新聚合批次。
     *
     * @param group    聚合组标识
     * @param receiver 接收人标识
     * @return 统一响应结果，包含刷新的消息数量
     */
    @Operation(summary = "按聚合组+接收人强制刷新")
    @PrePermission(PermissionCodes.MESSAGE_AGGREGATE_REFRESH)
    @Idempotent(key = "aggregate:flushByGroup", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/flush")
    public Result<Integer> flushByGroup(@RequestParam String group, @RequestParam String receiver) {
        return Result.ok(aggregateService.flushByGroup(group, receiver));
    }

    /**
     * 刷新全部到期聚合批次。
     *
     * @return 统一响应结果，包含刷新的消息数量
     */
    @Operation(summary = "刷新到期批次")
    @PrePermission(PermissionCodes.MESSAGE_AGGREGATE_REFRESH)
    @Idempotent(key = "aggregate:flushDue", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/flushDue")
    public Result<Integer> flushDue() {
        return Result.ok(aggregateService.flushDue());
    }
}
