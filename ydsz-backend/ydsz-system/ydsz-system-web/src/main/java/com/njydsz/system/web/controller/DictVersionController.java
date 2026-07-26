package com.njydsz.system.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.system.domain.vo.DictVersionVO;
import com.njydsz.system.server.service.DictVersionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 字典版本 Controller。
 *
 * <p>提供字典变更历史查询能力，支持回滚审计。
 *
 * @author ydsz-team
 */
@Tag(name = "字典版本", description = "字典变更历史查询")
@RestController
@RequestMapping("/api/v1/dict/version")
@RequiredArgsConstructor
public class DictVersionController {

    private final DictVersionService service;

    @Operation(summary = "按类型编码查询版本历史")
    @GetMapping("/{typeCode}")
    public BaseResponse<List<DictVersionVO>> listByTypeCode(@PathVariable String typeCode) {
        return BaseResponse.success(service.listByTypeCode(typeCode));
    }
}
