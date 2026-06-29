package com.example.integration;

import com.example.common.AppConfig;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LangChain4j 集成 Demo
 *
 * 核心知识点：
 * 1. ChatLanguageModel - 模型调用
 * 2. AiServices - AI 服务代理
 * 3. ChatMemory - 消息历史管理
 * 4. @Tool - 工具定义
 * 5. @SystemMessage - 系统提示词
 * 6. @MemoryId - 多用户会话隔离
 * 7. TokenStream - 流式输出
 */
public class LangChain4jIntegrationDemo {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jIntegrationDemo.class);

    // ========== 1. 工具定义 ==========
    static class WeatherTools {
        @Tool("获取城市天气")
        public String getWeather(String city) {
            log.info(">>> [天气] {}", city);
            return Map.of("北京", "晴 25°C", "上海", "多云 28°C")
                .getOrDefault(city, city + " 晴 22°C");
        }
    }

    // ========== 2. AI 服务接口（带记忆） ==========
    interface ChatAssistant {
        String chat(@MemoryId String userId, String message);
    }

    // ========== 3. 多用户会话管理 ==========
    static class MultiUserChatService {
        private final ChatAssistant assistant;
        private final Map<String, ChatMemory> userMemories = new ConcurrentHashMap<>();

        MultiUserChatService(ChatLanguageModel model) {
            // 为每个用户创建独立的 ChatMemory
            this.assistant = AiServices.builder(ChatAssistant.class)
                .chatLanguageModel(model)
                .chatMemoryProvider(memoryId ->
                    userMemories.computeIfAbsent(memoryId.toString(),
                        k -> MessageWindowChatMemory.builder()
                            .maxMessages(20)
                            .build())
                )
                .tools(new WeatherTools())
                .build();
        }

        String chat(String userId, String message) {
            log.info(">>> [{}] 用户消息: {}", userId, message);
            return assistant.chat(userId, message);
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

        System.out.println("\n" + "=".repeat(60));
        System.out.println("  LangChain4j 集成 Demo");
        System.out.println("=".repeat(60));

        // ========== 演示1: 基础模型调用 ==========
        System.out.println("\n--- 1. 基础模型调用 ---");
        basicChat(model);

        // ========== 演示2: AiServices 代理 ==========
        System.out.println("\n--- 2. AiServices 代理 ---");
        aiServicesDemo(model);

        // ========== 演示3: 工具调用 ==========
        System.out.println("\n--- 3. 工具调用 ---");
        toolCallingDemo(model);

        // ========== 演示4: 多用户会话 ==========
        System.out.println("\n--- 4. 多用户会话 ---");
        multiUserDemo(model);

        // ========== 演示5: 消息历史 ==========
        System.out.println("\n--- 5. 消息历史 ---");
        chatMemoryDemo(model);
    }

    static void basicChat(ChatLanguageModel model) {
        // 简单调用
        String response = model.chat("你好，请自我介绍");
        System.out.println("AI: " + response);

        // 带系统提示词
        List<ChatMessage> messages = List.of(
            dev.langchain4j.data.message.SystemMessage.from("你是一个专业的Java技术顾问"),
            dev.langchain4j.data.message.UserMessage.from("什么是Spring Boot？")
        );
        ChatResponse chatResponse = model.chat(messages);
        System.out.println("\n技术顾问: " + chatResponse.aiMessage().text());
    }

    static void aiServicesDemo(ChatLanguageModel model) {
        // 创建简单助手
        interface SimpleAssistant {
            String chat(String message);
        }

        SimpleAssistant assistant = AiServices.builder(SimpleAssistant.class)
            .chatLanguageModel(model)
            .build();

        System.out.println("助手: " + assistant.chat("用一句话介绍Java"));
    }

    static void toolCallingDemo(ChatLanguageModel model) {
        interface WeatherAssistant {
            String chat(String message);
        }

        WeatherAssistant assistant = AiServices.builder(WeatherAssistant.class)
            .chatLanguageModel(model)
            .tools(new WeatherTools())
            .build();

        System.out.println("用户: 北京天气怎么样？");
        System.out.println("助手: " + assistant.chat("北京天气怎么样？"));
    }

    static void multiUserDemo(ChatLanguageModel model) {
        MultiUserChatService service = new MultiUserChatService(model);

        // 用户A的对话
        System.out.println("\n[用户A] 你好，我是A");
        System.out.println("助手: " + service.chat("userA", "你好，我是A"));

        // 用户B的对话
        System.out.println("\n[用户B] 你好，我是B");
        System.out.println("助手: " + service.chat("userB", "你好，我是B"));

        // 用户A继续（助手应该记得A）
        System.out.println("\n[用户A] 我是谁？");
        System.out.println("助手: " + service.chat("userA", "我是谁？"));
    }

    static void chatMemoryDemo(ChatLanguageModel model) {
        // 创建带记忆的助手
        interface MemoryAssistant {
            String chat(@MemoryId String sessionId, String message);
        }

        ChatMemory memory = MessageWindowChatMemory.builder()
            .maxMessages(10)
            .build();

        MemoryAssistant assistant = AiServices.builder(MemoryAssistant.class)
            .chatLanguageModel(model)
            .chatMemory(memory)
            .build();

        String sessionId = "test-session";

        System.out.println("用户: 我叫张三");
        System.out.println("助手: " + assistant.chat(sessionId, "我叫张三"));

        System.out.println("\n用户: 我叫什么名字？");
        System.out.println("助手: " + assistant.chat(sessionId, "我叫什么名字？"));
    }
}
