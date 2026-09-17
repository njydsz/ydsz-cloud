package com.njydsz.agent.infra.search;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.search.WebSearchService;

/**
 * HTTP Web 搜索服务实现
 *
 * <p>支持两种模式：
 * <ol>
 *   <li>内置 DuckDuckGo HTML 端点（免费，无 Key）</li>
 *   <li>自定义搜索 API（通过配置 provider / api-key / endpoint）</li>
 * </ol>
 *
 * <p>降级策略：搜索服务不可用时返回空列表，不影响 RAG 主流程。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@Component
public class HttpWebSearchService implements WebSearchService {

  /** DuckDuckGo HTML 搜索端点 */
  private static final String DUCKDUCKGO_HTML_ENDPOINT = "https://html.duckduckgo.com/html/";

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  private final AgentProperties properties;
  private final RestClient restClient;

  public HttpWebSearchService(AgentProperties properties, RestClient.Builder restClientBuilder) {
    this.properties = properties;
    this.restClient = restClientBuilder.build();
  }

  @Override
  public List<SearchResult> search(String query, int topK) {
    if (query == null || query.isBlank()) {
      return List.of();
    }

    String provider = properties.getWebSearch().getProvider();
    try {
      if ("custom".equals(provider)) {
        return searchCustom(query, topK);
      }
      return searchDuckDuckGo(query, topK);
    } catch (RestClientException e) {
      if (properties.getWebSearch().isDegradedOnFailure()) {
        log.warn("[WebSearch] 搜索服务不可用，降级返回空结果: provider={}, error={}", provider, e.getMessage());
        return List.of();
      }
      throw e;
    } catch (Exception e) {
      if (properties.getWebSearch().isDegradedOnFailure()) {
        log.warn("[WebSearch] 搜索异常，降级返回空结果: error={}", e.getMessage());
        return List.of();
      }
      throw e;
    }
  }

  @Override
  public boolean isAvailable() {
    return properties.getWebSearch().isEnabled();
  }

  /**
   * 使用 DuckDuckGo HTML 端点进行搜索
   *
   * @param query 搜索关键词
   * @param topK 返回结果数
   * @return 搜索结果列表
   */
  private List<SearchResult> searchDuckDuckGo(String query, int topK) {
    String html = restClient.post()
        .uri(DUCKDUCKGO_HTML_ENDPOINT)
        .header("User-Agent", "Mozilla/5.0 (compatible; ydsz-agent; WebSearch)")
        .body("q=" + query)
        .retrieve()
        .body(String.class);

    if (html == null || html.isEmpty()) {
      return List.of();
    }
    return parseDuckDuckGoHtml(html, topK);
  }

  /**
   * 使用自定义搜索 API 进行搜索
   *
   * @param query 搜索关键词
   * @param topK 返回结果数
   * @return 搜索结果列表
   */
  private List<SearchResult> searchCustom(String query, int topK) {
    AgentProperties.WebSearch config = properties.getWebSearch();
    String endpoint = config.getEndpoint();
    if (endpoint == null || endpoint.isBlank()) {
      log.warn("[WebSearch] 自定义搜索端点未配置 (ydsz.agent.web-search.endpoint)");
      return List.of();
    }

    String response = restClient.get()
        .uri(uriBuilder -> uriBuilder
            .path(endpoint)
            .queryParam("q", query)
            .queryParam("num", topK)
            .build())
        .header("Authorization", "Bearer " + config.getApiKey())
        .retrieve()
        .body(String.class);

    if (response == null || response.isEmpty()) {
      return List.of();
    }
    return parseCustomResponse(response, topK);
  }

  /**
   * 解析 DuckDuckGo HTML 搜索结果
   *
   * <p>从 HTML 中提取标题、摘要和链接。使用简单的正则/字符串匹配方式解析，
   * 避免引入额外的 HTML 解析依赖。
   *
   * @param html HTML 响应内容
   * @param topK 返回结果数上限
   * @return 搜索结果列表
   */
  private List<SearchResult> parseDuckDuckGoHtml(String html, int topK) {
    List<SearchResult> results = new ArrayList<>(COLLECTION_CAPACITY);

    // 解析 result__title 类名标记的搜索结果标题
    String titleMarker = "result__title";
    String snippetMarker = "result__snippet";
    String linkMarker = "result__url";

    int searchFrom = 0;
    while (results.size() < topK && searchFrom < html.length()) {
      int titleIdx = html.indexOf(titleMarker, searchFrom);
      if (titleIdx < 0) {
        break;
      }
      int titleStart = html.indexOf('>', titleIdx);
      if (titleStart < 0) {
        break;
      }
      titleStart++;
      int titleEnd = html.indexOf('<', titleStart);
      if (titleEnd < 0) {
        break;
      }
      String title = htmlDecode(html.substring(titleStart, titleEnd).trim());
      if (title.isEmpty()) {
        searchFrom = titleEnd;
        continue;
      }

      // 在标题附近查找摘要
      int snippetIdx = html.indexOf(snippetMarker, titleEnd);
      String snippet = "";
      if (snippetIdx >= 0 && snippetIdx - titleEnd < 2000) {
        int snippetStart = html.indexOf('>', snippetIdx);
        if (snippetStart >= 0) {
          snippetStart++;
          int snippetEnd = html.indexOf('<', snippetStart);
          if (snippetEnd > snippetStart) {
            snippet = htmlDecode(html.substring(snippetStart, snippetEnd).trim());
          }
        }
      }

      // 在标题附近查找链接
      String url = "";
      int linkIdx = html.indexOf(linkMarker, titleEnd);
      if (linkIdx >= 0 && linkIdx - titleEnd < 1000) {
        int urlStart = html.indexOf("href=\"", linkIdx);
        if (urlStart >= 0) {
          urlStart += 6;
          int urlEnd = html.indexOf("\"", urlStart);
          if (urlEnd > urlStart) {
            url = htmlDecode(html.substring(urlStart, urlEnd).trim());
          }
        }
        // 回退：如果没找到 href，查找 >标记后的普通URL
        if (url.isEmpty()) {
          int plainUrlStart = html.indexOf('>', linkIdx);
          if (plainUrlStart >= 0) {
            plainUrlStart++;
            int plainUrlEnd = html.indexOf('<', plainUrlStart);
            if (plainUrlEnd > plainUrlStart) {
              url = htmlDecode(html.substring(plainUrlStart, plainUrlEnd).trim());
            }
          }
        }
      }

      results.add(new SearchResult(title, snippet, url));
      searchFrom = titleEnd;
    }

    return results.isEmpty() ? List.of() : results;
  }

  /**
   * 解析自定义搜索 API 的响应（JSON 格式）
   *
   * <p>期望格式：{"results": [{"title": "...", "snippet": "...", "url": "..."}]}
   *
   * @param response JSON 响应字符串
   * @param topK 返回结果数上限
   * @return 搜索结果列表
   */
  private List<SearchResult> parseCustomResponse(String response, int topK) {
    // 使用 ydsz-common-json 解析（若有），否则返回空
    // 简化实现：尝试从 JSON 字符串中提取键值对
    try {
      List<SearchResult> results = new ArrayList<>(COLLECTION_CAPACITY);
      // 简单 JSON 数组搜索模式提取
      int resultsIdx = response.indexOf("[");
      if (resultsIdx < 0) {
        return List.of();
      }
      int depth = 0;
      int objStart = -1;
      for (int i = resultsIdx; i < response.length(); i++) {
        char c = response.charAt(i);
        if (c == '{') {
          if (depth == 0) {
            objStart = i;
          }
          depth++;
        } else if (c == '}') {
          depth--;
          if (depth == 0 && objStart >= 0) {
            String obj = response.substring(objStart, i + 1);
            String title = extractJsonString(obj, "title");
            String snippet = extractJsonString(obj, "snippet");
            String url = extractJsonString(obj, "url");
            if (title != null) {
              results.add(new SearchResult(title, snippet != null ? snippet : "", url != null ? url : ""));
            }
            if (results.size() >= topK) {
              break;
            }
            objStart = -1;
          }
        }
      }
      return results.isEmpty() ? List.of() : results;
    } catch (Exception e) {
      log.warn("[WebSearch] 解析自定义 API 响应失败: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * 从 JSON 对象字符串中提取指定键的字符串值
   *
   * @param json JSON 对象字符串
   * @param key 键名
   * @return 字符串值，未找到返回 null
   */
  private String extractJsonString(String json, String key) {
    String marker = "\"" + key + "\"";
    int keyIdx = json.indexOf(marker);
    if (keyIdx < 0) {
      return null;
    }
    int colonIdx = json.indexOf(':', keyIdx + marker.length());
    if (colonIdx < 0) {
      return null;
    }
    int quoteStart = json.indexOf('"', colonIdx);
    if (quoteStart < 0) {
      return null;
    }
    quoteStart++;
    int quoteEnd = json.indexOf('"', quoteStart);
    if (quoteEnd < 0) {
      return null;
    }
    return htmlDecode(json.substring(quoteStart, quoteEnd));
  }

  /**
   * 简易 HTML 实体解码
   *
   * @param text 包含 HTML 实体的文本
   * @return 解码后的纯文本
   */
  private String htmlDecode(String text) {
    if (text == null) {
      return "";
    }
    return text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
        .replace("&#8212;", "—")
        .replace("&#8211;", "–")
        .replace("&#8220;", "\"")
        .replace("&#8221;", "\"")
        .replace("&#8230;", "…")
        .replace("<b>", "").replace("</b>", "")
        .replace("<strong>", "").replace("</strong>", "")
        .replace("<em>", "").replace("</em>", "")
        .replace("<br>", "\n")
        .trim();
  }
}
