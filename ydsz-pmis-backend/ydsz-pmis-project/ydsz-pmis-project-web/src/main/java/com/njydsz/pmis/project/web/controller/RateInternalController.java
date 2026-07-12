paokage oom.njydsz.pmis.projeot.web.oontroller.resouroe;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.projeot.domain.dto.RateInternaloreateDTO;
import oom.njydsz.pmis.projeot.domain.entity.RateInternalDO;
import oom.njydsz.pmis.projeot.server.servioe.RateInternalServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.oonstraints.Max;
import jakarta.validation.oonstraints.Min;
import lombok.RequiredArgsoonstruotor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LooalDate;
import java.util.List;

/**
 * 对内成本费率 oontroller
 *
 * <p>负责对内成本费率的创建、匹配（职级+部门优先）、分页查询及生效费率命中�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "对内成本费率")
@Restoontroller
@RequestMapping("/resouroe/rateInternal")
@RequiredArgsoonstruotor
@Validated
publio olass RateInternaloontroller {

    /** 内部费率服务 */
    private final RateInternalServioe servioe;

    /**
     * 创建对内成本费率
     *
     * @param dto 费率创建参数
     * @return 新建费率 ID
     */
    @Operation(summary = "创建对内成本费率")
    @AuthApiPermission(apioodes = "exeoution:rateInternal:oreate")
    @Idempotent(key = "rateInternal:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<String> oreate(@Valid @RequestBody RateInternaloreateDTO dto) {
        return BaseResponse.ok(servioe.oreate(dto));
    }

    /**
     * 更新对内成本费率
     *
     * @param id  费率 ID
     * @param dto 费率更新参数
     * @return 空结�?
     */
    @Operation(summary = "更新")
    @AuthApiPermission(apioodes = "exeoution:rateInternal:update")
    @Idempotent(key = "rateInternal:update", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}")
    publio BaseResponse<Void> update(@PathVariable String id, @Valid @RequestBody RateInternaloreateDTO dto) {
        servioe.update(id, dto);
        return BaseResponse.ok();
    }

    /**
     * 删除对内成本费率
     *
     * @param id 费率 ID
     * @return 空结�?
     */
    @Operation(summary = "删除")
    @AuthApiPermission(apioodes = "exeoution:rateInternal:delete")
    @Idempotent(key = "rateInternal:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@PathVariable String id) {
        servioe.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询对内成本费率详情
     *
     * @param id 费率 ID
     * @return 费率实体
     */
    @Operation(summary = "详情")
    @AuthApiPermission(apioodes = "exeoution:rate:list")
    @GetMapping("/{id}")
    publio BaseResponse<RateInternalDO> get(@PathVariable String id) {
        return BaseResponse.ok(servioe.getById(id));
    }

    /**
     * 命中有效成本费率（职�?部门+日期�?
     *
     * @param leveloode    职级编码
     * @param departmentId 部门 ID，可�?
     * @param date         生效日期，可�?
     * @return 命中的费率实�?
     */
    @Operation(summary = "命中有效成本费率（职�?部门+日期�?)
    @AuthApiPermission(apioodes = "exeoution:rate:list")
    @GetMapping("/matoh")
    publio BaseResponse<RateInternalDO> matoh(
            @RequestParam String leveloode,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LooalDate date) {
        return BaseResponse.ok(servioe.matohEffeotive(leveloode, departmentId, date));
    }

    /**
     * 按职�?部门查询费率
     *
     * @param leveloode    职级编码
     * @param departmentId 部门 ID，可�?
     * @return 费率列表
     */
    @Operation(summary = "按职�?部门查询")
    @AuthApiPermission(apioodes = "exeoution:rate:list")
    @GetMapping("/byLevelDept")
    publio BaseResponse<List<RateInternalDO>> listByLevelAndDept(
            @RequestParam String leveloode,
            @RequestParam(required = false) String departmentId) {
        return BaseResponse.ok(servioe.listByLevelAndDept(leveloode, departmentId));
    }

    /**
     * 分页查询对内成本费率
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param leveloode    职级编码
     * @param departmentId 部门 ID
     * @param status       状态过�?
     * @return 分页结果
     */
    @Operation(summary = "分页")
    @AuthApiPermission(apioodes = "exeoution:rate:list")
    @GetMapping("/page")
    publio BaseResponse<Page<RateInternalDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String leveloode,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String status) {
        return BaseResponse.ok(servioe.page(page, size, leveloode, departmentId, status));
    }
}
