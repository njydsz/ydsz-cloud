package com.njydsz.message.web.controller.receipt;

import java.io.IOException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.message.server.service.receipt.ReadReceiptService;

/**
 * 已读回执 Controller。
 *
 * <p>提供短信短链跳转 HTTP 回调端点，用于无登录态场景下收集用户对消息的「点击」行为数据。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/readReceipt/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>短信短链跳转</b>：{@code GET /s/{shortCode}} — 短信中插入短链（避免长 URL 占用字符），
 *       用户点击时 302 重定向到原始 URL，同时标记消息为「已点击」
 * </ul>
 *
 * <p><b>与 ReadStatusController 的区别：</b>
 *
 * <ul>
 *   <li>本 Controller：<b>无登录态</b>，由短信短链端点被动触发
 *   <li>ReadStatusController：<b>有登录态</b>，由用户在站内通知中心主动操作（markRead 等）
 * </ul>
 *
 * <p><b>短链降级：</b>当 {@code handleShortLinkClick} 返回 {@code null}（短链不存在或已过期），
 * 返回 HTTP 404 提示用户，而非重定向到错误页，避免引入外部跳转风险。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@Tag(name = "已读回执", description = "短信短链回调")
@RestController
@RequestMapping("/api/v1/message/readReceipt")
@RequiredArgsConstructor
public class ReadReceiptController {

  /** 已读回执服务 */
  private final ReadReceiptService readReceiptService;

  /**
   * 短链跳转端点。
   *
   * <p>用户点击短信中的短链时，标记消息已读并 302 重定向到原始 URL。
   *
   * @param shortCode 短链 code
   * @param response HTTP 响应
   */
  @Operation(summary = "短链跳转")
  @GetMapping("/s/{shortCode}")
  public void shortLinkRedirect(@PathVariable String shortCode, HttpServletResponse response) {
    String originalUrl = readReceiptService.handleShortLinkClick(shortCode);
    if (originalUrl != null) {
      try {
        response.sendRedirect(originalUrl);
      } catch (IOException e) {
        log.warn("[ReadReceipt] 重定向失败: {}", e.getMessage());
      }
    } else {
      response.setStatus(404);
    }
  }
}
