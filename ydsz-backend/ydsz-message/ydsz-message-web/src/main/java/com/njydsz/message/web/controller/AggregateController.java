package com.njydsz.message.web.controller.batch;

import org.springframework.web.bind.annotation.GetMapping;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.domain.converter.MessageConverter;
import com.njydsz.message.domain.entity.batch.MsgAggregate;
import com.njydsz.message.domain.vo.MsgAggregateVO;
import com.njydsz.message.server.service.batch.AggregateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;

/**
 * 聚合批次 Controller。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "聚合批次", description = "消息聚合批次查询与刷新")
@RestController
@RequestMapping("/api/v1/message/aggregate")
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
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_AGGREGATE_LIST)
    @GetMapping("/page")
    public BaseResponse<Page<MsgAggregateVO>> page(PageQuery query) {
        Page<MsgAggregate> page = aggregateService.page(query);
        Page<MsgAggregateVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(MessageConverter.INSTANT.aggregateListToVO(page.getRecords()));
        return BaseResponse.success(voPage);
    }

    /**
     * 按聚合组和接收人强制刷新聚合批次。
     *
     * @param group    聚合组标识
     * @param receiver 接收人标识
     * @return 统一响应结果，包含刷新的消息数量
     */
    @Operation(summary = "按聚合组+接收人强制刷新")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_AGGREGATE_REFRESH)
    @Idempotent(key = "ydsz:message:AggregateController:flushByGroup:lock", ttlSeconds = 5)
    @Audit(module = "聚合批次", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'flushByGroup'")
    @RateLimit(resource = "message.aggregate.flushByGroup", threshold = 50)
    @PostMapping("/flush")
    public BaseResponse<Integer> flushByGroup(@RequestParam String group, @RequestParam String receiver) {
        return BaseResponse.success(aggregateService.flushByGroup(group, receiver));
    }

    /**
     * 刷新全部到期聚合批次。
     *
     * @return 统一响应结果，包含刷新的消息数量
     */
    @Operation(summary = "刷新到期批次")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_AGGREGATE_REFRESH)
    @Idempotent(key = "ydsz:message:AggregateController:flushDue:lock", ttlSeconds = 5)
    @Audit(module = "聚合批次", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'flushDue'")
    @RateLimit(resource = "message.aggregate.flushDue", threshold = 50)
    @PostMapping("/flushDue")
    public BaseResponse<Integer> flushDue() {
        return BaseResponse.success(aggregateService.flushDue());
    }
}
