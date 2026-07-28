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
 * <p>提供告警派发记录的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
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
