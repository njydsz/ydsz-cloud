package com.njydsz.agent.web.controller.skill;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.domain.dto.SkillExecutionRequestDTO;
import com.njydsz.agent.domain.skill.SkillDescriptor;
import com.njydsz.agent.domain.skill.SkillExecutionResult;
import com.njydsz.agent.domain.vo.SkillExecutionResponseVO;
import com.njydsz.agent.server.skill.SkillService;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.PermissionCodes;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * Skill Engine HTTP API 控制器。
 *
 * <p>提供 Skill 的发现、详情查询、执行和运维能力：
 *
 * <ul>
 *   <li>{@code GET /api/agent/skill/list} - 列出所有可用 Skill
 *   <li>{@code GET /api/agent/skill/{skillCode}} - 获取 Skill 详情（含描述/入参 schema）
 *   <li>{@code POST /api/agent/skill/execute} - 执行 Skill
 *   <li>{@code POST /api/agent/skill/sandbox/invalidate} - 清空沙箱缓存
 * </ul>
 *
 * <h3>架构位置</h3>
 *
 * <pre>
 *   前端 / 第三方系统
 *     → ydsz-gateway
 *       → ydsz-agent-web（本 Controller）
 *         → SkillService（应用服务）
 *           → SkillRegistry → SkillRuntime → 执行环境
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@ApiVersion("26.09.17")
@RestController
@RequestMapping("/agent/skill")
@RequiredArgsConstructor
@Tag(name = "Skill 管理", description = "Skill 发现 / 详情 / 执行 / 运维")
public class SkillController {

  /** Skill 管理服务 */
  private final SkillService skillService;

  /**
   * 列出所有已注册的 Skill。
   *
   * @return 统一响应结果，data 为 Skill 视图对象列表
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_EXECUTE)
  @Audit(
      module = "Skill管理",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'listSkills'")
  @GetMapping("/list")
  @Operation(summary = "列出所有可用 Skill", description = "返回所有已注册 Skill 的定义描述列表")
  public YdszResponse<List<SkillDescriptorVO>> listSkills() {
    log.info("[Skill-API] 查询 Skill 列表请求");
    List<SkillDescriptor> skills = skillService.listSkills();
    List<SkillDescriptorVO> vos = new ArrayList<>(skills.size());
    for (SkillDescriptor skill : skills) {
      vos.add(toSkillDescriptorVO(skill));
    }
    return YdszResponse.success(vos);
  }

  /**
   * 获取 Skill 详情。
   *
   * @param skillCode Skill 编码
   * @return 统一响应结果，data 为 Skill 详情视图
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_EXECUTE)
  @Audit(
      module = "Skill管理",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'getSkillDetail: ' + #skillCode")
  @GetMapping("/{skillCode}")
  @Operation(summary = "获取 Skill 详情",
      description = "根据 Skill 编码返回详细信息，包括描述、入参 Schema、脚本列表等")
  public YdszResponse<SkillDescriptorVO> getSkillDetail(
      @PathVariable @NotBlank String skillCode) {
    log.info("[Skill-API] 查询 Skill 详情请求: skillCode={}", skillCode);
    SkillDescriptor descriptor = skillService.getSkillDetail(skillCode);
    return YdszResponse.success(toSkillDescriptorVO(descriptor));
  }

  /**
   * 执行 Skill。
   *
   * @param request Skill 执行请求体
   * @return 统一响应结果，data 为执行结果视图
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_EXECUTE)
  @Audit(
      module = "Skill管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'executeSkill: ' + #request.skillCode")
  @Idempotent(key = "'skill:execute:' + #request.skillCode", ttlSeconds = 5)
  @PostMapping("/execute")
  @Operation(summary = "执行 Skill",
      description = "根据 Skill 编码执行对应 Skill，传入输入参数")
  public YdszResponse<SkillExecutionResponseVO> executeSkill(
      @Valid @RequestBody SkillExecutionRequestDTO request) {
    log.info("[Skill-API] 执行 Skill 请求: skillCode={}, timeoutMs={}",
        request.getSkillCode(), request.getTimeoutMs());
    SkillExecutionResult result = skillService.executeSkill(
        request.getSkillCode(),
        request.getInputParams(),
        request.getTimeoutMs());
    return YdszResponse.success(toExecutionResponseVO(result));
  }

  /**
   * 清空沙箱缓存（运维接口）。
   *
   * @return 统一响应结果
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_EXECUTE)
  @Audit(
      module = "Skill管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'invalidateSandbox'")
  @PostMapping("/sandbox/invalidate")
  @Operation(summary = "清空沙箱缓存",
      description = "运维手段：清理沙箱运行时的内部缓存（如预构建镜像层、中间产物等）")
  public YdszResponse<Void> invalidateSandbox() {
    log.info("[Skill-API] 清理沙箱缓存请求");
    skillService.invalidateSandbox();
    return YdszResponse.success();
  }

  // ==========================================================================
  // 内部方法
  // ==========================================================================

  /** SkillDescriptor → SkillDescriptorVO 转换。 */
  private SkillDescriptorVO toSkillDescriptorVO(SkillDescriptor descriptor) {
    SkillDescriptorVO vo = new SkillDescriptorVO();
    vo.setSkillCode(descriptor.skillCode());
    vo.setName(descriptor.name());
    vo.setDescription(descriptor.description());
    vo.setVersion(descriptor.version());
    vo.setSkillMd(descriptor.skillMd());
    vo.setScripts(descriptor.scripts());
    vo.setAssets(descriptor.assets());
    vo.setInputSchema(descriptor.inputSchema());
    vo.setMetadata(descriptor.metadata());
    vo.setTargetType(descriptor.targetType().getCode());
    vo.setIsSandboxRequired(descriptor.isSandboxRequired());
    return vo;
  }

  /** SkillExecutionResult → SkillExecutionResponseVO 转换。 */
  private SkillExecutionResponseVO toExecutionResponseVO(SkillExecutionResult result) {
    SkillExecutionResponseVO vo = new SkillExecutionResponseVO();
    vo.setSkillCode(result.skillCode());
    vo.setSuccess(result.isSuccess());
    vo.setStdout(result.stdout());
    vo.setStderr(result.stderr());
    vo.setOutputFiles(result.outputFiles());
    vo.setMetrics(result.metrics());
    vo.setErrorMessage(result.errorMessage());
    vo.setCompletedAt(result.completedAt());
    return vo;
  }

  // ==========================================================================
  // 内部视图对象
  // ==========================================================================

  /**
   * Skill 定义视图对象（对外展示用）。
   *
   * <p>避免直接暴露 SkillDescriptor record 的内部结构，提供独立的可序列化视图类型。
   */
  @Data
  public static class SkillDescriptorVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Skill 编码 */
    private String skillCode;

    /** 可读名称 */
    private String name;

    /** 用途描述 */
    private String description;

    /** 版本号 */
    private String version;

    /** SKILL.md 内容 */
    private String skillMd;

    /** 脚本路径列表 */
    private List<String> scripts;

    /** 资源路径列表 */
    private List<String> assets;

    /** 入参 JSON Schema */
    private Map<String, Object> inputSchema;

    /** 扩展元数据 */
    private Map<String, String> metadata;

    /** 执行目标类型（local / sandbox） */
    private String targetType;

    /** 是否需要沙箱隔离执行 */
    private boolean isSandboxRequired;
  }
}
