package com.njydsz.agent.domain.config.properties;

import java.util.List;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpProperties {
  private boolean isEnabled = true;
  private List<McpServerEntry> servers;
  private Integer defaultTimeout;
  private boolean isServerEnabled = false;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class McpServerEntry {
    private String name;
    private String transportType;
    private String url;
    private Integer timeout;
    private boolean isEnabled = true;
    private String authType = "none";
    private String authApiKey;
    private String authToken;
    private String authClientId;
    private String authClientSecret;
    private String authTokenUrl;
    private String command;
    private List<String> args;
    private java.util.Map<String, String> envVars;
  }
}
