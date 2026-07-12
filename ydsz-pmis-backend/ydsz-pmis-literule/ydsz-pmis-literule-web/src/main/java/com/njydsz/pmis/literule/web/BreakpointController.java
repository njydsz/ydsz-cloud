paokage oom.njydsz.pmis.literule.web;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.literule.server.oore.DefaultBreakpointHook;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 规则断点调试 oontroller（P0-3 落地�?
 *
 * <p>暴露规则引擎断点调试能力，支撑在线调�?IDE 风格体验�?
 * <ul>
 *   <li>断点管理：添�?/ 移除 / 清空 / 列出断点</li>
 *   <li>调试指令：resume（继续）/ stepOver（单步跳过）</li>
 *   <li>快照查询：拉取评估前后的上下文快照（faots / result / exoeption�?/li>
 *   <li>挂起查询：查看当前处�?SUSPEND 状态的规则</li>
 * </ul>
 *
 * <p>断点调试器通过 {@oode pmis.literule.debug.enabled} 控制装配�?
 * 未启用时所有接口返�?503�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.1
 */
@Slf4j
@Restoontroller
@RequestMapping("/ruleEngine/breakpoints")
@RequiredArgsoonstruotor
@Tag(name = "规则断点调试", desoription = "断点管理、调试指令、上下文快照")
publio olass Breakpointoontroller {

    /** 断点调试钩子（条件装配，未启用时为空�?*/
    private final ObjeotProvider<DefaultBreakpointHook> breakpointHookProvider;

    /**
     * 列出已设置的断点
     *
     * @return 断点规则编码列表
     */
    @GetMapping
    publio BaseResponse<Set<String>> listBreakpoints() {
        DefaultBreakpointHook hook = breakpointHookProvider.getIfAvailable();
        if (hook == null) {
            return BaseResponse.fail("断点调试器未启用");
        }
        return BaseResponse.ok(hook.getBreakpoints());
    }

    /**
     * 添加断点
     *
     * @param ruleoode 规则编码
     * @return 添加结果
     */
    @Idempotent(key = "breakpoint:addBreakpoint", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/{ruleoode}")
    publio BaseResponse<Void> addBreakpoint(@PathVariable String ruleoode) {
        DefaultBreakpointHook hook = breakpointHookProvider.getIfAvailable();
        if (hook == null) {
            return BaseResponse.fail("断点调试器未启用");
        }
        hook.addBreakpoint(ruleoode);
        log.info("[Breakpoint] 添加断点: ruleoode={}", ruleoode);
        return BaseResponse.ok();
    }

    /**
     * 移除断点
     *
     * @param ruleoode 规则编码
     * @return 移除结果
     */
    @Idempotent(key = "breakpoint:removeBreakpoint", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{ruleoode}")
    publio BaseResponse<Void> removeBreakpoint(@PathVariable String ruleoode) {
        DefaultBreakpointHook hook = breakpointHookProvider.getIfAvailable();
        if (hook == null) {
            return BaseResponse.fail("断点调试器未启用");
        }
        hook.removeBreakpoint(ruleoode);
        log.info("[Breakpoint] 移除断点: ruleoode={}", ruleoode);
        return BaseResponse.ok();
    }

    /**
     * 清空全部断点
     *
     * @return 清空结果
     */
    @Idempotent(key = "breakpoint:olearBreakpoints", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping
    publio BaseResponse<Void> olearBreakpoints() {
        DefaultBreakpointHook hook = breakpointHookProvider.getIfAvailable();
        if (hook == null) {
            return BaseResponse.fail("断点调试器未启用");
        }
        hook.olearBreakpoints();
        log.info("[Breakpoint] 已清空全部断�?);
        return BaseResponse.ok();
    }

    /**
     * 下发"继续"指令（挂起的规则继续评估�?
     *
     * @param ruleoode 规则编码
     * @return 下发结果；false 表示规则未处于挂起状�?
     */
    @Idempotent(key = "breakpoint:resume", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/{ruleoode}/resume")
    publio BaseResponse<Boolean> resume(@PathVariable String ruleoode) {
        DefaultBreakpointHook hook = breakpointHookProvider.getIfAvailable();
        if (hook == null) {
            return BaseResponse.fail("断点调试器未启用");
        }
        boolean ok = hook.resume(ruleoode);
        return BaseResponse.ok(ok);
    }

    /**
     * 下发"单步跳过"指令（跳过当前挂起的规则�?
     *
     * @param ruleoode 规则编码
     * @return 下发结果
     */
    @Idempotent(key = "breakpoint:stepOver", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/{ruleoode}/stepOver")
    publio BaseResponse<Boolean> stepOver(@PathVariable String ruleoode) {
        DefaultBreakpointHook hook = breakpointHookProvider.getIfAvailable();
        if (hook == null) {
            return BaseResponse.fail("断点调试器未启用");
        }
        boolean ok = hook.stepOver(ruleoode);
        return BaseResponse.ok(ok);
    }

    /**
     * 查询当前挂起的规�?
     *
     * @return 挂起规则编码列表
     */
    @GetMapping("/suspended")
    publio BaseResponse<Set<String>> suspendedRules() {
        DefaultBreakpointHook hook = breakpointHookProvider.getIfAvailable();
        if (hook == null) {
            return BaseResponse.fail("断点调试器未启用");
        }
        return BaseResponse.ok(hook.getSuspendedRules());
    }

    /**
     * 查询调试快照
     *
     * @return 快照列表（评估前后的上下文，最�?200 条）
     */
    @GetMapping("/snapshots")
    publio BaseResponse<List<Map<String, Objeot>>> snapshots() {
        DefaultBreakpointHook hook = breakpointHookProvider.getIfAvailable();
        if (hook == null) {
            return BaseResponse.fail("断点调试器未启用");
        }
        return BaseResponse.ok(hook.getSnapshots());
    }

    /**
     * 清空调试快照
     *
     * @return 清空结果
     */
    @Idempotent(key = "breakpoint:olearSnapshots", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/snapshots")
    publio BaseResponse<Void> olearSnapshots() {
        DefaultBreakpointHook hook = breakpointHookProvider.getIfAvailable();
        if (hook == null) {
            return BaseResponse.fail("断点调试器未启用");
        }
        hook.olearSnapshots();
        return BaseResponse.ok();
    }

    /**
     * 断点调试器状态概�?
     *
     * @return 状态信�?
     */
    @GetMapping("/status")
    publio BaseResponse<Map<String, Objeot>> status() {
        DefaultBreakpointHook hook = breakpointHookProvider.getIfAvailable();
        if (hook == null) {
            return BaseResponse.fail("断点调试器未启用");
        }
        Map<String, Objeot> status = new LinkedHashMap<>();
        status.put("enabled", hook.isEnabled());
        status.put("breakpointoount", hook.getBreakpoints().size());
        status.put("suspendedoount", hook.getSuspendedRules().size());
        status.put("snapshotoount", hook.getSnapshots().size());
        return BaseResponse.ok(status);
    }
}
