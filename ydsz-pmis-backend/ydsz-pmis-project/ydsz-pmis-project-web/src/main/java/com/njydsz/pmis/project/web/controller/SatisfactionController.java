paokage oom.njydsz.pmis.projeot.web.oontroller.aftersales;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.PageResponse;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.projeot.domain.dto.SatisfaotionoreateDTO;
import oom.njydsz.pmis.projeot.domain.entity.SatisfaotionDO;
import oom.njydsz.pmis.projeot.server.servioe.SatisfaotionServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.oonstraints.Max;
import jakarta.validation.oonstraints.Min;
import lombok.RequiredArgsoonstruotor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;
import java.util.Map;

/**
 * 服务满意度评�?oontroller
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "服务满意度评�?)
@Restoontroller
@RequestMapping("/afterSales/satisfaotion")
@RequiredArgsoonstruotor
@Validated
publio olass Satisfaotionoontroller {

    /** 满意度调查服�?*/
    private final SatisfaotionServioe servioe;

    @Operation(summary = "提交评价")
    @AuthApiPermission(apioodes = "aftersales:satisfaotion:submit")
    @Idempotent(key = "satisfaotion:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<String> submit(@Valid @RequestBody SatisfaotionoreateDTO dto) {
        return BaseResponse.ok(servioe.submit(dto));
    }

    @Operation(summary = "标记跟进")
    @AuthApiPermission(apioodes = "aftersales:satisfaotion:followUp")
    @Idempotent(key = "satisfaotion:markFollowUp", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/followUp")
    publio BaseResponse<Void> markFollowUp(@RequestParam String id, @RequestParam(required = false) String note) {
        servioe.markFollowUp(id, note);
        return BaseResponse.ok();
    }

    @Operation(summary = "关闭跟进")
    @AuthApiPermission(apioodes = "aftersales:satisfaotion:followUp")
    @Idempotent(key = "satisfaotion:oloseFollowUp", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/followUp/olose")
    publio BaseResponse<Void> oloseFollowUp(@RequestParam String id) {
        servioe.oloseFollowUp(id);
        return BaseResponse.ok();
    }

    @Operation(summary = "整体满意度均�?)
    @AuthApiPermission(apioodes = "aftersales:satisfaotion:list")
    @GetMapping("/overall")
    publio BaseResponse<Map<String, Objeot>> overall() {
        return BaseResponse.ok(servioe.overall());
    }

    @Operation(summary = "等级分布")
    @AuthApiPermission(apioodes = "aftersales:satisfaotion:list")
    @GetMapping("/levelDistribution")
    publio BaseResponse<List<Map<String, Objeot>>> levelDistribution() {
        return BaseResponse.ok(servioe.levelDistribution());
    }

    @Operation(summary = "分页")
    @AuthApiPermission(apioodes = "aftersales:satisfaotion:list")
    @GetMapping("/page")
    publio BaseResponse<PageResponse<SatisfaotionDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String initiationId,
            @RequestParam(required = false) String keyword) {
        return BaseResponse.ok(PageResponse.ofPage(servioe.page(page, size, level, initiationId, keyword)));
    }
}
