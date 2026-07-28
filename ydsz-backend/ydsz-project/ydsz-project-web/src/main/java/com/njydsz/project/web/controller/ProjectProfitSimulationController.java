package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectProfitSimulation;
import com.njydsz.project.server.service.ProjectProfitSimulationService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectProfitSimulationVO;
import com.njydsz.project.domain.dto.post.ProjectProfitSimulationPostDTO;
import com.njydsz.project.domain.dto.put.ProjectProfitSimulationPutDTO;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 利润模拟 Controller
 *
 * <p>提供项目利润模拟的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/project/profit/simulation")
@RequiredArgsConstructor
public class ProjectProfitSimulationController {

    private final ProjectProfitSimulationService service;

    /**
     * 按 ID 查询利润模拟
     *
     * @param id 利润模拟主键 ID
     * @return 利润模拟视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectProfitSimulationVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询利润模拟列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页利润模拟视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectProfitSimulationVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectProfitSimulation> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectProfitSimulationListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建利润模拟
     *
     * @param dto 利润模拟创建入参
     * @return 是否创建成功
     */
    @Idempotent(key = "ydsz:project:ProjectProfitSimulationController:save:lock", ttlSeconds = 5)
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectProfitSimulation")
    public BaseResponse<Boolean> save(@RequestBody ProjectProfitSimulationPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新利润模拟
     *
     * @param dto 利润模拟更新入参
     * @return 是否更新成功
     */
    @Idempotent(key = "ydsz:project:ProjectProfitSimulationController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectProfitSimulation")
    public BaseResponse<Boolean> update(@RequestBody ProjectProfitSimulationPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除利润模拟
     *
     * @param id 利润模拟主键 ID
     * @return 是否删除成功
     */
    @Idempotent(key = "ydsz:project:ProjectProfitSimulationController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectProfitSimulation")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
