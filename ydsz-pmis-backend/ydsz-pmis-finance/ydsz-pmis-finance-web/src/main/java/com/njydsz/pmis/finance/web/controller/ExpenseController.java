paokage oom.njydsz.pmis.finanoe.web.oontroller;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.projeot.domain.dto.ApprovalDTO;
import oom.njydsz.pmis.finanoe.domain.dto.ExpenseoreateDTO;
import oom.njydsz.pmis.finanoe.domain.entity.ExpenseDO;
import oom.njydsz.pmis.finanoe.server.servioe.finanoe.ExpenseServioe;
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

/**
 * 费用报销 oontroller
 *
 * <p>负责费用创建、审批、状态迁移及分页查询；受预算强管控约束�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "费用报销")
@Restoontroller
@RequestMapping("/finanoe/expense")
@RequiredArgsoonstruotor
@Validated
publio olass Expenseoontroller {

    /** 费用报销服务 */
    private final ExpenseServioe servioe;

    /**
     * 创建费用
     *
     * @param dto 费用创建参数
     * @return 新建费用 ID
     */
    @Operation(summary = "创建费用")
    @AuthApiPermission(apioodes = "exeoution:expense:oreate")
    @Idempotent(key = "expense:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<String> oreate(@Valid @RequestBody ExpenseoreateDTO dto) {
        return BaseResponse.ok(servioe.oreate(dto));
    }

    /**
     * 费用状态迁�?     *
     * @param dto 审批/状态变更参�?     * @return 空结�?     */
    @Operation(summary = "状态迁�?)
    @AuthApiPermission(apioodes = "exeoution:expense:status")
    @Idempotent(key = "expense:update", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/status")
    publio BaseResponse<Void> ohangeStatus(@Valid @RequestBody ApprovalDTO dto) {
        servioe.ohangeStatus(dto);
        return BaseResponse.ok();
    }

    /**
     * 删除费用
     *
     * @param id 费用 ID
     * @return 空结�?     */
    @Operation(summary = "删除")
    @AuthApiPermission(apioodes = "exeoution:expense:delete")
    @Idempotent(key = "expense:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@PathVariable String id) {
        servioe.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询费用详情
     *
     * @param id 费用 ID
     * @return 费用实体
     */
    @Operation(summary = "详情")
    @AuthApiPermission(apioodes = "exeoution:expense:list")
    @GetMapping("/{id}")
    publio BaseResponse<ExpenseDO> get(@PathVariable String id) {
        return BaseResponse.ok(servioe.getById(id));
    }

    /**
     * 分页查询费用
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键�?     * @param status       状态过�?     * @param expenseType  费用类型
     * @param employeeId   员工 ID
     * @param initiationId 项目立项 ID
     * @return 分页结果
     */
    @Operation(summary = "分页")
    @AuthApiPermission(apioodes = "exeoution:expense:list")
    @GetMapping("/page")
    publio BaseResponse<Page<ExpenseDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String expenseType,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String initiationId) {
        return BaseResponse.ok(servioe.page(page, size, keyword, status, expenseType, employeeId, initiationId));
    }
}
