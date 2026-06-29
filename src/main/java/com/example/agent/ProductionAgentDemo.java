package com.example.agent;

import com.example.common.AppConfig;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 生产级 ReACT Agent Demo
 *
 * 核心特性：
 * 1. 智能路由 - 自动判断是否需要工具
 * 2. 强制工具调用 - 匹配工具时强制调用
 * 3. 提示词强化 - 确保工具调用格式正确
 * 4. 结果验证 - 检查工具是否正确调用
 * 5. 重试机制 - 工具调用失败时重试
 * 6. 降级处理 - 工具不可用时直接回答
 */
public class ProductionAgentDemo {

    private static final Logger log = LoggerFactory.getLogger(ProductionAgentDemo.class);

    // ========== 工具定义 ==========

    record ToolSpec(String name, String description, List<String> keywords, String paramDesc) {}
    record ToolResult(boolean success, String result, String error) {}

    static class ToolRegistry {
        private final Map<String, ToolSpec> tools = new LinkedHashMap<>();
        private final Map<String, ToolExecutor> executors = new HashMap<>();

        void register(ToolSpec spec, ToolExecutor executor) {
            tools.put(spec.name(), spec);
            executors.put(spec.name(), executor);
        }

        Optional<ToolSpec> findTool(String query) {
            return tools.values().stream()
                .filter(tool -> tool.keywords().stream().anyMatch(query::contains))
                .findFirst();
        }

        ToolResult execute(String toolName, String param) {
            ToolExecutor executor = executors.get(toolName);
            if (executor == null) return new ToolResult(false, null, "工具不存在: " + toolName);
            try {
                return new ToolResult(true, executor.execute(param), null);
            } catch (Exception e) {
                return new ToolResult(false, null, e.getMessage());
            }
        }

        String formatTools() {
            return tools.values().stream()
                .map(t -> "- %s: %s（参数: %s）".formatted(t.name(), t.description(), t.paramDesc()))
                .collect(Collectors.joining("\n"));
        }
    }

    @FunctionalInterface
    interface ToolExecutor {
        String execute(String param) throws Exception;
    }

    // ========== 工具实现 ==========

    static class WeatherTool implements ToolExecutor {
        @Override
        public String execute(String city) {
            log.info(">>> [天气工具] 查询: {}", city);
            return Map.of(
                "北京", "北京今天晴，25°C，微风",
                "上海", "上海今天多云，28°C",
                "广州", "广州今天雷阵雨，32°C"
            ).getOrDefault(city, city + "今天晴，22°C");
        }
    }

    static class CalculatorTool implements ToolExecutor {
        @Override
        public String execute(String expr) {
            log.info(">>> [计算工具] 计算: {}", expr);
            expr = expr.replaceAll("\\s+", "");
            if (expr.contains("+")) {
                String[] p = expr.split("\\+");
                return String.valueOf(Double.parseDouble(p[0]) + Double.parseDouble(p[1]));
            } else if (expr.contains("*")) {
                String[] p = expr.split("\\*");
                return String.valueOf(Double.parseDouble(p[0]) * Double.parseDouble(p[1]));
            }
            return "无法计算: " + expr;
        }
    }

    static class KnowledgeTool implements ToolExecutor {
        @Override
        public String execute(String query) {
            log.info(">>> [知识库工具] 搜索: {}", query);
            return Map.of(
                "Java", "Java是面向对象编程语言，1995年由Sun发布",
                "Python", "Python是解释型语言，1991年由Guido发布"
            ).entrySet().stream()
                .filter(e -> query.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("未找到: " + query);
        }
    }

    // ========== Agent 核心 ==========

    enum Route { TOOL_REQUIRED, TOOL_OPTIONAL, DIRECT_ANSWER }

    static class ProductionAgent {
        private final ChatLanguageModel model;
        private final ToolRegistry registry;

        ProductionAgent(ChatLanguageModel model, ToolRegistry registry) {
            this.model = model;
            this.registry = registry;
        }

        String chat(String userQuery) {
            log.info(">>> [Agent] 收到: {}", userQuery);
            Route route = route(userQuery);
            log.info(">>> [Agent] 路由: {}", route);

            return switch (route) {
                case TOOL_REQUIRED -> chatWithTool(userQuery);
                case TOOL_OPTIONAL -> chatOptional(userQuery);
                case DIRECT_ANSWER -> model.chat(userQuery);
            };
        }

        private Route route(String query) {
            if (registry.findTool(query).isPresent()) return Route.TOOL_REQUIRED;
            if (query.contains("?") || query.contains("？") || query.contains("什么")) return Route.TOOL_OPTIONAL;
            return Route.DIRECT_ANSWER;
        }

