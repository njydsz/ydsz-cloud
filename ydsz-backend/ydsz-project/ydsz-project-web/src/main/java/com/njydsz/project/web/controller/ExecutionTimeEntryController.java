package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.execution.ExecutionTimeEntry;
import com.njydsz.project.server.service.ExecutionTimeEntryService;

import java.util.List;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ExecutionTimeEntryVO;
import com.njydsz.project.domain.dto.post.ExecutionTimeEntryPostDTO;
import com.njydsz.project.domain.dto.put.ExecutionTimeEntryPutDTO;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 工时录入 Controller
 *
 * <p>提供工时录入记录的 REST API，是「项目管理 / 工时管理」业务域的 Controller。
 * 对标大厂 PMIS / 工时管理系统的「项目工时 / 工时填报 / 工时审批 / 工时成本核算」管理界面。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #update} /
 *       {@link #remove}</li>
 * </ul>
 *
 * <p><b>工时类型：</b>可计费工时 / 不可计费工时 / 休假 / 培训 / 行政。
 *
 * <p><b>工时审批：</b>工时由 PM 审批后生效，审批通过后联动
 * {@code ydsz_cost_allocation} 成本归集，基于 {@code ydsz_rate_internal} 内部费率计算人力成本。
 *
 * <p><b>安全控制：</b>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计</li>
 *   <li>员工只能查看 / 修改自己的工时（{@code applicantId} 限制）</li>
 *   <li>已审批的工时禁止修改，错误需通过工时调整单纠正</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.ExecutionTimeEntryService 工时 Service
 * @see com.njydsz.project.domain.entity.execution.ExecutionTimeEntry 工时实体
 */
@RestController
@RequestMapping("/api/v1/project/execution/time/entry")
@RequiredArgsConstructor
public class ExecutionTimeEntryController {

    private final ExecutionTimeEntryService service;

    /**
     * 按 ID 查询工时记录
     *
     * @param id 工时记录主键 ID
     * @return 工时记录视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ExecutionTimeEntryVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询工时记录列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页工时记录视图对象
     */
    @GetMapping("/page")
    public PageResponse<List<ExecutionTimeEntryVO>> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ExecutionTimeEntry> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.executionTimeEntryListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建工时记录
     *
     * @param dto 工时记录创建入参
     * @return 是否创建成功
     */
    @Idempotent(key = "ydsz:project:ExecutionTimeEntryController:save:lock", ttlSeconds = 5)
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ExecutionTimeEntry")
    public BaseResponse<Boolean> save(@RequestBody ExecutionTimeEntryPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新工时记录
     *
     * @param dto 工时记录更新入参
     * @return 是否更新成功
     */
    @Idempotent(key = "ydsz:project:ExecutionTimeEntryController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ExecutionTimeEntry")
    public BaseResponse<Boolean> update(@RequestBody ExecutionTimeEntryPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除工时记录
     *
     * @param id 工时记录主键 ID
     * @return 是否删除成功
     */
    @Idempotent(key = "ydsz:project:ExecutionTimeEntryController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ExecutionTimeEntry")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
