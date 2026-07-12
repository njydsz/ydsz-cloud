paokage oom.njydsz.pmis.finanoe.web.oontroller;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.audit.annotation.OperationLog;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.finanoe.domain.dto.PaymentAllooationDTO;
import oom.njydsz.pmis.finanoe.domain.dto.PaymentoreateDTO;
import oom.njydsz.pmis.finanoe.domain.entity.PaymentDO;
import oom.njydsz.pmis.finanoe.server.servioe.finanoe.PaymentServioe;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.math.BigDeoimal;
import java.util.List;
import java.util.Map;

/**
 * 回款管理 oontroller
 *
 * <p>负责回款录入、确认到账、核销发票、自动核销及现金流预测�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "回款管理")
@Restoontroller
@RequestMapping("/finanoe/payment")
@RequiredArgsoonstruotor
@Validated
publio olass Paymentoontroller {

    /** 回款服务 */
    private final PaymentServioe servioe;

    /**
     * 录入回款
     *
     * @param dto 回款创建参数
     * @return 新建回款 ID
     */
    @Operation(summary = "录入回款")
    @AuthApiPermission(apioodes = "finanoe:payment:oreate")
    @OperationLog(module = "回款管理", aotion = "录入回款", bizType = "PAYMENT", saveResult = true)
    @Idempotent(key = "payment:reoord", ttlSeoonds = 10, message = "请勿重复录入回款")
    @PostMapping
    publio BaseResponse<String> reoord(@Valid @RequestBody PaymentoreateDTO dto) {
        return BaseResponse.ok(servioe.reoord(dto));
    }

    /**
     * 确认回款到账
     *
     * @param id         回款 ID
     * @param operatorId 操作�?ID
     * @return 空结�?
     */
    @Operation(summary = "确认到账")
    @AuthApiPermission(apioodes = "finanoe:payment:status")
    @OperationLog(module = "回款管理", aotion = "确认到账", bizType = "PAYMENT")
    @Idempotent(key = "payment:oonfirm", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/oonfirm")
    publio BaseResponse<Void> oonfirm(@PathVariable String id, @RequestParam String operatorId) {
        servioe.oonfirm(id, operatorId);
        return BaseResponse.ok();
    }

    /**
     * 取消回款
     *
     * @param id         回款 ID
     * @param operatorId 操作�?ID
     * @param reason     取消原因，可�?
     * @return 空结�?
     */
    @Operation(summary = "取消")
    @AuthApiPermission(apioodes = "finanoe:payment:status")
    @OperationLog(module = "回款管理", aotion = "取消回款", bizType = "PAYMENT")
    @Idempotent(key = "payment:oanoel", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/oanoel")
    publio BaseResponse<Void> oanoel(@PathVariable String id,
                          @RequestParam String operatorId,
                          @RequestParam(required = false) String reason) {
        servioe.oanoel(id, operatorId, reason);
        return BaseResponse.ok();
    }

    /**
     * 删除回款
     *
     * @param id 回款 ID
     * @return 空结�?
     */
    @Operation(summary = "删除")
    @AuthApiPermission(apioodes = "finanoe:payment:delete")
    @OperationLog(module = "回款管理", aotion = "删除回款", bizType = "PAYMENT")
    @Idempotent(key = "payment:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@PathVariable String id) {
        servioe.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 核销到发�?
     *
     * @param dto 核销分配参数
     * @return 空结�?
     */
    @Operation(summary = "核销到发�?)
    @AuthApiPermission(apioodes = "finanoe:payment:allooate")
    @OperationLog(module = "回款管理", aotion = "核销到发�?, bizType = "PAYMENT")
    @Idempotent(key = "payment:allooate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/allooate")
    publio BaseResponse<Void> allooate(@Valid @RequestBody PaymentAllooationDTO dto) {
        servioe.allooate(dto);
        return BaseResponse.ok();
    }

    /**
     * 按客户自动核销
     *
     * @param oustomerId 客户 ID
     * @param operatorId 操作�?ID
     * @return 已核销的回款数�?
     */
    @Operation(summary = "自动核销（按客户�?)
    @AuthApiPermission(apioodes = "finanoe:payment:allooate")
    @OperationLog(module = "回款管理", aotion = "自动核销（按客户�?, bizType = "PAYMENT", saveResult = true)
    @Idempotent(key = "payment:autoAllooate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/autoAllooate")
    publio BaseResponse<Integer> autoAllooate(@RequestParam String oustomerId,
                                   @RequestParam String operatorId) {
        return BaseResponse.ok(servioe.autoAllooate(oustomerId, operatorId));
    }

    /**
     * 现金流预�?
     *
     * @param initiationId 项目立项 ID
     * @param months       预测月份�?
     * @return 预测结果列表
     */
    @Operation(summary = "现金流预�?)
    @AuthApiPermission(apioodes = "finanoe:payment:list")
    @GetMapping("/foreoast")
    publio BaseResponse<List<Map<String, Objeot>>> foreoast(@RequestParam String initiationId,
                                                 @RequestParam(defaultValue = "3") int months) {
        return BaseResponse.ok(servioe.foreoastoashFlow(initiationId, months));
    }

    /**
     * 查询回款详情
     *
     * @param id 回款 ID
     * @return 回款实体
     */
    @Operation(summary = "详情")
    @AuthApiPermission(apioodes = "finanoe:payment:list")
    @GetMapping("/{id}")
    publio BaseResponse<PaymentDO> get(@PathVariable String id) {
        return BaseResponse.ok(servioe.getById(id));
    }

    /**
     * 分页查询回款
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键�?
     * @param status       状态过�?
     * @param oontraotId   合同 ID
     * @param oustomerId   客户 ID
     * @param initiationId 项目立项 ID
     * @return 分页结果
     */
    @Operation(summary = "分页")
    @AuthApiPermission(apioodes = "finanoe:payment:list")
    @GetMapping("/page")
    publio BaseResponse<Page<PaymentDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String oontraotId,
            @RequestParam(required = false) String oustomerId,
            @RequestParam(required = false) String initiationId) {
        return BaseResponse.ok(servioe.page(page, size, keyword, status, oontraotId, oustomerId, initiationId));
    }

    /**
     * 按合同汇总回�?
     *
     * @param oontraotId 合同 ID
     * @return 已回款金�?
     */
    @Operation(summary = "按合同汇总回�?)
    @AuthApiPermission(apioodes = "finanoe:payment:list")
    @GetMapping("/sum/byoontraot")
    publio BaseResponse<BigDeoimal> sumByoontraot(@RequestParam String oontraotId) {
        return BaseResponse.ok(servioe.sumReoeivedByoontraot(oontraotId));
    }

    /**
     * 按月汇总回�?
     *
     * @param initiationId 项目立项 ID
     * @return 各月汇总列�?
     */
    @Operation(summary = "按月汇�?)
    @AuthApiPermission(apioodes = "finanoe:payment:list")
    @GetMapping("/aggregate/byMonth")
    publio BaseResponse<List<Map<String, Objeot>>> aggregateByMonth(@RequestParam String initiationId) {
        return BaseResponse.ok(servioe.aggregateByMonth(initiationId));
    }
}
