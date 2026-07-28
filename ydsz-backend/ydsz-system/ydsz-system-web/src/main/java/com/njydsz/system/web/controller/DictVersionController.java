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
 * 字典版本 Controller
 *
 * <p>提供字典变更历史查询能力。字典版本是字典变更审计与回滚能力的数据基础：
 * 每次字典项发生变更（save / updateById / removeById）时，{@link com.njydsz.system.server.service.DictItemServiceImpl}
 * 会自动创建一条版本快照（含变更时间、操作说明、变更前字典全量 JSON 快照）。
 *
 * <p><b>接口路径：</b>{@code /api/v1/dict/version}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>变更历史查询</b>：按 typeCode 查询该类型下所有版本快照，按 version 倒序</li>
 *   <li><b>回滚审计</b>：运维/审计场景定位「字典项为什么突然变了」</li>
 *   <li><b>合规留存</b>：字典变更符合「变更留痕」合规要求</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>前端展示「字典变更时间线」</li>
 *   <li>审计日志中按 typeCode + 时间窗检索变更</li>
 *   <li>未来支持「一键回滚到指定版本」（依赖 {@code DictVersionService.rollbackTo}）</li>
 * </ul>
 *
 * <p><b>注意：</b>本接口为查询专用，回滚操作应在业务侧通过
 * {@code DictVersionService.rollbackTo(typeCode, version)} 显式调用，避免误操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.server.service.DictVersionService 字典版本业务逻辑
 * @see com.njydsz.system.domain.entity.DictVersion 字典版本实体
 */
@Tag(name = "字典版本", description = "字典变更历史查询（每条版本含全量 JSON 快照，支持回滚）")
@RestController
@RequestMapping("/api/v1/dict/version")
@RequiredArgsConstructor
public class DictVersionController {

    private final DictVersionService service;

    /**
     * 按字典类型编码查询版本历史。
     *
     * <p>返回该 typeCode 下所有版本快照，按 version 倒序（最新版本在前）。
     * 每条记录含变更时间、操作人、操作说明、变更前全量 JSON 快照。
     *
     * @param typeCode 字典类型编码（如 {@code "order_status"}）
     * @return 版本历史列表（可能为空）
     */
    @Operation(summary = "按类型编码查询版本历史")
    @GetMapping("/{typeCode}")
    public BaseResponse<List<DictVersionVO>> listByTypeCode(@PathVariable String typeCode) {
        return BaseResponse.success(service.listByTypeCode(typeCode));
    }
}
