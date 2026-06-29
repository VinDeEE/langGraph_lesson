package com.example.agent;

import com.example.common.AppConfig;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * ReACT Agent Demo
 *
 * 核心知识点：
 * 1. ReACT = Reasoning + Acting
 * 2. LLM 自动决定是否调用工具
 * 3. 工具结果返回给 LLM 继续推理
 * 4. 循环执行直到 LLM 认为任务完成
 *
 * 流程：
 * ┌─────┐
 * │start│
 * └──┬──┘
 *    ▼
 * ┌─────┐     ┌─────────────────┐
 * │model│ ──▶ │ action_executor │
 * └─────┘     └────────┬────────┘
 *    ▲                 │
 *    │                 ▼
 *    │           ┌──────────┐
 *    └───────────│ 继续/结束 │
 *                └──────────┘
 */
public class ReActAgentDemo {

    private static final Logger log = LoggerFactory.getLogger(ReActAgentDemo.class);

    // 定义工具
    public static class Tools {

        public String getWeather(String city) {
            log.info(">>> [工具] 查询天气: {}", city);
            // 模拟天气查询
            Map<String, String> weatherData = Map.of(
                "北京", "北京今天晴，25°C，微风",
                "上海", "上海今天多云，28°C，东南风",
                "广州", "广州今天雷阵雨，32°C，湿度80%",
                "深圳", "深圳今天阴，30°C，南风"
            );
            return weatherData.getOrDefault(city, city + "今天晴，22°C");
        }

        public String calculate(String expression) {
            log.info(">>> [工具] 计算: {}", expression);
            try {
                // 简单的计算模拟
                if (expression.contains("+")) {
                    String[] parts = expression.split("\\+");
                    double a = Double.parseDouble(parts[0].trim());
                    double b = Double.parseDouble(parts[1].trim());
                    return String.valueOf(a + b);
                } else if (expression.contains("*")) {
                    String[] parts = expression.split("\\*");
                    double a = Double.parseDouble(parts[0].trim());
                    double b = Double.parseDouble(parts[1].trim());
                    return String.valueOf(a * b);
                }
                return "无法计算: " + expression;
            } catch (Exception e) {
                return "计算错误: " + e.getMessage();
            }
        }

        public String searchKnowledge(String query) {
            log.info(">>> [工具] 搜索: {}", query);
            // 模拟知识库搜索
            if (query.contains("Java")) {
                return "Java是一种面向对象的编程语言，由Sun Microsystems于1995年发布。";
            } else if (query.contains("Python")) {
                return "Python是一种解释型、面向对象的高级编程语言，由Guido van Rossum于1991年发布。";
            }
            return "未找到相关信息: " + query;
        }
    }

    public static void main(String[] args) throws Exception {
        // 加载配置
        var config = AppConfig.loadConfig();
        String apiKey = config.getProperty(AppConfig.OPENAI_API_KEY);
        String baseUrl = config.getProperty(AppConfig.OPENAI_API_BASE_URL);
        String modelName = config.getProperty(AppConfig.OPENAI_MODEL_NAME);

        if (!AppConfig.isApiKeyConfigured(apiKey)) {
            log.error("请先配置 API Key！");
            return;
        }

        // 创建 LLM
        ChatLanguageModel model = OpenAiChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .modelName(modelName)
            .timeout(Duration.ofSeconds(60))
            .build();

        System.out.println("\n" + "=".repeat(60));
        System.out.println("  ReACT Agent Demo");
        System.out.println("=".repeat(60));

        // 创建 Agent
        // 注意：当前版本的 Agent 接口需要较复杂的配置
        // 这里我们手动实现 ReACT 循环来演示核心概念

        Tools tools = new Tools();

        // 测试用例
        List<String> testQueries = List.of(
            "北京今天天气怎么样？",
            "100 + 200 等于多少？",
            "告诉我什么是Java"
        );

        for (String query : testQueries) {
            runReACT(model, tools, query);
        }
    }

    /**
     * 手动实现 ReACT 循环
     *
     * 核心逻辑：
     * 1. 发送用户消息给 LLM
     * 2. LLM 决定是直接回答还是调用工具
     * 3. 如果调用工具，执行工具并将结果返回给 LLM
     * 4. 重复直到 LLM 给出最终答案
     */
    static void runReACT(ChatLanguageModel model, Tools tools, String userQuery) {
        System.out.println("\n" + "-".repeat(50));
        System.out.println("用户: " + userQuery);
        System.out.println("-".repeat(50));

        // 构建消息历史
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from("""
            你是一个智能助手。你可以使用以下工具：
            1. getWeather(city) - 获取城市天气
            2. calculate(expression) - 计算数学表达式
            3. searchKnowledge(query) - 搜索知识库

            如果需要使用工具，请用以下格式回复：
            TOOL: 工具名(参数)

            如果不需要工具，直接回答用户问题。
            """));
        messages.add(UserMessage.from(userQuery));

        // ReACT 循环（最多 5 轮）
        int maxIterations = 5;
        for (int i = 0; i < maxIterations; i++) {
            log.info(">>> ReACT 第 {} 轮", i + 1);

            // 1. 调用 LLM
            String llmResponse = model.chat(messages).aiMessage().text();
            System.out.println("\nLLM: " + llmResponse);

            // 2. 检查是否需要调用工具
            if (llmResponse.startsWith("TOOL:")) {
                // 解析工具调用
                String toolCall = llmResponse.substring(5).trim();
                String toolResult = executeTool(tools, toolCall);
                System.out.println("工具结果: " + toolResult);

                // 3. 将工具结果添加到消息历史
                messages.add(AiMessage.from(llmResponse));
                messages.add(UserMessage.from("工具返回结果: " + toolResult));

            } else {
                // 4. LLM 给出了最终答案
                System.out.println("\n最终答案: " + llmResponse);
                break;
            }
        }
    }

    /**
     * 执行工具调用
     */
    static String executeTool(Tools tools, String toolCall) {
        try {
            if (toolCall.startsWith("getWeather(")) {
                String city = toolCall.replaceAll("getWeather\\((.+)\\)", "$1");
                return tools.getWeather(city);
            } else if (toolCall.startsWith("calculate(")) {
                String expr = toolCall.replaceAll("calculate\\((.+)\\)", "$1");
                return tools.calculate(expr);
            } else if (toolCall.startsWith("searchKnowledge(")) {
                String query = toolCall.replaceAll("searchKnowledge\\((.+)\\)", "$1");
                return tools.searchKnowledge(query);
            }
            return "未知工具: " + toolCall;
        } catch (Exception e) {
            return "工具执行错误: " + e.getMessage();
        }
    }
}
