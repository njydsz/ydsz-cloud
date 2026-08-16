package com.njydsz.literule.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.literule.api.RulePack;
import com.njydsz.literule.domain.converter.LiteruleConverter;
import com.njydsz.literule.domain.vo.InstallResultVO;
import com.njydsz.literule.domain.vo.PackDiffVO;
import com.njydsz.literule.domain.vo.PackUpdateInfoVO;
import com.njydsz.literule.domain.vo.RulePackVO;
import com.njydsz.literule.server.benchmark.RuleStressTestService;
import com.njydsz.literule.server.spi.RulePackProvider;
import com.njydsz.literule.server.spi.RulePackProvider.InstallResult;

/**
 * 规则集市场 Controller
 *
 * <p>业务背景：规则集（Pack）是将一组相关规则打包发布的单元，支持版本管理、 一键安装、版本对比、回滚等能力。规则集市场提供官方/社区规则集的浏览、
 * 搜索、安装、评分等能力，类似应用市场。同时集成规则压测工具，用于规则 变更前的性能回归验证。
 *
 * <p>核心能力：
 *
 * <ul>
 *   <li>规则集市场：列表、搜索、版本管理、安装、回滚、差异对比
 *   <li>规则集管理：发布、删除、标记官方、评分
 *   <li>规则压测：线程池并发 Dry-run，统计 QPS、P50/P95/P99、错误率
 *   <li>知识包更新提醒：检查已安装包的版本更新并批量升级
 * </ul>
 *
 * <p>从 {@link RuleAdminController} 拆分而来，与原文件共享基路径 {@code /ruleEngine/rules}，所有端点 URL 保持不变。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/rule-engine/rules")
@RequiredArgsConstructor
@Validated
@Tag(name = "规则集市场", description = "规则集发布、安装、版本管理与压测")
public class RulePackController {

  /** 规则包服务（SPI，由 project 模块提供实现） */
  private final RulePackProvider rulePackProvider;

  /** 规则压测服务（P2-9）：可选注入，RuleAdminService 未装配时为空 */
  private final ObjectProvider<RuleStressTestService> ruleStressTestServiceProvider;

  /** 列出全部规则集（市场首页） */
  @GetMapping("/packs")
  public BaseResponse<List<RulePackVO>> listPacks() {
    return BaseResponse.success(
        rulePackProvider.listAll().stream().map(LiteruleConverter.INSTANT::entityToVO).toList());
  }

  /** 搜索规则集 */
  @GetMapping("/packs/search")
  public BaseResponse<List<RulePackVO>> searchPacks(
      @RequestParam(value = "keyword", required = false) String keyword) {
    return BaseResponse.success(
        rulePackProvider.search(keyword).stream()
            .map(LiteruleConverter.INSTANT::entityToVO)
            .toList());
  }

  /** 查询规则集最新版本 */
  @GetMapping("/packs/{packCode}/latest")
  public BaseResponse<RulePackVO> getLatestPack(@PathVariable String packCode) {
    return BaseResponse.success(
        LiteruleConverter.INSTANT.entityToVO(rulePackProvider.getLatest(packCode)));
  }

  /** 查询规则集的所有版本 */
  @GetMapping("/packs/{packCode}/versions")
  public BaseResponse<List<RulePackVO>> listPackVersions(@PathVariable String packCode) {
    return BaseResponse.success(
        rulePackProvider.listVersions(packCode).stream()
            .map(LiteruleConverter.INSTANT::entityToVO)
            .toList());
  }

  /** 查询规则集指定版本（含规则定义快照，P2-8） */
  @GetMapping("/packs/{packCode}/versions/{version}")
  public BaseResponse<RulePackVO> getPackVersion(
      @PathVariable String packCode, @PathVariable String version) {
    return BaseResponse.success(
        LiteruleConverter.INSTANT.entityToVO(rulePackProvider.getVersion(packCode, version)));
  }

  /** 知识包版本回滚（P2-8）：将该版本固化的规则定义整体恢复到在线规则表 */
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'rollbackPack'")
  @RateLimit(resource = "literule.rule_pack.rollbackPack", threshold = 50)
  @PostMapping("/packs/{packCode}/rollback")
  public BaseResponse<InstallResultVO> rollbackPack(
      @PathVariable String packCode,
      @RequestParam(value = "version") String version,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    return BaseResponse.success(
        LiteruleWebConverter.INSTANT.entityToVO(
            rulePackProvider.rollback(packCode, version, operator)));
  }

  /** 知识包版本差异对比（P2-8）：对比两个版本规则编码与内容差异 */
  @GetMapping("/packs/{packCode}/diff")
  public BaseResponse<PackDiffVO> diffPack(
      @PathVariable String packCode,
      @RequestParam(value = "from") String fromVersion,
      @RequestParam(value = "to") String toVersion) {
    return BaseResponse.success(
        LiteruleWebConverter.INSTANT.entityToVO(
            rulePackProvider.diff(packCode, fromVersion, toVersion)));
  }

  /** 发布规则集到市场 */
  @Idempotent(key = "ruleAdmin:publishPack", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'publishPack'")
  @RateLimit(resource = "literule.rule_pack.publishPack", threshold = 50)
  @PostMapping("/packs")
  public BaseResponse<RulePackVO> publishPack(
      @Valid @RequestBody RulePack pack,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    return BaseResponse.success(
        LiteruleConverter.INSTANT.entityToVO(rulePackProvider.publish(pack, operator)));
  }

  /** 安装规则集（一键导入） */
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'installPack'")
  @RateLimit(resource = "literule.rule_pack.installPack", threshold = 50)
  @PostMapping("/packs/{packCode}/install")
  public BaseResponse<InstallResultVO> installPack(
      @PathVariable String packCode,
      @RequestParam(value = "version", required = false) String version,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    return BaseResponse.success(
        LiteruleWebConverter.INSTANT.entityToVO(
            rulePackProvider.install(packCode, version, operator)));
  }

  /** 删除规则集 */
  @Idempotent(key = "ruleAdmin:deletePack", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'deletePack'")
  @RateLimit(resource = "literule.rule_pack.deletePack", threshold = 50)
  @DeleteMapping("/packs/{id}")
  public BaseResponse<Void> deletePack(@PathVariable String id) {
    rulePackProvider.delete(id);
    return BaseResponse.success();
  }

  /** 标记为官方 */
  @Idempotent(key = "ruleAdmin:markOfficialPack", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'markOfficialPack'")
  @RateLimit(resource = "literule.rule_pack.markOfficialPack", threshold = 50)
  @PutMapping("/packs/{id}/official")
  public BaseResponse<Void> markOfficialPack(
      @PathVariable String id,
      @RequestParam(value = "official", defaultValue = "true") boolean official) {
    rulePackProvider.markOfficial(id, official);
    return BaseResponse.success();
  }

  /** 评分（0-5） */
  @Idempotent(key = "ruleAdmin:ratePack", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'ratePack'")
  @RateLimit(resource = "literule.rule_pack.ratePack", threshold = 50)
  @PutMapping("/packs/{id}/rate")
  public BaseResponse<Void> ratePack(
      @PathVariable String id, @RequestParam(value = "rating") double rating) {
    rulePackProvider.rate(id, rating);
    return BaseResponse.success();
  }

  /**
   * 规则压测
   *
   * <p>使用线程池并发执行 Dry-run，统计 QPS、P50/P95/P99 耗时、错误率等指标， 用于规则变更前的性能回归验证与容量评估。
   *
   * <p>请求体示例：
   *
   * <pre>
   * POST /rules/stress-test
   * {
   *   "ruleCode": null,
   *   "factsList": [{"budgetUsedRatio":0.95}, {"budgetUsedRatio":0.5}],
   *   "threads": 10,
   *   "iterations": 1000,
   *   "warmupIterations": 100
   * }
   * </pre>
   *
   * @param request 压测请求
   * @return 压测结果（含 QPS、分位数耗时、错误率、直方图）
   */
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'stressTest'")
  @RateLimit(resource = "literule.rule_pack.stressTest", threshold = 50)
  @PostMapping("/stress-test")
  @Operation(summary = "规则压测", description = "使用线程池并发执行 Dry-run，统计 QPS、P50/P95/P99 耗时、错误率")
  public BaseResponse<RuleStressTestService.StressTestResult> stressTest(
      @RequestBody Map<String, Object> request) {
    RuleStressTestService svc = ruleStressTestServiceProvider.getIfAvailable();
    if (svc == null) {
      return BaseResponse.error(BaseResultCode.FORBIDDEN, "规则压测服务未启用");
    }
    String ruleCode = (String) request.get("ruleCode");
    if (ruleCode != null && ruleCode.isBlank()) ruleCode = null;
    List<Map<String, Object>> factsList = new ArrayList<>();
    Object rawList = request.get("factsList");
    if (rawList instanceof List<?> list) {
      for (Object item : list) {
        if (item instanceof Map<?, ?> rawMap) {
          Map<String, Object> facts = new HashMap<>();
          rawMap.forEach((k, v) -> facts.put(String.valueOf(k), v));
          factsList.add(facts);
        }
      }
    }
    int threads = toInt(request.get("threads"), 10);
    int iterations = toInt(request.get("iterations"), 1000);
    int warmupIterations = toInt(request.get("warmupIterations"), 100);
    if (factsList == null || factsList.isEmpty()) {
      return BaseResponse.error(BaseResultCode.VALIDATION_FAILED, "factsList 不能为空");
    }
    return BaseResponse.success(
        svc.run(ruleCode, factsList, threads, iterations, warmupIterations));
  }

  /** 安全转换为 int */
  private int toInt(Object v, int defaultValue) {
    if (v == null) return defaultValue;
    if (v instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(v.toString());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * 检查已安装知识包的版本更新
   *
   * <p>查询当前租户已安装的知识包列表，对比每个包的已安装版本与市场最新版本， 返回有更新可用的包列表。
   *
   * @return 更新检查结果列表
   */
  @GetMapping("/packs/update-check")
  @Operation(summary = "知识包更新检查", description = "对比已安装知识包与市场最新版本，返回有更新的包列表")
  public BaseResponse<List<PackUpdateInfoVO>> checkPackUpdates() {
    return BaseResponse.success(
        rulePackProvider.checkPackUpdates().stream()
            .map(LiteruleWebConverter.INSTANT::entityToVO)
            .toList());
  }

  /**
   * 批量更新知识包到最新版本
   *
   * @param operator 操作人
   * @return 每个包的更新结果
   */
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'batchUpdatePacks'")
  @RateLimit(resource = "literule.rule_pack.batchUpdatePacks", threshold = 50)
  @PostMapping("/packs/batch-update")
  @Operation(summary = "批量更新知识包", description = "将指定知识包列表更新到最新版本")
  public BaseResponse<List<InstallResultVO>> batchUpdatePacks(
      @RequestBody List<String> packCodes,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    if (packCodes == null || packCodes.isEmpty()) {
      return BaseResponse.success(List.of());
    }
    List<InstallResult> results = new ArrayList<>();
    for (String packCode : packCodes) {
      try {
        results.add(rulePackProvider.install(packCode, null, operator));
      } catch (Exception e) {
        log.warn("[RuleAdmin] 批量更新知识包失败: packCode={}, err={}", packCode, e.getMessage());
      }
    }
    return BaseResponse.success(
        results.stream().map(LiteruleWebConverter.INSTANT::entityToVO).toList());
  }
}
