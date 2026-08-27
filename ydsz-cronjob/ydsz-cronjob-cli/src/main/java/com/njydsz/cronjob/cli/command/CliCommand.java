package com.njydsz.cronjob.cli.command;

/**
 * CLI 命令接口（P2-7）。
 *
 * <p>所有运维命令实现此接口，通过 {@link #execute} 执行具体操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface CliCommand {

  /**
   * 命令名称（用于注册和帮助显示）。
   *
   * @return 命令名称
   */
  String name();

  /**
   * 命令描述（用于帮助显示）。
   *
   * @return 命令描述
   */
  String description();

  /**
   * 命令用法说明。
   *
   * @return 用法字符串
   */
  String usage();

  /**
   * 执行命令。
   *
   * @param args 命令参数
   * @throws Exception 执行异常
   */
  void execute(String[] args) throws Exception;
}
