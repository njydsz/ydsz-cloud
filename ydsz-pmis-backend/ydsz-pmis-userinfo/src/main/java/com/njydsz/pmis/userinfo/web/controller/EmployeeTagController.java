package com.njydsz.pmis.userinfo.web.controller.user;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.userinfo.domain.dto.user.EmployeeTagCreateDTO;
import com.njydsz.pmis.userinfo.domain.entity.user.EmployeeTagDO;
import com.njydsz.pmis.userinfo.server.service.user.EmployeeTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/employeeTags")
@RequiredArgsConstructor
@Validated
public class EmployeeTagController {

    /** 人员标签服务 */
    private final EmployeeTagService tagService;

    /**
     * 添加标签
     *
     * @param dto 标签创建参数
     * @return 统一响应结果，包含新建标签 ID
     */
    @Operation(summary = "添加标签")
    @PrePermission(PermissionCodes.RESOURCE_TAG_CREATE)
    @OperationLog(module = "人员标签", action = "添加标签", bizType = "EMPLOYEE_TAG")
    @Idempotent(key = "employeeTag:add", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<String> add(@Valid @RequestBody EmployeeTagCreateDTO dto) {
        return Result.ok(tagService.add(dto));
    }

    /**
     * 删除标签
     *
     * @param id 标签 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除标签")
    @PrePermission(PermissionCodes.RESOURCE_TAG_DELETE)
    @OperationLog(module = "人员标签", action = "删除标签", bizType = "EMPLOYEE_TAG")
    @Idempotent(key = "employeeTag:remove", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable String id) {
        tagService.remove(id);
        return Result.ok();
    }

    /**
     * 覆盖式设置员工标签
     *
     * @param employeeId 员工 ID
     * @param tags       标签列表
     * @return 统一响应结果
     */
    @Operation(summary = "覆盖式设置员工标签")
    @PrePermission(PermissionCodes.RESOURCE_TAG_UPDATE)
    @OperationLog(module = "人员标签", action = "覆盖员工标签", bizType = "EMPLOYEE_TAG")
    @Idempotent(key = "employeeTag:replaceByEmployee", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/replace/{employeeId}")
    public Result<Void> replaceByEmployee(@PathVariable String employeeId,
                                     @Valid @RequestBody List<EmployeeTagCreateDTO> tags) {
        tagService.replaceByEmployee(employeeId, tags);
        return Result.ok();
    }

    /**
     * 按员工查询标签列表
     *
     * @param employeeId 员工 ID
     * @return 统一响应结果，包含标签列表
     */
    @Operation(summary = "按员工查询")
    @GetMapping("/byEmployee/{employeeId}")
    public Result<List<EmployeeTagDO>> listByEmployee(@PathVariable String employeeId) {
        return Result.ok(tagService.listByEmployee(employeeId));
    }

    /**
     * 按标签筛选候选人
     *
     * @param tagType 标签类型
     * @param tagCode 标签编码（可选）
     * @return 统一响应结果，包含候选人标签列表
     */
    @Operation(summary = "按标签筛选候选人")
    @GetMapping("/candidates")
    public Result<List<EmployeeTagDO>> candidates(@RequestParam String tagType,
                                             @RequestParam(required = false) String tagCode) {
        return Result.ok(tagService.findCandidates(tagType, tagCode));
    }
}
