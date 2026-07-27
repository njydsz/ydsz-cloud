package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.dto.ProjectInitiationDTO;
import com.njydsz.project.domain.dto.ProjectInitiationPageQuery;
import com.njydsz.project.domain.vo.ProjectInitiationVO;
import com.njydsz.project.server.service.ProjectInitiationService;

import jakarta.validation.Valid;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目立项 Controller。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/initiation")
@RequiredArgsConstructor
public class ProjectInitiationController {

    private final ProjectInitiationService projectInitiationService;

    @GetMapping("/{id}")
    public BaseResponse<ProjectInitiationVO> getById(@PathVariable String id) {
        return BaseResponse.success(projectInitiationService.getById(id));
    }

    @GetMapping("/code/{projectCode}")
    public BaseResponse<ProjectInitiationVO> getByCode(@PathVariable String projectCode) {
        return BaseResponse.success(projectInitiationService.getByCode(projectCode));
    }

    @GetMapping("/page")
    public PageResponse<ProjectInitiationVO> page(@Valid ProjectInitiationPageQuery query) {
        IPage<ProjectInitiationVO> result = projectInitiationService.page(query);
        return PageResponse.success(result.getRecords(), result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }

    @PostMapping
    @Audit(action = AuditAction.CREATE, module = "PROJECT", description = "创建项目立项")
    public BaseResponse<String> save(@Valid @RequestBody ProjectInitiationDTO dto) {
        return BaseResponse.success(projectInitiationService.save(dto));
    }

    @PutMapping
    @Audit(action = AuditAction.UPDATE, module = "PROJECT", description = "更新项目立项")
    public BaseResponse<Boolean> update(@Valid @RequestBody ProjectInitiationDTO dto) {
        return BaseResponse.success(projectInitiationService.updateById(dto));
    }

    @DeleteMapping("/{id}")
    @Audit(action = AuditAction.DELETE, module = "PROJECT", description = "删除项目立项")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(projectInitiationService.removeById(id));
    }

    @PutMapping("/{id}/stage")
    @Audit(action = AuditAction.UPDATE, module = "PROJECT", description = "推进项目阶段")
    public BaseResponse<Boolean> advanceStage(@PathVariable String id,
                                               @RequestParam String stage,
                                               @RequestParam(required = false) String gate) {
        return BaseResponse.success(projectInitiationService.advanceStage(id, stage, gate));
    }

    @GetMapping("/pm/{pmId}")
    public BaseResponse<List<ProjectInitiationVO>> listByPmId(@PathVariable String pmId) {
        return BaseResponse.success(projectInitiationService.listByPmId(pmId));
    }
}
