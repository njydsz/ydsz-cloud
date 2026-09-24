package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Prompt 模板编码配置属性。
 *
 * <p>绑定配置前缀 {@code ydzs.agent.prompt-template}，定义不同 Agent 编排模式下对应的
 * Prompt 模板编码映射。支持 REACT、Plan-Execute、Supervisor 等多种模式。
 * 默认启用（isEnabled=true），内置 DEFAULT/REACT/SUPERVISOR/PLAN_EXECUTE 等模板编码。
 *
 * @author ydsz
 * @since 26.09.24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromptTemplateProperties {
  private boolean isEnabled = true;
  private String defaultSystemCode = "DEFAULT_SYSTEM";
  private String reactSystemCode = "REACT_SYSTEM";
  private String planExecutePlanCode = "PLAN_EXECUTE_PLAN";
  private String planExecutePlanSystemCode = "PLAN_EXECUTE_PLAN_SYSTEM";
  private String planExecuteReplanCode = "PLAN_EXECUTE_REPLAN";
  private String supervisorPlanCode = "SUPERVISOR_PLAN";
  private String supervisorPlanSystemCode = "SUPERVISOR_PLAN_SYSTEM";
}
