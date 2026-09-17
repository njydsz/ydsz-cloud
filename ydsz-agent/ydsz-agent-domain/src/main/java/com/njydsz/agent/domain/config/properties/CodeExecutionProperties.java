package com.njydsz.agent.domain.config.properties;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeExecutionProperties {
  private static final int DEFAULT_TIMEOUT_SECONDS = 30;
  private static final int DEFAULT_MAX_OUTPUT_LENGTH = 5000;
  private static final String DEFAULT_DOCKER_MEMORY_LIMIT = "128m";
  private static final String DEFAULT_DOCKER_CPU_LIMIT = "0.5";

  private boolean isEnabled = false;
  private String mode = "docker";
  private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
  private int maxOutputLength = DEFAULT_MAX_OUTPUT_LENGTH;
  private String pythonPath = "python3";
  private List<String> allowedModules = List.of(
      "json", "math", "statistics", "itertools", "collections",
      "datetime", "functools", "operator", "re", "string");
  private String dockerImage = "python:3.12-alpine";
  private String dockerMemoryLimit = DEFAULT_DOCKER_MEMORY_LIMIT;
  private String dockerCpuLimit = DEFAULT_DOCKER_CPU_LIMIT;
}
