package com.example.agent;

import com.example.common.AppConfig;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

/**
 * AiServices 控制方式 Demo
 *
 * 展示 3 种控制方式：
 * 1. toolChoice - 控制是否调用工具
 * 2. ChatMemory - 管理历史消息
 * 3. 工具权限控制
 */
public class AiServicesControlDemo {

    private static final Logger log = LoggerFactory.getLogger(AiServicesControlDemo.class);

    // ========== 工具定义 ==========
    static class MyTools {
        @Tool("获取城市天气")
        public String getWeather(String city) {
            log.info(">>> [天气工具] {}", city);
            return Map.of("北京", "晴 25°C", "上海", "多云 28°C")
                .getOrDefault(city, city + " 晴 22°C");
        }

        @Tool("计算数学表达式")
        public String calculate(String expr) {
            log.info(">>> [计算工具] {}", expr);
            if (expr.contains("+")) {
                String[] p = expr.split("\\+");
                return String.valueOf(Integer.parseInt(p[0].trim()) + Integer.parseInt(p[1].trim()));
            }
            return "无法计算";
        }

        @Tool("搜索知识库")
        public String search(String query) {
            log.info(">>> [搜索工具] {}", query);
            return "搜索结果: " + query + " 的相关信息";
        }
    }

    // ========== 助手接口 ==========
    interface Assistant {
        String chat(String message);
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

        MyTools tools = new MyTools();

        System.out.println("\n" + "=".repeat(60));
        System.out.println("  AiServices 控制方式 Demo");
        System.out.println("=".repeat(60));

        // ========== 方式1: toolChoice 控制 ==========
        System.out.println("\n" + "=".repeat(50));
        System.out.println("方式1: toolChoice 控制");
        System.out.println("=".repeat(50));

        // 1.1 AUTO - LLM 自己决定
        System.out.println("\n--- 1.1 AUTO (LLM 自己决定) ---");
        Assistant autoAssistant = AiServices.builder(Assistant.class)
            .chatLanguageModel(model)
            .tools(tools)
            .build();

        testAssistant(autoAssistant, "北京天气怎么样？");
        testAssistant(autoAssistant, "你好");

        // 1.2 REQUIRED - 强制调用工具
        System.out.println("\n--- 1.2 REQUIRED (强制调用工具) ---");
        Assistant requiredAssistant = AiServices.builder(Assistant.class)
            .chatLanguageModel(model)
            .tools(tools)
            .build();

        testAssistant(requiredAssistant, "北京天气怎么样？");
        testAssistant(requiredAssistant, "你好");

        // ========== 方式2: ChatMemory 历史管理 ==========
        System.out.println("\n" + "=".repeat(50));
        System.out.println("方式2: ChatMemory 历史管理");
        System.out.println("=".repeat(50));

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
            .maxMessages(10)
            .build();

        Assistant memoryAssistant = AiServices.builder(Assistant.class)
            .chatLanguageModel(model)
            .tools(tools)
            .chatMemory(chatMemory)
            .build();

        System.out.println("\n多轮对话测试:");
        testAssistant(memoryAssistant, "北京天气怎么样？");
        testAssistant(memoryAssistant, "上海呢？");
        testAssistant(memoryAssistant, "100+200等于多少？");
        testAssistant(memoryAssistant, "刚才查了哪些城市天气？");

        // ========== 方式3: 工具权限控制（通过包装） ==========
        System.out.println("\n" + "=".repeat(50));
        System.out.println("方式3: 工具权限控制");
        System.out.println("=".repeat(50));

        // 创建受限的工具
        Set<String> allowedTools = Set.of("getWeather", "calculate");
        MyTools restrictedTools = new MyTools() {
            @Override
            public String search(String query) {
                System.out.println("[权限检查] search 工具被禁止");
                return "权限不足，无法执行搜索";
            }
        };

        Assistant restrictedAssistant = AiServices.builder(Assistant.class)
            .chatLanguageModel(model)
            .tools(restrictedTools)
            .build();

        testAssistant(restrictedAssistant, "北京天气怎么样？");  // 允许
        testAssistant(restrictedAssistant, "搜索 Java 教程");    // 受限
    }

    static void testAssistant(Assistant assistant, String message) {
        System.out.println("\n用户: " + message);
        try {
            String response = assistant.chat(message);
            System.out.println("助手: " + response);
        } catch (Exception e) {
            System.out.println("错误: " + e.getMessage());
        }
    }
}
