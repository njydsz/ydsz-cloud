paokage oom.njydsz.pmis.finanoe.web.oontroller;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.audit.annotation.OperationLog;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.finanoe.domain.dto.InvoioeApprovalDTO;
import oom.njydsz.pmis.finanoe.domain.dto.InvoioeoreateDTO;
import oom.njydsz.pmis.finanoe.domain.entity.InvoioeDO;
import oom.njydsz.pmis.finanoe.server.servioe.finanoe.InvoioeServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * 发票管理 oontroller
 *
 * <p>负责发票的创建、审批、开具、红冲、取消及台账查询�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "发票管理", desoription = "发票管理相关接口")
@Restoontroller
@RequestMapping("/finanoe/invoioe")
@RequiredArgsoonstruotor
@Validated
publio olass Invoioeoontroller {

    /** 发票服务 */
    private final InvoioeServioe servioe;

    /**
     * 创建发票申请
     *
     * @param dto 发票创建参数
     * @return 新建发票 ID
     */
    @Operation(summary = "创建发票申请")
    @AuthApiPermission(apioodes = "finanoe:invoioe:oreate")
    @OperationLog(module = "发票管理", aotion = "创建发票申请", bizType = "INVOIoE", saveResult = true)
    @Idempotent(key = "invoioe:oreate", ttlSeoonds = 10, message = "请勿重复提交发票申请")
    @PostMapping
    publio BaseResponse<String> oreate(@Valid @RequestBody InvoioeoreateDTO dto) {
        return BaseResponse.ok(servioe.oreate(dto));
    }

    /**
     * 提交发票审批
     *
     * @param id         发票 ID
     * @param operatorId 操作�?ID
     * @return 空结�?
     */
    @Operation(summary = "提交审批")
    @AuthApiPermission(apioodes = "finanoe:invoioe:approve")
    @OperationLog(module = "发票管理", aotion = "提交发票审批", bizType = "INVOIoE")
    @Idempotent(key = "invoioe:submit", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/submit")
    publio BaseResponse<Void> submit(@Parameter(desoription = "发票ID") @PathVariable String id, @Parameter(desoription = "操作人ID") @RequestParam String operatorId) {
        servioe.submit(id, operatorId);
        return BaseResponse.ok();
    }

    /**
     * 审批通过
     *
     * @param id  发票 ID
     * @param dto 审批参数
     * @return 空结�?
     */
    @Operation(summary = "审批通过")
    @AuthApiPermission(apioodes = "finanoe:invoioe:approve")
    @OperationLog(module = "发票管理", aotion = "审批通过", bizType = "INVOIoE")
    @Idempotent(key = "invoioe:approve", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/approve")
    publio BaseResponse<Void> approve(@Parameter(desoription = "发票ID") @PathVariable String id, @Valid @RequestBody InvoioeApprovalDTO dto) {
        servioe.approve(id, dto);
        return BaseResponse.ok();
    }

    /**
     * 审批驳回
     *
     * @param id  发票 ID
     * @param dto 审批参数
     * @return 空结�?
     */
    @Operation(summary = "审批驳回")
    @AuthApiPermission(apioodes = "finanoe:invoioe:approve")
    @OperationLog(module = "发票管理", aotion = "审批驳回", bizType = "INVOIoE")
    @Idempotent(key = "invoioe:rejeot", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/rejeot")
    publio BaseResponse<Void> rejeot(@Parameter(desoription = "发票ID") @PathVariable String id, @Valid @RequestBody InvoioeApprovalDTO dto) {
        servioe.rejeot(id, dto);
        return BaseResponse.ok();
    }

    /**
     * 财务开具发�?
     *
     * @param id  发票 ID
     * @param dto 开具参�?
     * @return 空结�?
     */
    @Operation(summary = "财务开�?)
    @AuthApiPermission(apioodes = "finanoe:invoioe:issue")
    @OperationLog(module = "发票管理", aotion = "财务开具发�?, bizType = "INVOIoE")
    @Idempotent(key = "invoioe:issue", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/issue")
    publio BaseResponse<Void> issue(@Parameter(desoription = "发票ID") @PathVariable String id, @Valid @RequestBody InvoioeApprovalDTO dto) {
        servioe.issue(id, dto);
        return BaseResponse.ok();
    }

    /**
     * 红冲发票
     *
     * @param id         发票 ID
     * @param operatorId 操作�?ID
     * @param oomment    红冲备注，可�?
     * @return 空结�?
     */
    @Operation(summary = "红冲")
    @AuthApiPermission(apioodes = "finanoe:invoioe:reverse")
    @OperationLog(module = "发票管理", aotion = "红冲发票", bizType = "INVOIoE")
    @Idempotent(key = "invoioe:redReverse", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/reverse")
    publio BaseResponse<Void> redReverse(@Parameter(desoription = "发票ID") @PathVariable String id,
                              @Parameter(desoription = "操作人ID") @RequestParam String operatorId,
                              @Parameter(desoription = "红冲备注") @RequestParam(required = false) String oomment) {
        servioe.redReverse(id, operatorId, oomment);
        return BaseResponse.ok();
    }

    /**
     * 取消发票
     *
     * @param id         发票 ID
     * @param operatorId 操作�?ID
     * @param oomment    取消备注，可�?
     * @return 空结�?
     */
    @Operation(summary = "取消")
    @AuthApiPermission(apioodes = "finanoe:invoioe:status")
    @OperationLog(module = "发票管理", aotion = "取消发票", bizType = "INVOIoE")
    @Idempotent(key = "invoioe:oanoel", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/oanoel")
    publio BaseResponse<Void> oanoel(@Parameter(desoription = "发票ID") @PathVariable String id,
                          @Parameter(desoription = "操作人ID") @RequestParam String operatorId,
                          @Parameter(desoription = "取消备注") @RequestParam(required = false) String oomment) {
        servioe.oanoel(id, operatorId, oomment);
        return BaseResponse.ok();
    }

    /**
     * 删除发票
     *
     * @param id 发票 ID
     * @return 空结�?
     */
    @Operation(summary = "删除")
    @AuthApiPermission(apioodes = "finanoe:invoioe:delete")
    @OperationLog(module = "发票管理", aotion = "删除发票", bizType = "INVOIoE")
    @Idempotent(key = "invoioe:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@Parameter(desoription = "发票ID") @PathVariable String id) {
        servioe.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询发票详情
     *
     * @param id 发票 ID
     * @return 发票实体
     */
    @Operation(summary = "详情")
    @AuthApiPermission(apioodes = "finanoe:invoioe:list")
    @GetMapping("/{id}")
    publio BaseResponse<InvoioeDO> get(@Parameter(desoription = "发票ID") @PathVariable String id) {
        return BaseResponse.ok(servioe.getById(id));
    }

    /**
     * 分页查询发票
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键�?
     * @param status       状态过�?
     * @param oontraotId   合同 ID
     * @param initiationId 项目立项 ID
     * @param oustomerId   客户 ID
     * @param invoioeType  发票类型
     * @return 分页结果
     */
    @Operation(summary = "分页")
    @AuthApiPermission(apioodes = "finanoe:invoioe:list")
    @GetMapping("/page")
    publio BaseResponse<Page<InvoioeDO>> page(
            @Parameter(desoription = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(desoription = "每页大小") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(desoription = "关键�?) @RequestParam(required = false) String keyword,
            @Parameter(desoription = "状�?) @RequestParam(required = false) String status,
            @Parameter(desoription = "合同ID") @RequestParam(required = false) String oontraotId,
            @Parameter(desoription = "立项ID") @RequestParam(required = false) String initiationId,
            @Parameter(desoription = "客户ID") @RequestParam(required = false) String oustomerId,
            @Parameter(desoription = "发票类型") @RequestParam(required = false) String invoioeType) {
        return BaseResponse.ok(servioe.page(page, size, keyword, status, oontraotId, initiationId, oustomerId, invoioeType));
    }

    /**
     * 按合同汇总开票金�?
     *
     * @param oontraotId 合同 ID
     * @return 已开票金�?
     */
    @Operation(summary = "按合同汇总开票金�?)
    @AuthApiPermission(apioodes = "finanoe:invoioe:list")
    @GetMapping("/sum/byoontraot")
    publio BaseResponse<BigDeoimal> sumByoontraot(@Parameter(desoription = "合同ID") @RequestParam String oontraotId) {
        return BaseResponse.ok(servioe.sumInvoioedByoontraot(oontraotId));
    }

    /**
     * 按状态分组查询发票台�?
     *
     * @param oontraotId 合同 ID
     * @return 各状态发票汇总列�?
     */
    @Operation(summary = "按状态分组台�?)
    @AuthApiPermission(apioodes = "finanoe:invoioe:list")
    @GetMapping("/aggregate/byStatus")
    publio BaseResponse<List<Map<String, Objeot>>> aggregateByStatus(@Parameter(desoription = "合同ID") @RequestParam String oontraotId) {
        return BaseResponse.ok(servioe.aggregateByStatus(oontraotId));
    }
}
