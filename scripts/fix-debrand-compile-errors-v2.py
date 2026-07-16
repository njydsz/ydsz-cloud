#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""修复剩余编译错误 - PgSearchEngine line 373 和 OpenAI 客户端。"""

import pathlib

ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis")


def fix_pg_search_engine_line_373():
    """修复 PgSearchEngine.java line 373 的 ESCAPE 子句。

    原代码（错误）:
        " WHERE title ILIKE ? ESCAPE \\"\\" ORDER BY title LIMIT ?"
    应为:
        " WHERE title ILIKE ? ESCAPE '\\\\' ORDER BY title LIMIT ?"
    """
    f = ROOT / "ydsz-backend/ydsz-common/ydsz-common-search/src/main/java/com/njydsz/common/search/engine/pg/PgSearchEngine.java"
    content = f.read_text(encoding="utf-8")

    # 文件中的字面内容是: " WHERE title ILIKE ? ESCAPE \"\"" ORDER BY title LIMIT ?"
    # 用 Python 字符串匹配，需要用 r"..." 或转义反斜杠
    old = '" WHERE title ILIKE ? ESCAPE \\"\\" ORDER BY title LIMIT ?"'
    new = '" WHERE title ILIKE ? ESCAPE \\\\\\\\ ORDER BY title LIMIT ?"'

    # 在 Python 字符串中:
    # old 的字面值: " WHERE title ILIKE ? ESCAPE \"\"" ORDER BY title LIMIT ?"
    # new 的字面值: " WHERE title ILIKE ? ESCAPE \\ ORDER BY title LIMIT ?"
    # 在 Java 源码中 \\ 表示一个反斜杠字符

    if old in content:
        content = content.replace(old, new, 1)
        f.write_text(content, encoding="utf-8")
        print(f"[OK] {f.name}: 修复 ESCAPE 子句")
    else:
        # 尝试另一种模式
        old2 = 'WHERE title ILIKE ? ESCAPE ""'
        new2 = 'WHERE title ILIKE ? ESCAPE \\\\\\\\'
        if old2 in content:
            content = content.replace(old2, new2, 1)
            f.write_text(content, encoding="utf-8")
            print(f"[OK] {f.name}: 修复 ESCAPE 子句 (模式 2)")
        else:
            print(f"[WARN] {f.name}: 未找到 ESCAPE 模式")
            # 打印实际内容用于调试
            lines = content.split('\n')
            for i, line in enumerate(lines, 1):
                if 'ESCAPE' in line:
                    print(f"  Line {i}: {repr(line)}")


