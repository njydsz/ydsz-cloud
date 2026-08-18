package com.njydsz.literule.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.excel.spring.ExcelWebSupport;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.literule.api.DecisionTableDefinition;
import com.njydsz.literule.api.spi.DecisionTableEvalProvider;
import com.njydsz.literule.infra.converter.LiteruleConverter;
import com.njydsz.literule.domain.dto.post.DecisionTablePostDTO;
import com.njydsz.literule.domain.entity.DecisionTable;
import com.njydsz.literule.domain.enums.LiteruleExceptionCode;
import com.njydsz.literule.domain.vo.DecisionTableDefinitionVO;
import com.njydsz.literule.domain.vo.DecisionTableVO;
import com.njydsz.literule.infra.mapper.DecisionTableMapper;
import com.njydsz.literule.server.config.DecisionTableAdminService;

/**
 * 决策表管理 Controller
 *
 * <p>业务背景：决策表（DMN 风格）是规则引擎的一种高级规则表达形式， 适合表达多条件组合的判定逻辑。运营人员可通过 Excel 在线/离线编辑决策表，
 * 由引擎在运行时根据事实数据匹配命中行并返回动作值。
 *
 * <p>核心能力：
 *
 * <ul>
 *   <li>决策表 CRUD
 *   <li>决策表评估（按 tableCode 加载并执行 DMN 评估）
 *   <li>Excel 导入/导出/模板下载
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
@Tag(name = "决策表管理", description = "决策表 CRUD、评估与 Excel 导入导出")
public class RuleDecisionTableController {

  /** 决策表 Mapper */
  private final DecisionTableMapper decisionTableMapper;

  /** 决策表管理服务（P0-3）：可选注入，未启用决策表时为空 */
  private final ObjectProvider<DecisionTableAdminService> decisionTableAdminServiceProvider;

  /** 决策表评估服务（SPI，由 project 模块提供实现） */
  private final DecisionTableEvalProvider decisionTableEvalProvider;

  /** Excel Web 导出支持（统一 HTTP 下载入口） */
  private final ExcelWebSupport excelWebSupport;

  /** 查询全部决策表 */
  @GetMapping("/decision-tables")
  public BaseResponse<List<DecisionTableVO>> listDecisionTables() {
    return BaseResponse.success(
        LiteruleConverter.INSTANT.decisionTableListToVO(decisionTableMapper.selectList(null)));
  }

  /** 查询单条决策表 */
  @GetMapping("/decision-tables/{tableCode}")
  public BaseResponse<DecisionTableVO> getDecisionTable(@PathVariable String tableCode) {
    DecisionTable dt =
        decisionTableMapper.selectOne(
            new LambdaQueryWrapper<DecisionTable>().eq(DecisionTable::getTableCode, tableCode));
    return BaseResponse.success(LiteruleConverter.INSTANT.entityToVO(dt));
  }

  /** 保存决策表 */
  @Idempotent(key = "ruleAdmin:saveDecisionTable", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'saveDecisionTable'")
  @RateLimit(resource = "literule.rule_decision_table.saveDecisionTable", threshold = 50)
  @PostMapping("/decision-tables")
  public BaseResponse<DecisionTableVO> saveDecisionTable(
      @Valid @RequestBody DecisionTablePostDTO dto) {
    DecisionTable decisionTable = LiteruleConverter.INSTANT.postDtoToEntity(dto);
    if (decisionTable.getId() != null) {
      decisionTableMapper.updateById(decisionTable);
    } else {
      decisionTableMapper.insert(decisionTable);
    }
    return BaseResponse.success(LiteruleConverter.INSTANT.entityToVO(decisionTable));
  }

  /** 删除决策表 */
  @Idempotent(key = "ruleAdmin:deleteDecisionTable", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'deleteDecisionTable'")
  @RateLimit(resource = "literule.rule_decision_table.deleteDecisionTable", threshold = 50)
  @DeleteMapping("/decision-tables/{id}")
  public BaseResponse<Void> deleteDecisionTable(@PathVariable String id) {
    decisionTableMapper.deleteById(id);
    return BaseResponse.success();
  }

  /**
   * 评估决策表
   *
   * <p>按 tableCode 加载已启用的决策表，以请求体中的 facts 作为事实数据执行 DMN 评估， 返回命中行的动作值列表（无命中时返回默认动作或空列表）。
   *
   * @param tableCode 决策表编码
   * @param facts 事实数据（变量名 -> 值）
   * @return 命中行的动作值列表
   */
  @Idempotent(key = "ruleAdmin:evaluateDecisionTable", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'postmapping'")
  @RateLimit(resource = "literule.rule_decision_table.evaluateDecisionTable", threshold = 50)
  @PostMapping("/decision-tables/{tableCode}/evaluate")
  public BaseResponse<List<Map<String, Object>>> evaluateDecisionTable(
      @PathVariable String tableCode, @RequestBody Map<String, Object> facts) {
    try {
      return BaseResponse.success(decisionTableEvalProvider.evaluate(tableCode, facts));
    } catch (Exception e) {
      log.warn("[DecisionTable] 评估失败: tableCode={}, err={}", tableCode, e.getMessage());
      return BaseResponse.error(e.getMessage());
    }
  }

  /**
   * 导出决策表为 Excel（P0-3）
   *
   * <p>将指定决策表导出为 .xlsx 文件，便于业务人员离线编辑或备份。
   *
   * @param tableCode 决策表编码
   * @return xlsx 文件流（Content-Type:
   *     application/vnd.openxmlformats-officedocument.spreadsheetml.sheet）
   */
  @GetMapping("/decision-tables/{tableCode}/export-excel")
  @AuthApiPermission(apiCodes = "execution:rule:view")
  public void exportDecisionTableExcel(
      @PathVariable String tableCode, HttpServletResponse response) {
    DecisionTableAdminService svc = decisionTableAdminServiceProvider.getIfAvailable();
    if (svc == null) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }
    try {
      byte[] bytes = svc.exportExcel(tableCode);
      excelWebSupport.writeBytes(response, bytes, tableCode + ".xlsx");
    } catch (IOException e) {
      log.warn("[DecisionTable] Excel 导出失败: tableCode={}, err={}", tableCode, e.getMessage());
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * 导入决策表 Excel（P0-3）
   *
   * <p>上传 .xlsx 文件，解析为决策表定义并保存。支持新增和更新（按 tableCode 覆盖）。
   *
   * @param file xlsx 文件（multipart/form-data）
   * @param operator 操作人
   * @return 保存后的决策表定义
   */
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'importDecisionTableExcel'")
  @RateLimit(resource = "literule.rule_decision_table.importDecisionTableExcel", threshold = 50)
  @PostMapping(value = "/decision-tables/import-excel", consumes = "multipart/form-data")
  @AuthApiPermission(apiCodes = "execution:rule:save")
  public BaseResponse<DecisionTableDefinitionVO> importDecisionTableExcel(
      @RequestParam("file") MultipartFile file,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    DecisionTableAdminService svc = decisionTableAdminServiceProvider.getIfAvailable();
    if (svc == null) {
      return BaseResponse.error(BaseResultCode.FORBIDDEN, "决策表管理服务未启用");
    }
    if (file == null || file.isEmpty()) {
      return BaseResponse.error(BaseResultCode.VALIDATION_FAILED, "上传文件不能为空");
    }
    try {
      byte[] bytes = file.getBytes();
      DecisionTableDefinition saved = svc.importExcel(bytes, operator);
      return BaseResponse.success(LiteruleConverter.INSTANT.entityToVO(saved));
    } catch (IllegalArgumentException e) {
      log.warn("[DecisionTable] Excel 导入失败: {}", e.getMessage());
      return BaseResponse.error(e.getMessage());
    } catch (IOException e) {
      log.warn("[DecisionTable] Excel 文件读取失败: {}", e.getMessage());
      return BaseResponse.error(LiteruleExceptionCode.DSL_PARSE_ERROR, "文件读取失败: " + e.getMessage());
    }
  }

  /**
   * 下载决策表 Excel 空白模板（P0-3）
   *
   * <p>返回预填充列结构的 .xlsx 模板，用户填写后通过 /import-excel 上传。
   *
   * @return xlsx 模板文件流
   */
  @GetMapping("/decision-tables/excel-template")
  @AuthApiPermission(apiCodes = "execution:rule:view")
  public void downloadDecisionTableExcelTemplate(HttpServletResponse response) {
    DecisionTableAdminService svc = decisionTableAdminServiceProvider.getIfAvailable();
    if (svc == null) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }
    try {
      byte[] bytes = svc.exportExcelTemplate();
      excelWebSupport.writeBytes(response, bytes, "decision-table-template.xlsx");
    } catch (IOException e) {
      log.warn("[DecisionTable] Excel 模板导出失败: err={}", e.getMessage());
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }
}
