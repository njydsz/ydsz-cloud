package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
