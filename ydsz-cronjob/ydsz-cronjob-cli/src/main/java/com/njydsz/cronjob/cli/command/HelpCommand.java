package com.njydsz.cronjob.cli.command;

import java.util.Map;

/**
 * 帮助命令（P2-7）。
 *
 * <p>显示所有可用命令及其用法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class HelpCommand implements CliCommand {

  /** 命令注册表 */
  private final Map<String, CliCommand> commands;

  /**
   * 构造帮助命令。
   *
   * @param commands 命令注册表
   */
  public HelpCommand(Map<String, CliCommand> commands) {
    this.commands = commands;
  }

  @Override
  public String name() {
    return "help";
  }

  @Override
  public String description() {
    return "显示帮助信息";
  }

  @Override
  public String usage() {
    return "help";
  }

  @Override
  public void execute(String[] args) {
    System.out.println("=== 可用命令 ===");
    for (CliCommand cmd : commands.values()) {
      System.out.printf("  %-10s %s%n", cmd.name(), cmd.description());
      System.out.printf("             用法: %s%n", cmd.usage());
    }
    System.out.println("\n  exit/quit   退出 CLI");
  }
}