        /**
         * 强制工具调用模式
         */
        private String chatWithTool(String userQuery) {
            log.info(">>> [Agent] 强制工具调用模式");

            String systemPrompt = """
                你必须使用工具回答问题。

                可用工具：
                %s

                格式：TOOL: 工具名(参数)
                """.formatted(registry.formatTools());

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(systemPrompt));
            messages.add(UserMessage.from(userQuery));

            for (int i = 0; i < 3; i++) {
                String response = model.chat(messages).aiMessage().text();
                log.info(">>> [Agent] LLM: {}", response);

                if (response.contains("TOOL:")) {
                    // 提取工具调用
                    String toolCall = extractToolCall(response);
                    ToolResult result = executeToolCall(toolCall);

                    if (result.success()) {
                        // 工具成功，让 LLM 组织最终答案
                        messages.add(AiMessage.from(response));
                        messages.add(UserMessage.from("工具结果: " + result.result()));
                        return model.chat(messages).aiMessage().text();
                    } else {
                        // 工具失败，重试
                        messages.add(AiMessage.from(response));
                        messages.add(UserMessage.from("工具失败: " + result.error() + "，请重试"));
                    }
                } else if (i < 2) {
                    // 没调用工具，强制要求
                    messages.add(AiMessage.from(response));
                    messages.add(UserMessage.from("请使用工具，格式：TOOL: 工具名(参数)"));
                } else {
                    return response;
                }
            }
            return "无法处理";
        }

        /**
         * 可选工具调用模式
         */
        private String chatOptional(String userQuery) {
            log.info(">>> [Agent] 可选工具调用模式");

            String systemPrompt = """
                你是智能助手。可用工具：
                %s

                如需工具，格式：TOOL: 工具名(参数)
                """.formatted(registry.formatTools());

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(systemPrompt));
            messages.add(UserMessage.from(userQuery));

            String response = model.chat(messages).aiMessage().text();

            if (response.contains("TOOL:")) {
                String toolCall = extractToolCall(response);
                ToolResult result = executeToolCall(toolCall);
                if (result.success()) {
                    messages.add(AiMessage.from(response));
                    messages.add(UserMessage.from("工具结果: " + result.result()));
                    return model.chat(messages).aiMessage().text();
                }
            }
            return response;
        }

        private String extractToolCall(String response) {
            // 提取 TOOL: 后面的内容
            int idx = response.indexOf("TOOL:");
            if (idx >= 0) {
                String call = response.substring(idx + 5).trim();
                // 取第一行
                int newline = call.indexOf('\n');
                return newline > 0 ? call.substring(0, newline).trim() : call;
            }
            return response;
        }

        private ToolResult executeToolCall(String toolCall) {
            Pattern pattern = Pattern.compile("(\\w+)\\((.+)\\)");
            Matcher matcher = pattern.matcher(toolCall);
            if (matcher.matches()) {
                return registry.execute(matcher.group(1), matcher.group(2));
            }
            return new ToolResult(false, null, "无法解析: " + toolCall);
        }
    }

    // ========== 主程序 ==========

    public static void main(String[] args) throws Exception {
        var config = AppConfig.loadConfig();
        String apiKey = config.getProperty(AppConfig.OPENAI_API_KEY);
        String baseUrl = config.getProperty(AppConfig.OPENAI_API_BASE_URL);
        String modelName = config.getProperty(AppConfig.OPENAI_MODEL_NAME);

        if (!AppConfig.isApiKeyConfigured(apiKey)) {
            log.error("请先配置 API Key！");
            return;
        }

        ChatLanguageModel model = OpenAiChatModel.builder()
            .apiKey(apiKey).baseUrl(baseUrl).modelName(modelName)
            .timeout(Duration.ofSeconds(60)).build();

        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolSpec("getWeather", "获取天气", List.of("天气", "气温"), "城市"), new WeatherTool());
        registry.register(new ToolSpec("calculate", "计算", List.of("计算", "多少", "+", "*"), "表达式"), new CalculatorTool());
        registry.register(new ToolSpec("searchKnowledge", "搜索知识", List.of("什么是", "Java", "Python"), "关键词"), new KnowledgeTool());

        ProductionAgent agent = new ProductionAgent(model, registry);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("  生产级 ReACT Agent Demo");
        System.out.println("=".repeat(60));

        List<String> queries = List.of(
            "北京今天天气怎么样？",
            "100 + 200 等于多少？",
            "告诉我什么是Java",
            "你好，今天心情不错"
        );

        for (String q : queries) {
            System.out.println("\n" + "-".repeat(50));
            System.out.println("用户: " + q);
            System.out.println("-".repeat(50));
            System.out.println("助手: " + agent.chat(q));
        }
    }
}
