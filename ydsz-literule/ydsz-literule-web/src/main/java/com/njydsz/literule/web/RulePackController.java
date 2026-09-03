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
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.literule.server.converter.LiteruleWebConverter;
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
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/literule/rules")
@RequiredArgsConstructor
@Validated
@Tag(name = "规则集市场", description = "规则集发布、安装、版本管理与压测")
public class RulePackController {

  /** 规则包服务（SPI，由 project 模块提供实现） */
  private final RulePackProvider rulePackProvider;

  /** 规则压测服务（P2-9）：可选注入，RuleAdminService 未装配时为空 */
  private final ObjectProvider<RuleStressTestService> ruleStressTestServiceProvider;

  /** 列出全部规则集（市场首页）
   * @return 规则集列表（按发布时间倒序）
   */
  @GetMapping("/packs")
  public YdszResponse<List<RulePackVO>> listPacks() {
    return YdszResponse.success(
        rulePackProvider.listAll().stream().map(LiteruleWebConverter.INSTANCE::entityToVO).toList());
  }

  /** 搜索规则集
   * @param keyword 搜索关键词（支持名称/描述模糊匹配）
   * @return 匹配的规则集列表
   */
  @GetMapping("/packs/search")
  public YdszResponse<List<RulePackVO>> searchPacks(
      @RequestParam(value = "keyword", required = false) String keyword) {
    return YdszResponse.success(
        rulePackProvider.search(keyword).stream()
            .map(LiteruleWebConverter.INSTANCE::entityToVO)
            .toList());
  }

  /** 查询规则集最新版本
   * @param packCode 规则集唯一编码
   * @return 最新版本规则集信息
   */
  @GetMapping("/packs/{packCode}/latest")
  public YdszResponse<RulePackVO> getLatestPack(@PathVariable String packCode) {
    return YdszResponse.success(
        LiteruleWebConverter.INSTANCE.entityToVO(rulePackProvider.getLatest(packCode)));
  }

  /** 查询规则集的所有版本
   * @param packCode 规则集唯一编码
   * @return 版本列表（按版本号倒序）
   */
  @GetMapping("/packs/{packCode}/versions")
  public YdszResponse<List<RulePackVO>> listPackVersions(@PathVariable String packCode) {
    return YdszResponse.success(
        rulePackProvider.listVersions(packCode).stream()
            .map(LiteruleWebConverter.INSTANCE::entityToVO)
            .toList());
  }

  /** 查询规则集指定版本（含规则定义快照，P2-8）
   * @param packCode 规则集唯一编码
   * @param version 版本号字符串
   * @return 指定版本的规则集信息
   */
  @GetMapping("/packs/{packCode}/versions/{version}")
  public YdszResponse<RulePackVO> getPackVersion(
      @PathVariable String packCode, @PathVariable String version) {
    return YdszResponse.success(
        LiteruleWebConverter.INSTANCE.entityToVO(rulePackProvider.getVersion(packCode, version)));
  }

  /** 知识包版本回滚（P2-8）：将该版本固化的规则定义整体恢复到在线规则表
   * @param packCode 规则集唯一编码
   * @param version 回滚目标版本号
   * @param operator 操作人用户名
   * @return 回滚安装结果
   */
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'rollbackPack'")
  @RateLimit(resource = "literule.rule_pack.rollbackPack", threshold = 50)
  @PostMapping("/packs/{packCode}/rollback")
  public YdszResponse<InstallResultVO> rollbackPack(
      @PathVariable String packCode,
      @RequestParam(value = "version") String version,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    return YdszResponse.success(
        LiteruleWebConverter.INSTANCE.entityToVO(
            rulePackProvider.rollback(packCode, version, operator)));
  }

  /** 知识包版本差异对比（P2-8）：对比两个版本规则编码与内容差异
   * @param packCode 规则集唯一编码
   * @param fromVersion 基准版本号
   * @param toVersion 目标版本号
   * @return 版本差异信息
   */
  @GetMapping("/packs/{packCode}/diff")
  public YdszResponse<PackDiffVO> diffPack(
      @PathVariable String packCode,
      @RequestParam(value = "from") String fromVersion,
      @RequestParam(value = "to") String toVersion) {
    return YdszResponse.success(
        LiteruleWebConverter.INSTANCE.entityToVO(
            rulePackProvider.diff(packCode, fromVersion, toVersion)));
  }

