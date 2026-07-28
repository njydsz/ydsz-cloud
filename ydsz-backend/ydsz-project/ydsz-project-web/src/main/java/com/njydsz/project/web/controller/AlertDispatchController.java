package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.alert.AlertDispatch;
import com.njydsz.project.server.service.AlertDispatchService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.AlertDispatchVO;
import com.njydsz.project.domain.dto.put.AlertDispatchPutDTO;
import com.njydsz.project.domain.dto.post.AlertDispatchPostDTO;

/**
 * 告警派发 Controller
 *
 * <p>提供告警派发记录的 REST API，是「项目管理 / 告警中心」业务域的 Controller。
 * 对标大厂 PMIS / 监控告警系统中的「告警分发 / 告警路由 / 告警推送」管理界面。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #update} /
 *       {@link #remove}</li>
 * </ul>
 *
 * <p><b>告警等级：</b>P0 紧急 / P1 重要 / P2 次要 / P3 提示。
 *
 * <p><b>推送通道：</b>通过 {@code pushChannels} 字段配置（INAPP 应用内 / EMAIL 邮件 /
 * SMS 短信 / IM 企微 / WEBHOOK Webhook）。
 *
 * <p><b>安全控制：</b>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计</li>
 *   <li>分页查询受 {@code DataScopeInterceptor} 数据权限控制</li>
 *   <li>告警派发通常由系统内部触发，外部调用方需具备 {@code project:alert:dispatch} 权限码</li>
 * </ul>
 *
 * <p><b>典型链路：</b>业务异常 / 定时任务失败 / 预算超阈值 → 调用 {@link #save} 创建告警
 * → {@code ydsz-message} 通知中心异步推送到目标通道。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.AlertDispatchService 告警 Service
 * @see com.njydsz.project.domain.entity.alert.AlertDispatch 告警实体
 */
@RestController
@RequestMapping("/api/v1/project/alert/dispatch")
@RequiredArgsConstructor
public class AlertDispatchController {

    private final AlertDispatchService service;

    /**
     * 按 ID 查询告警派发记录
     *
     * @param id 派发记录主键 ID
     * @return 派发记录视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<AlertDispatchVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询告警派发记录列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页派发记录视图对象
     */
    @GetMapping("/page")
    public PageResponse<AlertDispatchVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<AlertDispatch> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.alertDispatchListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建告警派发记录
     *
     * @param dto 派发记录创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create AlertDispatch")
    public BaseResponse<Boolean> save(@RequestBody AlertDispatchPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新告警派发记录
     *
     * @param dto 派发记录更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update AlertDispatch")
    public BaseResponse<Boolean> update(@RequestBody AlertDispatchPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除告警派发记录
     *
     * @param id 派发记录主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete AlertDispatch")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
