package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.execution.ExecutionClosure;
import com.njydsz.project.server.service.ExecutionClosureService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ExecutionClosureVO;
import com.njydsz.project.domain.dto.post.ExecutionClosurePostDTO;
import com.njydsz.project.domain.dto.put.ExecutionClosurePutDTO;

/**
 * 项目结项 Controller
 *
 * <p>提供项目结项记录的 REST API，是「项目管理 / 项目收尾」业务域的 Controller。
 * 对标大厂 PMIS / 项目管理系统中的「项目收尾 / 项目关闭 / 项目验收 / 经验教训总结」管理界面。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #update} /
 *       {@link #remove}</li>
 * </ul>
 *
 * <p><b>结项清单：</b>项目关闭前必须完成客户验收 / 内部验收 / 经验教训 /
 * 文档归档 / 资源释放 / 合同尾款。
 *
 * <p><b>联动链路：</b>结项完成后触发 {@code ProjectClosureCompletedEvent} 领域事件，
 * 联动立项状态推进（{@code EXECUTION → CLOSURE}）、客户满意度采集、售后保期启动。
 *
 * <p><b>安全控制：</b>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计</li>
 *   <li>结项审批需走 {@code ydsz-workflow} 流程引擎，结项状态变更由审批结果驱动</li>
 *   <li>结项数据是组织过程资产（经验教训库）的关键输入，禁止越权篡改</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.ExecutionClosureService 结项 Service
 * @see com.njydsz.project.domain.entity.execution.ExecutionClosure 结项实体
 */
@RestController
@RequestMapping("/api/v1/project/execution/closure")
@RequiredArgsConstructor
public class ExecutionClosureController {

    private final ExecutionClosureService service;

    /**
     * 按 ID 查询结项记录
     *
     * @param id 结项记录主键 ID
     * @return 结项记录视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ExecutionClosureVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询结项记录列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页结项记录视图对象
     */
    @GetMapping("/page")
    public PageResponse<ExecutionClosureVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ExecutionClosure> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.executionClosureListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建结项记录
     *
     * @param dto 结项记录创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ExecutionClosure")
    public BaseResponse<Boolean> save(@RequestBody ExecutionClosurePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新结项记录
     *
     * @param dto 结项记录更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ExecutionClosure")
    public BaseResponse<Boolean> update(@RequestBody ExecutionClosurePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除结项记录
     *
     * @param id 结项记录主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ExecutionClosure")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
