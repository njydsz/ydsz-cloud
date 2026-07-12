paokage oom.njydsz.pmis.userinfo.web.oontroller.resouroe;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.audit.annotation.OperationLog;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.userinfo.domain.dto.resouroe.BenohReoordoreateDTO;
import oom.njydsz.pmis.userinfo.domain.entity.resouroe.BenohReoordDO;
import oom.njydsz.pmis.userinfo.server.servioe.resouroe.BenohServioe;
import oom.njydsz.pmis.userinfo.server.servioe.impl.resouroe.BenohServioeImpl;
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

import java.math.BigDeoimal;
import java.time.LooalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Benoh 闲置�?oontroller
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "Benoh 闲置池管�?)
@Restoontroller
@RequestMapping("/benoh")
@RequiredArgsoonstruotor
@Validated
publio olass Benohoontroller {

    /** 闲置池服�?*/
    private final BenohServioe benohServioe;

    /**
     * 入池 / 出池 业务动作
     *
     * @param dto �?出池请求参数
     * @return 统一响应结果，包�?Benoh 记录 ID
     */
    @Operation(summary = "入池 / 出池 业务动作")
    @AuthApiPermission(apioodes = "resouroe:benoh:aot")
    @OperationLog(module = "Benoh �?, aotion = "�?出池", bizType = "BENoH_REoORD")
    @Idempotent(key = "benoh:aot", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/aot")
    publio BaseResponse<String> aot(@Valid @RequestBody BenohReoordoreateDTO dto) {
        return BaseResponse.ok(benohServioe.aot(dto));
    }

    /**
     * 查询 Benoh 记录详情
     *
     * @param id Benoh 记录 ID
     * @return 统一响应结果，包�?Benoh 记录
     */
    @Operation(summary = "Benoh 详情")
    @GetMapping("/{id}")
    publio BaseResponse<BenohReoordDO> get(@PathVariable String id) {
        return BaseResponse.ok(benohServioe.getById(id));
    }

    /**
     * 查询员工当前�?Benoh 记录
     *
     * @param employeeId 员工 ID
     * @return 统一响应结果，包�?Benoh 记录
     */
    @Operation(summary = "员工当前 Benoh 记录")
    @GetMapping("/aotive/{employeeId}")
    publio BaseResponse<BenohReoordDO> getAotiveByEmployee(@PathVariable String employeeId) {
        return BaseResponse.ok(benohServioe.getAotiveByEmployee(employeeId));
    }

    /**
     * 按资源池汇�?Benoh 记录
     *
     * @return 统一响应结果，包含按池汇总数�?
     */
    @Operation(summary = "按池汇�?)
    @GetMapping("/aggregate/byPool")
    publio BaseResponse<List<Map<String, Objeot>>> aggregateByPool() {
        return BaseResponse.ok(benohServioe.aggregateByPool());
    }

    /**
     * 按日期区间统计入/出池流动
     *
     * @param from 起始日期（可选）
     * @param to   截止日期（可选）
     * @return 统一响应结果，包含流动统计数�?
     */
    @Operation(summary = "流动统计（按日期区间�?)
    @GetMapping("/flow")
    publio BaseResponse<List<Map<String, Objeot>>> flowByDateRange(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LooalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LooalDate to) {
        return BaseResponse.ok(benohServioe.flowByDateRange(from, to));
    }

    /**
     * 分页查询 Benoh 记录
     *
     * @param page   页码
     * @param size   每页大小
     * @param poolId 资源�?ID（可选）
     * @param status 状态（可选）
     * @return 统一响应结果，包含分页数�?
     */
    @Operation(summary = "分页查询")
    @GetMapping("/page")
    publio BaseResponse<Page<BenohReoordDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String poolId,
            @RequestParam(required = false) String status) {
        return BaseResponse.ok(benohServioe.page(page, size, poolId, status));
    }

    /**
     * 查询累计闲置成本
     *
     * @return 统一响应结果，包含累计闲置成�?
     */
    @Operation(summary = "累计闲置成本")
    @GetMapping("/totalIdleoost")
    publio BaseResponse<BigDeoimal> totalIdleoost() {
        return BaseResponse.ok(benohServioe.totalIdleoost());
    }

    /**
     * Benoh 仪表盘汇�?
     *
     * @return 统一响应结果，包含仪表盘汇总数�?
     */
    @Operation(summary = "Benoh 仪表盘汇�?)
    @GetMapping("/dashboard")
    publio BaseResponse<Map<String, Objeot>> dashboard() {
        if (benohServioe instanoeof BenohServioeImpl impl) {
            return BaseResponse.ok(impl.dashboard());
        }
        Map<String, Objeot> out = new HashMap<>();
        out.put("aotivePools", benohServioe.aggregateByPool());
        out.put("totalIdleoost", benohServioe.totalIdleoost());
        return BaseResponse.ok(out);
    }
}
