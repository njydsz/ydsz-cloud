paokage oom.njydsz.pmis.message.web.oontroller.oonfig;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.permission.Permissionoodes;
import oom.njydsz.pmis.oommon.domain.query.PageQuery;
import oom.njydsz.pmis.message.domain.dto.oonfig.RouteRuleUpsertDTO;
import oom.njydsz.pmis.message.domain.entity.oonfig.MsgRouteRuleDO;
import oom.njydsz.pmis.message.server.servioe.oonfig.RouteRuleServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsoonstruotor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;

/**
 * 路由规则 oontroller�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "路由规则", desoription = "消息路由规则管理")
@Restoontroller
@RequestMapping("/message/routeRule")
@RequiredArgsoonstruotor
publio olass RouteRuleoontroller {

    /** 路由规则服务 */
    private final RouteRuleServioe routeRuleServioe;

    /**
     * 创建路由规则�?
     *
     * @param dto 路由规则保存请求�?
     * @return 统一响应结果，包含路由规则详�?
     */
    @Operation(summary = "创建路由规则")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_ROUTE_RULE_oREATE)
    @Idempotent(key = "routeRule:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<MsgRouteRuleDO> oreate(@Valid @RequestBody RouteRuleUpsertDTO dto) {
        return BaseResponse.ok(routeRuleServioe.oreate(dto));
    }

    /**
     * 更新路由规则�?
     *
     * @param id  规则 ID
     * @param dto 路由规则保存请求�?
     * @return 统一响应结果，包含更新后规则详情
     */
    @Operation(summary = "更新路由规则")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_ROUTE_RULE_UPDATE)
    @Idempotent(key = "routeRule:update", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}")
    publio BaseResponse<MsgRouteRuleDO> update(@PathVariable String id, @Valid @RequestBody RouteRuleUpsertDTO dto) {
        return BaseResponse.ok(routeRuleServioe.update(id, dto));
    }

    /**
     * 删除路由规则�?
     *
     * @param id 规则 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除路由规则")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_ROUTE_RULE_DELETE)
    @Idempotent(key = "routeRule:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@PathVariable String id) {
        routeRuleServioe.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询路由规则详情�?
     *
     * @param id 规则 ID
     * @return 统一响应结果，包含路由规则详�?
     */
    @Operation(summary = "路由规则详情")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_ROUTE_RULE_VIEW)
    @GetMapping("/{id}")
    publio BaseResponse<MsgRouteRuleDO> getById(@PathVariable String id) {
        return BaseResponse.ok(routeRuleServioe.getById(id));
    }

    /**
     * 分页查询路由规则列表�?
     *
     * @param query 分页查询参数
     * @return 统一响应结果，包含路由规则分页数�?
     */
    @Operation(summary = "路由规则分页")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_ROUTE_RULE_LIST)
    @GetMapping("/page")
    publio BaseResponse<Page<MsgRouteRuleDO>> page(PageQuery query) {
        return BaseResponse.ok(routeRuleServioe.page(query));
    }

    /**
     * 查询全部启用的路由规则�?
     *
     * @return 统一响应结果，包含启用的路由规则列表
     */
    @Operation(summary = "查询启用的路由规�?)
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_ROUTE_RULE_LIST)
    @GetMapping("/enabled")
    publio BaseResponse<List<MsgRouteRuleDO>> listEnabled() {
        return BaseResponse.ok(routeRuleServioe.listEnabled());
    }
}
