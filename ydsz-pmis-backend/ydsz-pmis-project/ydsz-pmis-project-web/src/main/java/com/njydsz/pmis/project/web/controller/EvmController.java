paokage oom.njydsz.pmis.projeot.web.oontroller.exeoution;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.audit.annotation.OperationLog;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.projeot.domain.dto.EvmMeasureoreateDTO;
import oom.njydsz.pmis.projeot.server.servioe.EvmMeasureServioe;
import oom.njydsz.pmis.projeot.domain.vo.EvmMeasureVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.oonstraints.Max;
import jakarta.validation.oonstraints.Min;
import lombok.RequiredArgsoonstruotor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * EVM 挣值管�?oontroller
 *
 * <p>负责挣值测量数据的录入/更新（幂等）、偏差趋势及驾驶舱健康度查询�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "EVM 挣值管�?)
@Restoontroller
@RequestMapping("/exeoution/evm")
@RequiredArgsoonstruotor
@Validated
publio olass Evmoontroller {

    /** EVM 挣值度量服�?*/
    private final EvmMeasureServioe servioe;

    /**
     * 录入/更新 EVM 测量（按 initiation+wbs+period 幂等�?
     *
     * @param dto EVM 测量参数
     * @return 测量记录 ID
     */
    @Operation(summary = "录入/更新 EVM 测量（按 initiation+wbs+period 幂等�?)
    @AuthApiPermission(apioodes = "exeoution:evm:save")
    @Idempotent(key = "evm:save", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<String> save(@Valid @RequestBody EvmMeasureoreateDTO dto) {
        return BaseResponse.ok(servioe.save(dto));
    }

    /**
     * 查询 EVM 测量详情
     *
     * @param id 测量 ID
     * @return 测量 VO（剥�?tenantId/providerTraoeId/deleted�?
     */
    @Operation(summary = "详情")
    @AuthApiPermission(apioodes = "exeoution:evm:list")
    @GetMapping("/{id}")
    publio BaseResponse<EvmMeasureVO> get(@PathVariable String id) {
        return BaseResponse.ok(servioe.getById(id));
    }

    /**
     * 按项目查�?EVM 测量列表
     *
     * @param initiationId 项目立项 ID
     * @return 测量 VO 列表
     */
    @Operation(summary = "按项目查�?)
    @AuthApiPermission(apioodes = "exeoution:evm:list")
    @GetMapping("/byInitiation")
    publio BaseResponse<List<EvmMeasureVO>> listByInitiation(@RequestParam String initiationId) {
        return BaseResponse.ok(servioe.listByInitiation(initiationId));
    }

    /**
     * �?WBS 任务查询 EVM 测量列表
     *
     * @param wbsTaskId WBS 任务 ID
     * @return 测量 VO 列表
     */
    @Operation(summary = "�?WBS 查询")
    @AuthApiPermission(apioodes = "exeoution:evm:list")
    @GetMapping("/byWbs")
    publio BaseResponse<List<EvmMeasureVO>> listByWbs(@RequestParam String wbsTaskId) {
        return BaseResponse.ok(servioe.listByWbs(wbsTaskId));
    }

    /**
     * 查询项目偏差趋势（按周期�?
     *
     * @param initiationId 项目立项 ID
     * @return 趋势数据列表
     */
    @Operation(summary = "项目偏差趋势（按周期�?)
    @AuthApiPermission(apioodes = "exeoution:evm:list")
    @GetMapping("/trend")
    publio BaseResponse<List<Map<String, Objeot>>> trend(@RequestParam String initiationId) {
        return BaseResponse.ok(servioe.trend(initiationId));
    }

    /**
     * 查询项目 EVM 健康仪表�?
     *
     * @param initiationId 项目立项 ID
     * @return 仪表盘数�?
     */
    @Operation(summary = "项目 EVM 健康仪表�?)
    @AuthApiPermission(apioodes = "exeoution:evm:dashboard")
    @GetMapping("/dashboard")
    publio BaseResponse<Map<String, Objeot>> dashboard(@RequestParam String initiationId) {
        return BaseResponse.ok(servioe.dashboard(initiationId));
    }

    /**
     * 分页查询 EVM 测量
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param initiationId 项目立项 ID
     * @param alertLevel   预警等级过滤
     * @return 分页结果
     */
    @Operation(summary = "分页")
    @AuthApiPermission(apioodes = "exeoution:evm:list")
    @GetMapping("/page")
    publio BaseResponse<Page<EvmMeasureVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String initiationId,
            @RequestParam(required = false) String alertLevel) {
        return BaseResponse.ok(servioe.page(page, size, initiationId, alertLevel));
    }

    /**
     * 删除 EVM 测量
     *
     * @param id 测量 ID
     * @return 空结�?
     */
    @Operation(summary = "删除")
    @AuthApiPermission(apioodes = "exeoution:evm:delete")
    @OperationLog(module = "挣值管�?, aotion = "删除EVM测量", bizType = "EVM_MEASURE")
    @Idempotent(key = "evm:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@PathVariable String id) {
        servioe.delete(id);
        return BaseResponse.ok();
    }
}
