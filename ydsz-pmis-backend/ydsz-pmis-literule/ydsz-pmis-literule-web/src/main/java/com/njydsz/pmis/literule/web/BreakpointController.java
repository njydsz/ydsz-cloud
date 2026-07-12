package com.njydsz.pmis.literule.web;

import com.njydsz.pmis.common.lock.annotation.Idempotent;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.literule.server.core.DefaultBreakpointHook;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 规则断点调试 Controller（P0-3 落地）
 *
 * <p>暴露规则引擎断点调试能力，支撑在线调试 IDE 风格体验：
 * <ul>
 *   <li>断点管理：添加 / 移除 / 清空 / 列出断点</li>
 *   <li>调试指令：resume（继续）/ stepOver（单步跳过）</li>
 *   <li>快照查询：拉取评估前后的上下文快照（facts / result / exception）</li>
 *   <li>挂起查询：查看当前处于 SUSPEND 状态的规则</li>
 * </ul>
 *
 * <p>断点调试器通过 {@code pmis.literule.debug.enabled} 控制装配，
 * 未启用时所有接口返回 503。
 *
 * @author ydsz-pmis-team
 * @since 1.5.1
 */
@Slf4j
@RestController
@RequestMapping("/ruleEngine/breakpoints")
@RequiredArgsConstructor
@Tag(name = "规则断点调试", description = "断点管理、调试指令、上下文快照")
public class BreakpointController {

    /** 断点调试钩子（条件装配，未启用时为空） */
    private final ObjectProvider<DefaultBreakpointHook> breakpointHookProvider;

    /**
     * 列出已设置的断点
     *
     * @return 断点规则编码列表
     */
    @GetMapping
    public BaseResponse<Set<String>> listBreakpoints() {
        DefaultBreakpointHook hook = breakpointHookProvider.getIfAvailable();
        if (hook == null) {
            return BaseResponse.fail("断点调试器未启用");
        }
        return BaseResponse.ok(hook.getBreakpoints());
    }

    /**
     * 添加断点
     *
     * @param ruleCode 规则编码
     * @return 添加结果
     */
    @Idempotent(key = "breakpoint:addBreakpoint", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{ruleCode}")
    public BaseResponse<Void> addBreakpoint(@PathVariable String ruleCode) {
        DefaultBreakpointHook hook = breakpointHookProvider.getIfAvailable();
        if (hook == null) {
            return BaseResponse.fail("断点调试器未启用");
        }
        hook.addBreakpoint(ruleCode);
        log.info("[Breakpoint] 添加断点: ruleCode={}", ruleCode);
        return BaseResponse.ok();
    }

    /**
     * 移除断点
     *
     * @param ruleCode 规则编码
     * @return 移除结果
     */
    @Idempotent(key = "breakpoint:removeBreakpoint", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{ruleCode}")
    public BaseResponse<Void> removeBreakpoint(@PathVariable String ruleCode) {
        DefaultBreakpointHook hook = breakpointHookProvider.getIfAvailable();
        if (hook == null) {
            return BaseResponse.fail("断点调试器未启用");
        }
        hook.removeBreakpoint(ruleCode);
        log.info("[Breakpoint] 移除断点: ruleCode={}", ruleCode);
        return BaseResponse.ok();
    }

    /**
     * 清空全部断点
     *
     * @return 清空结果
     */
    @Idempotent(key = "breakpoint:clearBreakpoints", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping
    public BaseResponse<Void> clearBreakpoints() {
        DefaultBreakpointHook hook = breakpointHookProvider.getIfAvailable();
        if (hook == null) {
            return BaseResponse.fail("断点调试器未启用");
        }
        hook.clearBreakpoints();
        log.info("[Breakpoint] 已清空全部断点");
        return BaseResponse.ok();
    }

    /**
     * 下发"继续"指令（挂起的规则继续评估）
     *
     * @param ruleCode 规则编码
     * @return 下发结果；false 表示规则未处于挂起状态
     */
    @Idempotent(key = "breakpoint:resume", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{ruleCode}/resume")
    public BaseResponse<Boolean> resume(@PathVariable String ruleCode) {
        DefaultBreakpointHook hook = breakpointHookProvider.getIfAvailable();
        if (hook == null) {
            return BaseResponse.fail("断点调试器未启用");
        }
        boolean ok = hook.resume(ruleCode);
        return BaseResponse.ok(ok);
    }

    /**
     * 下发"单步跳过"指令（跳过当前挂起的规则）
     *
     * @param ruleCode 规则编码
     * @return 下发结果
     */
    @Idempotent(key = "breakpoint:stepOver", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{ruleCode}/stepOver")
    public BaseResponse<Boolean> stepOver(@PathVariable String ruleCode) {
        DefaultBreakpointHook hook = breakpointHookProvider.getIfAvailable();
        if (hook == null) {
            return BaseResponse.fail("断点调试器未启用");
        }
        boolean ok = hook.stepOver(ruleCode);
        return BaseResponse.ok(ok);
    }

    /**
     * 查询当前挂起的规则
     *
     * @return 挂起规则编码列表
     */
    @GetMapping("/suspended")
    public BaseResponse<Set<String>> suspendedRules() {
        DefaultBreakpointHook hook = breakpointHookProvider.getIfAvailable();
        if (hook == null) {
            return BaseResponse.fail("断点调试器未启用");
        }
        return BaseResponse.ok(hook.getSuspendedRules());
    }

    /**
     * 查询调试快照
     *
     * @return 快照列表（评估前后的上下文，最多 200 条）
     */
    @GetMapping("/snapshots")
    public BaseResponse<List<Map<String, Object>>> snapshots() {
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
    @Idempotent(key = "breakpoint:clearSnapshots", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/snapshots")
    public BaseResponse<Void> clearSnapshots() {
        DefaultBreakpointHook hook = breakpointHookProvider.getIfAvailable();
        if (hook == null) {
            return BaseResponse.fail("断点调试器未启用");
        }
        hook.clearSnapshots();
        return BaseResponse.ok();
    }

    /**
     * 断点调试器状态概览
     *
     * @return 状态信息
     */
    @GetMapping("/status")
    public BaseResponse<Map<String, Object>> status() {
        DefaultBreakpointHook hook = breakpointHookProvider.getIfAvailable();
        if (hook == null) {
            return BaseResponse.fail("断点调试器未启用");
        }
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", hook.isEnabled());
        status.put("breakpointCount", hook.getBreakpoints().size());
        status.put("suspendedCount", hook.getSuspendedRules().size());
        status.put("snapshotCount", hook.getSnapshots().size());
        return BaseResponse.ok(status);
    }
}