def fix_openai_compatible_client():
    """修复 OpenAICompatibleClient.java - 把 Map<String, Object> 改为 JsonObject。"""
    f = ROOT / "ydsz-backend/ydsz-agent/ydsz-agent-infra/src/main/java/com/njydsz/agent/infra/llm/OpenAICompatibleClient.java"
    content = f.read_text(encoding="utf-8")

    # 添加 import
    if "import com.njydsz.common.json.object.JsonArray;" not in content:
        content = content.replace(
            "import com.njydsz.common.json.Json;",
            "import com.njydsz.common.json.Json;\nimport com.njydsz.common.json.object.JsonArray;\nimport com.njydsz.common.json.object.JsonObject;",
            1,
        )

    # parseResponse 方法
    replacements = [
        # parseResponse 方法
        (
            """    private ChatResponse parseResponse(String json) {
        Map<String, Object> obj = Json.parseMap(json);
        String id = obj.getString("id");
        String model = obj.getString("model");
        List<Object> choices = obj.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new LlmException("LLM 响应无 choices", LlmException.ErrorType.INVALID_RESPONSE);
        }
        Map<String, Object> choice = choices.getJSONObject(0);
        Map<String, Object> message = choice.getJSONObject("message");
        String finishReason = choice.getString("finish_reason");
        String content = message != null ? message.getString("content") : null;

        List<ToolCall> toolCalls = new ArrayList<>();
        if (message != null && message.containsKey("tool_calls")) {
            List<Object> calls = message.getJSONArray("tool_calls");
            for (int i = 0; i < calls.size(); i++) {
                Map<String, Object> call = calls.getJSONObject(i);
                String callId = call.getString("id");
                Map<String, Object> function = call.getJSONObject("function");
                String name = function.getString("name");
                String argsStr = function.getString("arguments");
                Map<String, Object> args = Json.toObject(argsStr, Map.class);
                toolCalls.add(new ToolCall(callId, name, args));
            }
        }

        TokenUsage usage = TokenUsage.zero();
        if (obj.containsKey("usage")) {
            Map<String, Object> usageObj = obj.getJSONObject("usage");
            usage = new TokenUsage(
                    usageObj.getIntValue("prompt_tokens", 0),
                    usageObj.getIntValue("completion_tokens", 0));
        }""",
            """    private ChatResponse parseResponse(String json) {
        JsonObject obj = Json.parseObjectToJsonObject(json);
        String id = obj.getString("id");
        String model = obj.getString("model");
        JsonArray choices = obj.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new LlmException("LLM 响应无 choices", LlmException.ErrorType.INVALID_RESPONSE);
        }
        JsonObject choice = choices.getJSONObject(0);
        JsonObject message = choice.getJSONObject("message");
        String finishReason = choice.getString("finish_reason");
        String content = message != null ? message.getString("content") : null;

        List<ToolCall> toolCalls = new ArrayList<>();
        if (message != null && message.containsKey("tool_calls")) {
            JsonArray calls = message.getJSONArray("tool_calls");
            for (int i = 0; i < calls.size(); i++) {
                JsonObject call = calls.getJSONObject(i);
                String callId = call.getString("id");
                JsonObject function = call.getJSONObject("function");
                String name = function.getString("name");
                String argsStr = function.getString("arguments");
                Map<String, Object> args = Json.toObject(argsStr, Map.class);
                toolCalls.add(new ToolCall(callId, name, args));
            }
        }

        TokenUsage usage = TokenUsage.zero();
        if (obj.containsKey("usage")) {
            JsonObject usageObj = obj.getJSONObject("usage");
            usage = new TokenUsage(
                    usageObj.getIntValue("prompt_tokens", 0),
                    usageObj.getIntValue("completion_tokens", 0));
        }""",
        ),
        # parseChunk 方法
        (
            """    private ChatChunk parseChunk(String data) {
        try {
            Map<String, Object> obj = Json.parseMap(data);
            String id = obj.getString("id");
            String model = obj.getString("model");
            List<Object> choices = obj.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            Map<String, Object> choice = choices.getJSONObject(0);
            Map<String, Object> delta = choice.getJSONObject("delta");
            String finishReason = choice.getString("finish_reason");
            String content = delta != null ? delta.getString("content") : null;

            TokenUsage usage = null;
            if (obj.containsKey("usage") && obj.get("usage") != null) {
                Map<String, Object> usageObj = obj.getJSONObject("usage");
                usage = new TokenUsage(
                        usageObj.getIntValue("prompt_tokens", 0),
                        usageObj.getIntValue("completion_tokens", 0));
            }""",
            """    private ChatChunk parseChunk(String data) {
        try {
            JsonObject obj = Json.parseObjectToJsonObject(data);
            String id = obj.getString("id");
            String model = obj.getString("model");
            JsonArray choices = obj.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            JsonObject choice = choices.getJSONObject(0);
            JsonObject delta = choice.getJSONObject("delta");
            String finishReason = choice.getString("finish_reason");
            String content = delta != null ? delta.getString("content") : null;

            TokenUsage usage = null;
            if (obj.containsKey("usage") && obj.get("usage") != null) {
                JsonObject usageObj = obj.getJSONObject("usage");
                usage = new TokenUsage(
                        usageObj.getIntValue("prompt_tokens", 0),
                        usageObj.getIntValue("completion_tokens", 0));
            }""",
        ),
    ]

    count = 0
    for old, new in replacements:
        if old in content:
            content = content.replace(old, new, 1)
            count += 1

    if count > 0:
        f.write_text(content, encoding="utf-8")
        print(f"[OK] {f.name}: 替换 {count} 处 Map→JsonObject")
    else:
        print(f"[SKIP] {f.name}: 未找到需要替换的方法")


def fix_openai_embedding_client():
    """修复 OpenAiEmbeddingClient.java。"""
    f = ROOT / "ydsz-backend/ydsz-agent/ydsz-agent-infra/src/main/java/com/njydsz/agent/infra/rag/OpenAiEmbeddingClient.java"
    content = f.read_text(encoding="utf-8")

    if "import com.njydsz.common.json.object.JsonArray;" not in content:
        content = content.replace(
            "import com.njydsz.common.json.Json;",
            "import com.njydsz.common.json.Json;\nimport com.njydsz.common.json.object.JsonArray;\nimport com.njydsz.common.json.object.JsonObject;",
            1,
        )

    old = """    private List<List<Float>> parseEmbeddings(String json) {
        Map<String, Object> obj = Json.parseMap(json);
        List<Object> data = obj.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new LlmException("Embedding 响应无 data", LlmException.ErrorType.INVALID_RESPONSE);
        }
        List<List<Float>> result = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> item = data.getJSONObject(i);
            List<Object> embedding = item.getJSONArray("embedding");
            List<Float> vector = new ArrayList<>(embedding.size());
            for (int j = 0; j < embedding.size(); j++) {
                vector.add(embedding.getFloatValue(j));
            }
            result.add(vector);
        }
        return result;
    }"""

    new = """    private List<List<Float>> parseEmbeddings(String json) {
        JsonObject obj = Json.parseObjectToJsonObject(json);
        JsonArray data = obj.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new LlmException("Embedding 响应无 data", LlmException.ErrorType.INVALID_RESPONSE);
        }
        List<List<Float>> result = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            JsonObject item = data.getJSONObject(i);
            JsonArray embedding = item.getJSONArray("embedding");
            List<Float> vector = new ArrayList<>(embedding.size());
            for (int j = 0; j < embedding.size(); j++) {
                vector.add(embedding.getFloatValue(j));
            }
            result.add(vector);
        }
        return result;
    }"""

    if old in content:
        content = content.replace(old, new, 1)
        f.write_text(content, encoding="utf-8")
        print(f"[OK] {f.name}: 改用 JsonObject API")
    else:
        print(f"[SKIP] {f.name}: 未找到目标方法")


def main():
    print("=== 修复剩余编译错误 ===\n")
    fix_pg_search_engine_line_373()
    fix_openai_compatible_client()
    fix_openai_embedding_client()
    print("\n=== 完成 ===")


if __name__ == "__main__":
    main()
