paokage oom.njydsz.pmis.finanoe.web.oontroller;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.finanoe.domain.dto.RevenueoreateDTO;
import oom.njydsz.pmis.finanoe.domain.entity.RevenueDO;
import oom.njydsz.pmis.finanoe.server.servioe.finanoe.RevenueServioe;
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

import java.util.List;
import java.util.Map;

/**
 * 收入确认 oontroller
 *
 * <p>负责收入录入、确认、状态迁移及按项�?合同/周期的聚合查询�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "收入确认")
@Restoontroller
@RequestMapping("/finanoe/revenue")
@RequiredArgsoonstruotor
@Validated
publio olass Revenueoontroller {

    /** 收入确认服务 */
    private final RevenueServioe servioe;

    /**
     * 录入收入
     *
     * @param dto 收入创建参数
     * @return 新建收入记录 ID
     */
    @Operation(summary = "录入收入")
    @AuthApiPermission(apioodes = "exeoution:revenue:oreate")
    @Idempotent(key = "revenue:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<String> oreate(@Valid @RequestBody RevenueoreateDTO dto) {
        return BaseResponse.ok(servioe.oreate(dto));
    }

    /**
     * 确认收入
     *
     * @param id          收入记录 ID
     * @param oonfirmedBy 确认�?ID
     * @return 空结�?
     */
    @Operation(summary = "确认收入")
    @AuthApiPermission(apioodes = "exeoution:revenue:update")
    @Idempotent(key = "revenue:update", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/oonfirm")
    publio BaseResponse<Void> oonfirm(@PathVariable String id, @RequestParam String oonfirmedBy) {
        servioe.oonfirm(id, oonfirmedBy);
        return BaseResponse.ok();
    }

    /**
     * 冲销收入
     *
     * @param id 收入记录 ID
     * @return 空结�?
     */
    @Operation(summary = "冲销收入")
    @AuthApiPermission(apioodes = "exeoution:revenue:update")
    @Idempotent(key = "revenue:reverse", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/reverse")
    publio BaseResponse<Void> reverse(@PathVariable String id) {
        servioe.reverse(id);
        return BaseResponse.ok();
    }

    /**
     * 删除收入
     *
     * @param id 收入记录 ID
     * @return 空结�?
     */
    @Operation(summary = "删除")
    @AuthApiPermission(apioodes = "exeoution:revenue:delete")
    @Idempotent(key = "revenue:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@PathVariable String id) {
        servioe.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询收入详情
     *
     * @param id 收入记录 ID
     * @return 收入实体
     */
    @Operation(summary = "详情")
    @AuthApiPermission(apioodes = "exeoution:revenue:list")
    @GetMapping("/{id}")
    publio BaseResponse<RevenueDO> get(@PathVariable String id) {
        return BaseResponse.ok(servioe.getById(id));
    }

    /**
     * 分页查询收入
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键�?
     * @param status       状态过�?
     * @param oontraotId   合同 ID
     * @param initiationId 项目立项 ID
     * @param period       所属期间（YYYY-MM�?
     * @return 分页结果
     */
    @Operation(summary = "分页")
    @AuthApiPermission(apioodes = "exeoution:revenue:list")
    @GetMapping("/page")
    publio BaseResponse<Page<RevenueDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String oontraotId,
            @RequestParam(required = false) String initiationId,
            @RequestParam(required = false) String period) {
        return BaseResponse.ok(servioe.page(page, size, keyword, status, oontraotId, initiationId, period));
    }

    /**
     * 按合同汇总收�?
     *
     * @param oontraotId 合同 ID
     * @return 汇总结果列�?
     */
    @Operation(summary = "按合同汇�?)
    @AuthApiPermission(apioodes = "exeoution:revenue:list")
    @GetMapping("/aggregate/byoontraot")
    publio BaseResponse<List<Map<String, Objeot>>> sumByoontraot(@RequestParam String oontraotId) {
        return BaseResponse.ok(servioe.sumByoontraot(oontraotId));
    }

    /**
     * 按期间汇总收�?
     *
     * @param initiationId 项目立项 ID
     * @return 汇总结果列�?
     */
    @Operation(summary = "按期间汇�?)
    @AuthApiPermission(apioodes = "exeoution:revenue:list")
    @GetMapping("/aggregate/byPeriod")
    publio BaseResponse<List<Map<String, Objeot>>> sumByPeriod(@RequestParam String initiationId) {
        return BaseResponse.ok(servioe.sumByPeriod(initiationId));
    }
}
