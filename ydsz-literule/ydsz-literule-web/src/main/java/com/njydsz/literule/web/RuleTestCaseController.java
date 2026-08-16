package com.njydsz.literule.web;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.literule.api.RuleResult;
import com.njydsz.literule.api.dto.TestCaseBatchRunDTO;
import com.njydsz.literule.domain.converter.LiteruleConverter;
import com.njydsz.literule.domain.dto.post.RuleTestCasePostDTO;
import com.njydsz.literule.domain.entity.RuleTestCaseDO;
import com.njydsz.literule.domain.vo.RuleTestCaseVO;
import com.njydsz.literule.infra.mapper.RuleTestCaseMapper;
import com.njydsz.literule.server.config.RuleAdminService;

/**
 * 规则测试用例 Controller
 *
 * <p>业务背景：规则引擎在迭代过程中需要回归测试保障，测试用例保存
 * 输入事实快照与预期触发规则集合，CI 通过批量执行接口对比实际触发结果，
 * 判定规则变更是否引入回归。
 *
 * <p>核心能力：
 * <ul>
 *   <li>测试用例 CRUD</li>
 *   <li>批量执行测试用例（回归测试），输出通过率报告</li>
 * </ul>
 *
 * <p>从 {@link RuleAdminController} 拆分而来，与原文件共享基路径
 * {@code /ruleEngine/rules}，所有端点 URL 保持不变。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/rule-engine/rules")
@RequiredArgsConstructor
@Validated
@Tag(name = "规则测试用例", description = "规则回归测试用例管理与批量执行")
public class RuleTestCaseController {

    /** 规则测试用例 Mapper */
    private final RuleTestCaseMapper ruleTestCaseMapper;
    /** 规则管理服务 */
    private final RuleAdminService ruleAdminService;

    /**
     * 查询测试用例（可选按规则编码过滤）
     *
     * @param ruleCode 规则编码（可选）
     * @return 测试用例列表
     */
    @GetMapping("/test-cases")
    public BaseResponse<List<RuleTestCaseVO>> listTestCases(@RequestParam(required = false) String ruleCode) {
        LambdaQueryWrapper<RuleTestCaseDO> wrapper = new LambdaQueryWrapper<>();
        if (ruleCode != null && !ruleCode.isBlank()) {
            wrapper.eq(RuleTestCaseDO::getRuleCode, ruleCode);
        }
        wrapper.orderByDesc(RuleTestCaseDO::getUpdatedAt);
        return BaseResponse.success(LiteruleConverter.INSTANT.ruleTestCaseListToVO(ruleTestCaseMapper.selectList(wrapper)));
    }

    /**
     * 保存测试用例
     *
     * @param testCase 测试用例
     * @return 保存后的测试用例
     */
    @Idempotent(key = "ruleAdmin:saveTestCase", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'saveTestCase'")
    @RateLimit(resource = "literule.rule_test_case.saveTestCase", threshold = 50)
    @PostMapping("/test-cases")
    public BaseResponse<RuleTestCaseVO> saveTestCase(@Valid @RequestBody RuleTestCasePostDTO dto) {
        RuleTestCaseDO testCase = LiteruleConverter.INSTANT.postDtoToEntity(dto);
        if (testCase.getId() != null) {
            ruleTestCaseMapper.updateById(testCase);
        } else {
            ruleTestCaseMapper.insert(testCase);
        }
        return BaseResponse.success(LiteruleConverter.INSTANT.entityToVO(testCase));
    }

    /**
     * 删除测试用例
     *
     * @param id 测试用例 ID
     * @return 操作结果
     */
    @Idempotent(key = "ruleAdmin:deleteTestCase", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'deleteTestCase'")
    @RateLimit(resource = "literule.rule_test_case.deleteTestCase", threshold = 50)
    @DeleteMapping("/test-cases/{id}")
    public BaseResponse<Void> deleteTestCase(@PathVariable String id) {
        ruleTestCaseMapper.deleteById(id);
        return BaseResponse.success();
    }

    /**
     * 批量执行测试用例（回归测试）
     *
     * <p>对每个测试用例执行 dry-run，对比实际触发规则与预期触发规则，
     * 返回通过率报告。支持 CI 集成：当 anyFail=true 时 HTTP 状态码仍为 200，
     * CI 脚本通过 response body 中的 passRate 判断是否阻断流水线。
     *
     * @param request 请求体，包含 ids（测试用例 ID 列表，为空则执行全部）
     * @return 回归测试报告（含每个用例的 pass/fail + 通过率统计）
     */
    @Idempotent(key = "ruleAdmin:batchRunTestCases", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'postmapping'")
    @RateLimit(resource = "literule.rule_test_case.batchRunTestCases", threshold = 50)
    @PostMapping("/test-cases/batch-run")
    public BaseResponse<Map<String, Object>> batchRunTestCases(@Valid @RequestBody TestCaseBatchRunDTO dto) {
        List<Long> ids = dto.getIds();

        List<RuleTestCaseDO> testCases;
        if (ids == null || ids.isEmpty()) {
            // 执行全部测试用例
            testCases = ruleTestCaseMapper.selectList(null);
        } else {
            testCases = ids.stream()
                .map(ruleTestCaseMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        }

        if (testCases.isEmpty()) {
            return BaseResponse.success(Map.of("total", 0, "passed", 0, "failed", 0, "passRate", "100%"));
        }

        List<Map<String, Object>> caseResults = new ArrayList<>();
        int passed = 0;
        int failed = 0;

        for (RuleTestCaseDO tc : testCases) {
            List<RuleResult> results = ruleAdminService.dryRun(null, tc.getFactsData());

            // 获取实际触发的规则编码集合
            Set<String> actualTriggered = results.stream()
                .map(RuleResult::getRuleCode)
                .collect(Collectors.toSet());

            // 获取预期触发的规则编码集合
            Set<String> expectedTriggered = new HashSet<>();
            if (tc.getExpectedTriggered() != null) {
                expectedTriggered.addAll(tc.getExpectedTriggered());
            }

            // 对比
            boolean isPass = actualTriggered.equals(expectedTriggered);
            if (isPass) {
                passed++;
            } else {
                failed++;
            }

            Set<String> missing = new LinkedHashSet<>(expectedTriggered);
            missing.removeAll(actualTriggered);

            Set<String> unexpected = new LinkedHashSet<>(actualTriggered);
            unexpected.removeAll(expectedTriggered);

            Map<String, Object> caseResult = new LinkedHashMap<>();
            caseResult.put("testCaseId", tc.getId());
            caseResult.put("testCaseName", tc.getName());
            caseResult.put("ruleCode", tc.getRuleCode());
            caseResult.put("pass", isPass);
            caseResult.put("expectedTriggered", expectedTriggered);
            caseResult.put("actualTriggered", actualTriggered);
            caseResult.put("missing", missing);
            caseResult.put("unexpected", unexpected);
            caseResult.put("results", results);
            caseResults.add(caseResult);
        }

        double passRate = (double) passed / testCases.size() * 100;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("total", testCases.size());
        report.put("passed", passed);
        report.put("failed", failed);
        report.put("passRate", String.format("%.1f%%", passRate));
        report.put("allPassed", failed == 0);
        report.put("caseResults", caseResults);

        return BaseResponse.success(report);
    }
}
