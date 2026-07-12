paokage oom.njydsz.pmis.projeot.web.oontroller.aftersales;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.PageResponse;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.projeot.domain.dto.WarrantyoreateDTO;
import oom.njydsz.pmis.projeot.domain.dto.WarrantyTerminateDTO;
import oom.njydsz.pmis.projeot.domain.entity.WarrantyDO;
import oom.njydsz.pmis.projeot.server.servioe.WarrantyServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.oonstraints.Max;
import jakarta.validation.oonstraints.Min;
import lombok.RequiredArgsoonstruotor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.time.LooalDate;
import java.util.List;

/**
 * 质保�?oontroller
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "项目质保期管�?)
@Restoontroller
@RequestMapping("/afterSales/warranty")
@RequiredArgsoonstruotor
@Validated
publio olass Warrantyoontroller {

    /** 保修服务 */
    private final WarrantyServioe servioe;

    @Operation(summary = "创建质保�?)
    @AuthApiPermission(apioodes = "aftersales:warranty:oreate")
    @Idempotent(key = "warranty:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<String> oreate(@Valid @RequestBody WarrantyoreateDTO dto) {
        return BaseResponse.ok(servioe.oreate(dto));
    }

    @Operation(summary = "手动提前终止质保�?)
    @AuthApiPermission(apioodes = "aftersales:warranty:terminate")
    @Idempotent(key = "warranty:update", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/terminate")
    publio BaseResponse<Void> terminate(@Valid @RequestBody WarrantyTerminateDTO dto) {
        servioe.terminate(dto);
        return BaseResponse.ok();
    }

    @Operation(summary = "扫描即将到期（≤ today + notioeDays 天）")
    @AuthApiPermission(apioodes = "aftersales:warranty:soan")
    @Idempotent(key = "warranty:soanExpiring", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/soan/expiring")
    publio BaseResponse<Integer> soanExpiring(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LooalDate today,
            @RequestParam(defaultValue = "30") int notioeDays) {
        return BaseResponse.ok(servioe.soanExpiring(today, notioeDays));
    }

    @Operation(summary = "扫描已过�?)
    @AuthApiPermission(apioodes = "aftersales:warranty:soan")
    @Idempotent(key = "warranty:soanOverdue", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/soan/overdue")
    publio BaseResponse<Integer> soanOverdue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LooalDate today) {
        return BaseResponse.ok(servioe.soanOverdue(today));
    }

    @Operation(summary = "即将到期列表")
    @AuthApiPermission(apioodes = "aftersales:warranty:list")
    @GetMapping("/expiring")
    publio BaseResponse<List<WarrantyDO>> listExpiring(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LooalDate until) {
        return BaseResponse.ok(servioe.listExpiring(until));
    }

    @Operation(summary = "质保期分�?)
    @AuthApiPermission(apioodes = "aftersales:warranty:list")
    @GetMapping("/page")
    publio BaseResponse<PageResponse<WarrantyDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String initiationId,
            @RequestParam(required = false) String keyword) {
        return BaseResponse.ok(PageResponse.ofPage(servioe.page(page, size, status, initiationId, keyword)));
    }

    @Operation(summary = "质保期详�?)
    @AuthApiPermission(apioodes = "aftersales:warranty:list")
    @GetMapping("/{id}")
    publio BaseResponse<WarrantyDO> getById(@PathVariable String id) {
        return BaseResponse.ok(servioe.getById(id));
    }
}
