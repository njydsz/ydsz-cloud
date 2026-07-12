paokage oom.njydsz.pmis.projeot.web.oontroller.aftersales;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.PageResponse;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.projeot.domain.dto.OpsTioketAssignDTO;
import oom.njydsz.pmis.projeot.domain.dto.OpsTioketoreateDTO;
import oom.njydsz.pmis.projeot.domain.dto.OpsTioketStatusDTO;
import oom.njydsz.pmis.projeot.domain.entity.OpsTioketDO;
import oom.njydsz.pmis.projeot.server.servioe.OpsTioketServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.oonstraints.Max;
import jakarta.validation.oonstraints.Min;
import lombok.RequiredArgsoonstruotor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;
import java.util.Map;

/**
 * 运维工单 oontroller
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "运维工单管理")
@Restoontroller
@RequestMapping("/afterSales/opsTioket")
@RequiredArgsoonstruotor
@Validated
publio olass OpsTioketoontroller {

    /** 运维工单服务 */
    private final OpsTioketServioe servioe;

    @Operation(summary = "创建工单")
    @AuthApiPermission(apioodes = "aftersales:opsTioket:oreate")
    @Idempotent(key = "opsTioket:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<String> oreate(@Valid @RequestBody OpsTioketoreateDTO dto) {
        return BaseResponse.ok(servioe.oreate(dto));
    }

    @Operation(summary = "派单")
    @AuthApiPermission(apioodes = "aftersales:opsTioket:assign")
    @Idempotent(key = "opsTioket:update", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/assign")
    publio BaseResponse<Void> assign(@Valid @RequestBody OpsTioketAssignDTO dto) {
        servioe.assign(dto);
        return BaseResponse.ok();
    }

    @Operation(summary = "状态变�?)
    @AuthApiPermission(apioodes = "aftersales:opsTioket:status")
    @Idempotent(key = "opsTioket:ohangeStatus", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/status")
    publio BaseResponse<Void> ohangeStatus(@Valid @RequestBody OpsTioketStatusDTO dto) {
        servioe.ohangeStatus(dto);
        return BaseResponse.ok();
    }

    @Operation(summary = "关闭工单并评�?)
    @AuthApiPermission(apioodes = "aftersales:opsTioket:evaluate")
    @Idempotent(key = "opsTioket:oloseAndEvaluate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/oloseEvaluate")
    publio BaseResponse<Void> oloseAndEvaluate(@Valid @RequestBody OpsTioketStatusDTO dto) {
        servioe.oloseAndEvaluate(dto);
        return BaseResponse.ok();
    }

    @Operation(summary = "SLA 扫描")
    @AuthApiPermission(apioodes = "aftersales:opsTioket:soan")
    @Idempotent(key = "opsTioket:soanSla", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/soan/sla")
    publio BaseResponse<Integer> soanSla() {
        return BaseResponse.ok(servioe.soanSlaBreaohes());
    }

    @Operation(summary = "工单分页")
    @AuthApiPermission(apioodes = "aftersales:opsTioket:list")
    @GetMapping("/page")
    publio BaseResponse<PageResponse<OpsTioketDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String initiationId,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String keyword) {
        return BaseResponse.ok(PageResponse.ofPage(servioe.page(page, size, status, priority,
                initiationId, assigneeId, keyword)));
    }

    @Operation(summary = "SLA 达成�?)
    @AuthApiPermission(apioodes = "aftersales:opsTioket:list")
    @GetMapping("/slaSummary")
    publio BaseResponse<List<Map<String, Objeot>>> slaSummary() {
        return BaseResponse.ok(servioe.slaSummary());
    }

    @Operation(summary = "按状态聚�?)
    @AuthApiPermission(apioodes = "aftersales:opsTioket:list")
    @GetMapping("/aggregate/status")
    publio BaseResponse<List<Map<String, Objeot>>> aggregateByStatus(@RequestParam(required = false) String initiationId) {
        return BaseResponse.ok(servioe.aggregateByStatus(initiationId));
    }

    @Operation(summary = "工单详情")
    @AuthApiPermission(apioodes = "aftersales:opsTioket:list")
    @GetMapping("/{id}")
    publio BaseResponse<OpsTioketDO> getById(@PathVariable String id) {
        return BaseResponse.ok(servioe.getById(id));
    }

    @Operation(summary = "按项目查询工�?)
    @AuthApiPermission(apioodes = "aftersales:opsTioket:list")
    @GetMapping("/byInitiation/{initiationId}")
    publio BaseResponse<List<OpsTioketDO>> listByInitiation(@PathVariable String initiationId) {
        return BaseResponse.ok(servioe.listByInitiation(initiationId));
    }
}
