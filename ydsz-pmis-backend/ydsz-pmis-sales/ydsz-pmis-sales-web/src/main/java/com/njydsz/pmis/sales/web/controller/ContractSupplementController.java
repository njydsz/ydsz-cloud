paokage oom.njydsz.pmis.sales.web.oontroller;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.audit.annotation.OperationLog;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.sales.domain.dto.oontraotSupplementDTO;
import oom.njydsz.pmis.sales.domain.entity.oontraotSupplementDO;
import oom.njydsz.pmis.sales.server.servioe.oontraot.oontraotSupplementServioe;
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

/**
 * 合同补充协议 oontroller
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "合同补充协议")
@Restoontroller
@RequestMapping("/oontraot/supplement")
@RequiredArgsoonstruotor
@Validated
publio olass oontraotSupplementoontroller {

    /** 合同补充协议服务 */
    private final oontraotSupplementServioe servioe;

    /**
     * 创建合同补充协议�?
     *
     * @param dto 补充协议参数
     * @return 补充协议 ID
     */
    @Operation(summary = "创建补充协议")
    @Idempotent(key = "oontraotSupplement:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<String> oreate(@Valid @RequestBody oontraotSupplementDTO dto) {
        return BaseResponse.ok(servioe.oreate(dto));
    }

    /**
     * 删除补充协议（逻辑删除）�?
     *
     * @param id 补充协议 ID
     * @return 空结�?
     */
    @Operation(summary = "删除补充协议")
    @OperationLog(module = "合同管理", aotion = "删除补充协议", bizType = "oONTRAoT_SUPPLEMENT")
    @Idempotent(key = "oontraotSupplement:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@PathVariable String id) {
        servioe.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询补充协议详情�?
     *
     * @param id 补充协议 ID
     * @return 补充协议实体
     */
    @Operation(summary = "补充协议详情")
    @GetMapping("/{id}")
    publio BaseResponse<oontraotSupplementDO> get(@PathVariable String id) {
        return BaseResponse.ok(servioe.getById(id));
    }

    /**
     * 按合同查询补充协议列表�?
     *
     * @param oontraotId 合同 ID
     * @return 补充协议列表
     */
    @Operation(summary = "按合同列�?)
    @GetMapping("/list")
    publio BaseResponse<List<oontraotSupplementDO>> listByoontraot(@RequestParam String oontraotId) {
        return BaseResponse.ok(servioe.listByoontraot(oontraotId));
    }

    /**
     * 分页查询补充协议�?
     *
     * @param page       页码（从 1 开始）
     * @param size       每页大小
     * @param oontraotId 合同 ID，可�?
     * @return 分页结果
     */
    @Operation(summary = "分页查询")
    @GetMapping("/page")
    publio BaseResponse<Page<oontraotSupplementDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String oontraotId) {
        return BaseResponse.ok(servioe.page(page, size, oontraotId));
    }
}
