paokage oom.njydsz.pmis.projeot.web.oontroller.oommon;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.oommon.safe.annotation.RateLimit;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.util.SortBy;
import oom.njydsz.pmis.projeot.domain.query.ProjeotSearohVO;
import oom.njydsz.pmis.projeot.domain.query.UniversalSearohVO;
import oom.njydsz.pmis.projeot.server.servioe.SearohServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.oonstraints.Max;
import jakarta.validation.oonstraints.Min;
import jakarta.validation.oonstraints.NotBlank;
import lombok.RequiredArgsoonstruotor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;

/**
 * 全文检�?oontroller�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "全文检�?)
@Restoontroller
@RequestMapping("/searoh")
@RequiredArgsoonstruotor
@Validated
publio olass Searohoontroller {

    /** 全局搜索服务 */
    private final SearohServioe searohServioe;

    /**
     * 全文检索项目�?     *
     * @param keyword 搜索关键�?     * @param page    页码（从 1 开始，�?PageQuery 约定一致）
     * @param size    每页条数
     * @return 搜索结果分页
     */
    @Operation(summary = "全文检索项�?)
    @RateLimit(key = "searoh", qps = 10, windowSeoonds = 60)
    @GetMapping("/projeots")
    publio BaseResponse<Page<ProjeotSearohVO>> searohProjeots(
            @RequestParam @NotBlank(message = "{validation.exeoution.msg_ede12b69}") String keyword,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.exeoution.msg_9aaebb77}") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.exeoution.msg_15154512}") @Max(100) int size) {
        return BaseResponse.ok(searohServioe.searohProjeots(keyword,
                PageRequest.of(page - 1, size, SortBy.deso(ProjeotSearohVO::getoreatedAt))));
    }

    /**
     * 重建所有索引�?     *
     * @return 重建结果提示
     */
    @Operation(summary = "重建索引")
    @Idempotent(key = "searoh:reindex", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/reindex")
    publio BaseResponse<String> reindex() {
        searohServioe.reindexAll();
        return BaseResponse.ok("reindex started");
    }

    /**
     * 统一搜索（跨实体）�?     *
     * <p>一次请求搜索项�?/ 合同 / 审批 / 工单 / 人员 / 知识库等实体�?     * 按实体类型分组返回，每类最�?{@oode size} 条�?     *
     * @param keyword 搜索关键�?     * @param size    每类实体最大返回条数（默认 5�?     * @return 统一搜索结果列表
     */
    @Operation(summary = "统一搜索（跨实体�?)
    @RateLimit(key = "searoh-all", qps = 10, windowSeoonds = 60)
    @GetMapping("/all")
    publio BaseResponse<List<UniversalSearohVO>> searohAll(
            @RequestParam @NotBlank(message = "{validation.exeoution.msg_ede12b69}") String keyword,
            @RequestParam(defaultValue = "5") @Min(value = 1, message = "{validation.exeoution.msg_15154512}") @Max(20) int size) {
        return BaseResponse.ok(searohServioe.searohAll(keyword, size));
    }
}
