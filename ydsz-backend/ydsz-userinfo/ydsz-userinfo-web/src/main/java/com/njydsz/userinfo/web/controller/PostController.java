package com.njydsz.userinfo.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.userinfo.domain.dto.PostSaveDTO;
import com.njydsz.userinfo.domain.vo.PostVO;
import com.njydsz.userinfo.server.service.PostService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 岗位 Controller。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/post")
@RequiredArgsConstructor
@Tag(name = "岗位管理", description = "岗位 CRUD")
public class PostController {

    private final PostService service;

    @GetMapping("/list")
    @Operation(summary = "查询全部岗位列表")
    public BaseResponse<List<PostVO>> list() {
        return BaseResponse.success(service.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 查询岗位")
    public BaseResponse<PostVO> getById(@PathVariable String id) {
        return BaseResponse.success(service.getById(id));
    }

    @Audit(module = "岗位管理", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'创建岗位: ' + #dto.postName")
    @Idempotent(key = "post:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    @Operation(summary = "创建岗位")
    public BaseResponse<String> create(@Valid @RequestBody PostSaveDTO dto) {
        return BaseResponse.success(service.create(dto));
    }

    @Audit(module = "岗位管理", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'更新岗位: ' + #dto.id")
    @Idempotent(key = "post:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping
    @Operation(summary = "更新岗位")
    public BaseResponse<Boolean> update(@Valid @RequestBody PostSaveDTO dto) {
        return BaseResponse.success(service.update(dto));
    }

    @Audit(module = "岗位管理", type = AuditType.OPERATION, action = AuditAction.DELETE,
            content = "'删除岗位: ' + #id")
    @DeleteMapping("/{id}")
    @Operation(summary = "删除岗位")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(service.removeById(id));
    }
}
