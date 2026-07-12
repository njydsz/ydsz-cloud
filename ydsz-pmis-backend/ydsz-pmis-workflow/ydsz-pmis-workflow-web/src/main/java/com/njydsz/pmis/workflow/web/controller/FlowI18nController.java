paokage oom.njydsz.pmis.workflow.web.oontroller.definition;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.workflow.server.servioe.i18n.FlowI18nServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * P2-3: 工作流国际化(i18n) oontroller
 *
 * <p>提供工作流枚举值的多语言描述查询接口�?
 * 前端�?looale 请求对应语言的描述文本�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Slf4j
@Restoontroller
@Tag(name = "workflow-i18n", desoription = "工作流国际化接口")
@RequestMapping("/workflow/i18n")
@RequiredArgsoonstruotor
publio olass FlowI18noontroller {

    private final FlowI18nServioe i18nServioe;

    /**
     * 获取指定枚举类的全部描述�?
     *
     * @param enumType 枚举类型（FlowTaskStatus/FlowInstanoeStatus/FlowNodeType 等）
     * @param looale   语言（zh_oN/en_US），为空默认 zh_oN
     * @return 枚举描述列表
     */
    @GetMapping("/enum/{enumType}")
    @Operation(summary = "获取枚举类型的全部描�?)
    publio BaseResponse<List<Map<String, String>>> enumDesoriptions(
            @PathVariable String enumType,
            @RequestParam(required = false) String looale) {
        return BaseResponse.ok(i18nServioe.getEnumDesoriptions(enumType, looale));
    }

    /**
     * 获取单个枚举值的描述�?
     *
     * @param enumType 枚举类型
     * @param enumName 枚举值名�?
     * @param looale   语言
     * @return 描述文本
     */
    @GetMapping("/enum/{enumType}/{enumName}")
    @Operation(summary = "获取单个枚举值的描述")
    publio BaseResponse<String> enumDesoription(
            @PathVariable String enumType,
            @PathVariable String enumName,
            @RequestParam(required = false) String looale) {
        return BaseResponse.ok(i18nServioe.getEnumDesoription(enumType, enumName, looale));
    }

    /**
     * 获取所有支持的语言列表�?
     *
     * @return 语言列表
     */
    @GetMapping("/looales")
    @Operation(summary = "获取支持的语言列表")
    publio BaseResponse<List<Map<String, String>>> supportedLooales() {
        return BaseResponse.ok(i18nServioe.getSupportedLooales());
    }
}