  /** 发布规则集到市场
   * @param pack 待发布的规则集信息
   * @param operator 操作人用户名
   * @return 发布后的规则集信息
   */
  @Idempotent(key = "ruleAdmin:publishPack", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'publishPack'")
  @RateLimit(resource = "literule.rule_pack.publishPack", threshold = 50)
  @PostMapping("/packs")
  public YdszResponse<RulePackVO> publishPack(
      @Valid @RequestBody RulePackVO pack,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    return YdszResponse.success(
        LiteruleWebConverter.INSTANCE.entityToVO(rulePackProvider.publish(pack, operator)));
  }

  /** 安装规则集（一键导入）

   * @param packCode 规则集唯一编码
   * @param version 指定版本号（为空则安装最新版本）
   * @param operator 操作人用户名
   * @return 安装结果（含成功/失败计数）
   */
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'installPack'")
  @RateLimit(resource = "literule.rule_pack.installPack", threshold = 50)
  @PostMapping("/packs/{packCode}/install")
  public YdszResponse<InstallResultVO> installPack(
      @PathVariable String packCode,
      @RequestParam(value = "version", required = false) String version,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    return YdszResponse.success(
        LiteruleWebConverter.INSTANCE.entityToVO(
            rulePackProvider.install(packCode, version, operator)));
  }

  /** 删除规则集
   * @param id 规则集唯一标识
   * @return 无返回内容
   */
  @Idempotent(key = "ruleAdmin:deletePack", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'deletePack'")
  @RateLimit(resource = "literule.rule_pack.deletePack", threshold = 50)
  @DeleteMapping("/packs/{id}")
  public YdszResponse<Void> deletePack(@PathVariable String id) {
    rulePackProvider.delete(id);
    return YdszResponse.success();
  }

  /** 标记为官方
   * @param id 规则集唯一标识
   * @param official 是否官方（true/false）
   * @return 无返回内容
   */
  @Idempotent(key = "ruleAdmin:markOfficialPack", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'markOfficialPack'")
  @RateLimit(resource = "literule.rule_pack.markOfficialPack", threshold = 50)
  @PutMapping("/packs/{id}/official")
  public YdszResponse<Void> markOfficialPack(
      @PathVariable String id,
      @RequestParam(value = "official", defaultValue = "true") boolean official) {
    rulePackProvider.markOfficial(id, official);
    return YdszResponse.success();
  }

  /** 评分（0-5）
   * @param id 规则集唯一标识
   * @param rating 评分值（0.0-5.0）
   * @return 无返回内容
   */
  @Idempotent(key = "ruleAdmin:ratePack", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'ratePack'")
  @RateLimit(resource = "literule.rule_pack.ratePack", threshold = 50)
  @PutMapping("/packs/{id}/rate")
  public YdszResponse<Void> ratePack(
      @PathVariable String id, @RequestParam(value = "rating") double rating) {
    rulePackProvider.rate(id, rating);
    return YdszResponse.success();
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
  public YdszResponse<RuleStressTestService.StressTestResult> stressTest(
      @RequestBody Map<String, Object> request) {
    RuleStressTestService svc = ruleStressTestServiceProvider.getIfAvailable();
    if (svc == null) {
      return YdszResponse.error(YdszResultCode.FORBIDDEN, "规则压测服务未启用");
    }
    String ruleCode = (String) request.get("ruleCode");
    if (ruleCode != null && ruleCode.isBlank()) {
      ruleCode = null;
    }
    List<Map<String, Object>> factsList = new ArrayList<>(16);
    Object rawList = request.get("factsList");
    if (rawList instanceof List<?> list) {
      for (Object item : list) {
        if (item instanceof Map<?, ?> rawMap) {
          Map<String, Object> facts = new HashMap<>(16);
          rawMap.forEach((k, v) -> facts.put(String.valueOf(k), v));
          factsList.add(facts);
        }
      }
    }
    int threads = toInt(request.get("threads"), 10);
    int iterations = toInt(request.get("iterations"), 1000);
    int warmupIterations = toInt(request.get("warmupIterations"), 100);
    if (factsList == null || factsList.isEmpty()) {
      return YdszResponse.error(YdszResultCode.VALIDATION_FAILED, "factsList 不能为空");
    }
    return YdszResponse.success(
        svc.run(ruleCode, factsList, threads, iterations, warmupIterations));
  }

  /** 安全转换为 int */
  private int toInt(Object v, int defaultValue) {
    if (v == null) {
      return defaultValue;
    }
    if (v instanceof Number n) {
      return n.intValue();
    }
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
  public YdszResponse<List<PackUpdateInfoVO>> checkPackUpdates() {
    return YdszResponse.success(
        rulePackProvider.checkPackUpdates().stream()
            .map(LiteruleWebConverter.INSTANCE::entityToVO)
            .toList());
  }

  /**
   * 批量更新知识包到最新版本
   *
   * @param packCodes 待更新的规则集编码列表
   * @param operator 操作人用户名
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
  public YdszResponse<List<InstallResultVO>> batchUpdatePacks(
      @RequestBody List<String> packCodes,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    if (packCodes == null || packCodes.isEmpty()) {
      return YdszResponse.success(List.of());
    }
    List<InstallResult> results = new ArrayList<>(16);
    for (String packCode : packCodes) {
      try {
        results.add(rulePackProvider.install(packCode, null, operator));
      } catch (Exception e) {
        log.warn("[RuleAdmin] 批量更新知识包失败: packCode={}, err={}", packCode, e.getMessage());
      }
    }
    return YdszResponse.success(
        results.stream().map(LiteruleWebConverter.INSTANCE::entityToVO).toList());
  }
}
