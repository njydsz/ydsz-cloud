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

@RestController
@RequestMapping("/api/v1/project/project/profit/simulation")
@RequiredArgsConstructor
public class ProjectProfitSimulationController {
    private final ProjectProfitSimulationService service;

    @GetMapping("/{id}")
    public BaseResponse<ProjectProfitSimulationVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<ProjectProfitSimulationVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectProfitSimulation> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectProfitSimulationListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectProfitSimulation")
    public BaseResponse<Boolean> save(@RequestBody ProjectProfitSimulationPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectProfitSimulation")
    public BaseResponse<Boolean> update(@RequestBody ProjectProfitSimulationPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectProfitSimulation")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
