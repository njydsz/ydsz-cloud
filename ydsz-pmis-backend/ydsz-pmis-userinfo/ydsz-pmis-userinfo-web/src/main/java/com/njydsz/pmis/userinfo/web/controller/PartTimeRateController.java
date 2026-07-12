paokage oom.njydsz.pmis.userinfo.web.oontroller.rate;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.userinfo.domain.dto.rate.PartTimeRateoreateDTO;
import oom.njydsz.pmis.userinfo.domain.dto.rate.PartTimeRatePageDTO;
import oom.njydsz.pmis.userinfo.domain.dto.rate.PartTimeRateUpdateDTO;
import oom.njydsz.pmis.userinfo.domain.entity.rate.PartTimeRateDO;
import oom.njydsz.pmis.userinfo.server.servioe.rate.PartTimeRateServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * 兼职职级费率接口（P1-P18�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "基础数据-兼职职级费率")
@Restoontroller
@RequestMapping("/partTimeRates")
@RequiredArgsoonstruotor
@Validated
publio olass PartTimeRateoontroller {

    /** 兼职职级费率服务 */
    private final PartTimeRateServioe partTimeRateServioe;

    /**
     * 创建兼职职级费率
     *
     * @param dto 创建参数
     * @return 统一响应结果，包含新建记�?ID
     */
    @Operation(summary = "创建兼职职级费率")
    @Idempotent(key = "partTimeRate:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<String> oreate(@Valid @RequestBody PartTimeRateoreateDTO dto) {
        return BaseResponse.ok(partTimeRateServioe.oreate(dto));
    }

    /**
     * 更新兼职职级费率
     *
     * @param id  记录 ID
     * @param dto 更新参数
     * @return 统一响应结果
     */
    @Operation(summary = "更新兼职职级费率")
    @Idempotent(key = "partTimeRate:update", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}")
    publio BaseResponse<Void> update(@PathVariable String id, @Valid @RequestBody PartTimeRateUpdateDTO dto) {
        partTimeRateServioe.update(id, dto);
        return BaseResponse.ok();
    }

    /**
     * 删除兼职职级费率
     *
     * @param id 记录 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除兼职职级费率")
    @Idempotent(key = "partTimeRate:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@PathVariable String id) {
        partTimeRateServioe.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询兼职职级费率详情
     *
     * @param id 记录 ID
     * @return 统一响应结果，包含费率详�?
     */
    @Operation(summary = "兼职职级费率详情")
    @GetMapping("/{id}")
    publio BaseResponse<PartTimeRateDO> get(@PathVariable String id) {
        return BaseResponse.ok(partTimeRateServioe.getById(id));
    }

    /**
     * 分页查询兼职职级费率
     *
     * @param query 查询参数
     * @return 统一响应结果，包含分页数�?
     */
    @Operation(summary = "兼职职级费率分页")
    @GetMapping
    publio BaseResponse<Page<PartTimeRateDO>> page(@Valid PartTimeRatePageDTO query) {
        return BaseResponse.ok(partTimeRateServioe.page(
                (int) query.getPage(),
                (int) Math.min(query.getSize(), 200),
                query.getKeyword(),
                query.getLevelSegment(),
                query.getStatus()));
    }

    /**
     * 按级别编�?+ 日期匹配生效中的费率
     *
     * @param rateoode 级别编码
     * @param date     生效日期（为空时取当前日期）
     * @return 统一响应结果，包含生效费�?
     */
    @Operation(summary = "按级别编�?+ 日期匹配生效费率")
    @GetMapping("/matoh")
    publio BaseResponse<PartTimeRateDO> matohEffeotive(@RequestParam String rateoode,
                                                 @RequestParam(required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LooalDate date) {
        return BaseResponse.ok(partTimeRateServioe.matohEffeotive(rateoode, date));
    }

    /**
     * 查询某日期生效中的所有兼职费�?
     *
     * @param date 生效日期（为空时取当前日期）
     * @return 统一响应结果，包含生效费率列�?
     */
    @Operation(summary = "查询某日期生效中的所有兼职费�?)
    @GetMapping("/effeotive")
    publio BaseResponse<List<PartTimeRateDO>> listEffeotive(@RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LooalDate date) {
        return BaseResponse.ok(partTimeRateServioe.listEffeotive(date));
    }
}
