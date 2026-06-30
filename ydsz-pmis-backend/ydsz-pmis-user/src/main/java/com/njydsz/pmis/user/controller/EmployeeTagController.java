package com.njydsz.pmis.user.controller;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.user.dto.EmployeeTagCreateDTO;
import com.njydsz.pmis.user.entity.EmployeeTagDO;
import com.njydsz.pmis.user.service.EmployeeTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.List;

/**
 * 人员标签 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "人员标签管理")
@RestController
@RequestMapping("/api/v1/employee-tags")
@RequiredArgsConstructor
public class EmployeeTagController {

    private final EmployeeTagService tagService;

    @Operation(summary = "添加标签")
    @PrePermission("resource:tag:add")
    @OperationLog(module = "人员标签", action = "添加标签", bizType = "EMPLOYEE_TAG")
    @PostMapping
    public R<Long> add(@Valid @RequestBody EmployeeTagCreateDTO dto) {
        return R.ok(tagService.add(dto));
    }

    @Operation(summary = "删除标签")
    @PrePermission("resource:tag:remove")
    @OperationLog(module = "人员标签", action = "删除标签", bizType = "EMPLOYEE_TAG")
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        tagService.remove(id);
        return R.ok();
    }

    @Operation(summary = "覆盖式设置员工标签")
    @PrePermission("resource:tag:replace")
    @OperationLog(module = "人员标签", action = "覆盖员工标签", bizType = "EMPLOYEE_TAG")
    @PutMapping("/replace/{employeeId}")
    public R<Void> replaceByEmployee(@PathVariable Long employeeId,
                                     @RequestBody List<EmployeeTagCreateDTO> tags) {
        tagService.replaceByEmployee(employeeId, tags);
        return R.ok();
    }

    @Operation(summary = "按员工查询")
    @GetMapping("/by-employee/{employeeId}")
    public R<List<EmployeeTagDO>> listByEmployee(@PathVariable Long employeeId) {
        return R.ok(tagService.listByEmployee(employeeId));
    }

    @Operation(summary = "按标签筛选候选人")
    @GetMapping("/candidates")
    public R<List<EmployeeTagDO>> candidates(@RequestParam String tagType,
                                             @RequestParam(required = false) String tagCode) {
        return R.ok(tagService.findCandidates(tagType, tagCode));
    }
}
