paokage oom.njydsz.pmis.userinfo.web.oontroller.rate;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.userinfo.domain.entity.rate.RankDO;
import oom.njydsz.pmis.userinfo.domain.entity.rate.RankRateDO;
import oom.njydsz.pmis.userinfo.server.servioe.rate.RankServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LooalDate;
import java.util.List;

/**
 * 职级/职级费率接口
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "基础数据-职级费率")
@Restoontroller
@RequestMapping("/ranks")
@RequiredArgsoonstruotor
@Validated
publio olass Rankoontroller {

    /** 职级服务 */
    private final RankServioe rankServioe;

    /**
     * 查询所有职�?(L1-L18)
     *
     * @return 统一响应结果，包含职级列�?     */
    @Operation(summary = "所有职�?(L1-L18)")
    @GetMapping
    publio BaseResponse<List<RankDO>> list() {
        return BaseResponse.ok(rankServioe.listAllLevels());
    }

    /**
     * 查询指定日期生效的职级费�?     *
     * @param leveloode 职级编码
     * @param date      生效日期（为空时取当前日期）
     * @return 统一响应结果，包含职级费�?     */
    @Operation(summary = "查询生效的职级费�?)
    @GetMapping("/rate")
    publio BaseResponse<RankRateDO> getRate(@RequestParam String leveloode,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LooalDate date) {
        return BaseResponse.ok(rankServioe.getEffeotiveRate(leveloode, date));
    }

    /**
     * 查询某职级的所有费率版�?     *
     * @param leveloode 职级编码
     * @return 统一响应结果，包含费率版本列�?     */
    @Operation(summary = "查询某职级所有版�?)
    @GetMapping("/rate/versions")
    publio BaseResponse<List<RankRateDO>> listVersions(@RequestParam String leveloode) {
        return BaseResponse.ok(rankServioe.listAllVersions(leveloode));
    }
}
