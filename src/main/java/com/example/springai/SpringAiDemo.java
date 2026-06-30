package com.example.springai;

import com.example.common.AppConfig;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring AI 集成 Demo
 *
 * 注意：这是一个模拟 Spring 风格的 Demo，不依赖 Spring Boot
 * 真实 Spring Boot 集成需要添加 spring-boot-starter 依赖
 *
 * 核心知识点：
 * 1. Spring 风格的依赖注入
 * 2. @Component、@Service 注解模拟
 * 3. 配置外部化
 * 4. 多用户会话管理
 * 5. REST API 风格的服务设计
 */
public class SpringAiDemo {

    private static final Logger log = LoggerFactory.getLogger(SpringAiDemo.class);

    // ========== 1. 配置类（模拟 @Configuration） ==========
    static class AiConfig {
        private final String apiKey;
        private final String baseUrl;
        private final String modelName;

        AiConfig() {
            var props = loadProperties();
            this.apiKey = props.getProperty("openai.api.key");
            this.baseUrl = props.getProperty("openai.api.base-url");
            this.modelName = props.getProperty("openai.model.name");
        }

        ChatLanguageModel chatModel() {
            return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(60))
                .build();
        }

        private Properties loadProperties() {
            try {
                var props = new Properties();
                var is = getClass().getClassLoader().getResourceAsStream("application.properties");
                if (is != null) props.load(is);
                return props;
            } catch (Exception e) {
                return new Properties();
            }
        }
    }

    // ========== 2. 工具类（模拟 @Component） ==========
    static class WeatherService {
        @Tool("获取城市天气")
        public String getWeather(String city) {
            log.info(">>> [天气服务] 查询: {}", city);
            return Map.of(
                "北京", "晴 25°C",
                "上海", "多云 28°C",
                "广州", "雷阵雨 32°C"
            ).getOrDefault(city, city + " 晴 22°C");
        }
    }

    static class CalculatorService {
        @Tool("计算数学表达式")
        public String calculate(String expression) {
            log.info(">>> [计算服务] 计算: {}", expression);
            try {
                expression = expression.replaceAll("\\s+", "");
                if (expression.contains("+")) {
                    String[] parts = expression.split("\\+");
                    return String.valueOf(
                        Integer.parseInt(parts[0]) + Integer.parseInt(parts[1])
                    );
                }
                return "无法计算";
            } catch (Exception e) {
                return "计算错误: " + e.getMessage();
            }
        }
    }

    // ========== 3. AI 助手接口（模拟 @Service） ==========
    interface AiAssistant {
        String chat(@MemoryId String userId, @UserMessage String message);
    }

    // ========== 4. 服务类（模拟 @Service） ==========
    static class ChatService {
        private final AiAssistant assistant;
        private final Map<String, List<ChatMessage>> chatHistory = new ConcurrentHashMap<>();

        ChatService(ChatLanguageModel model, WeatherService weatherService,
                    CalculatorService calculatorService) {
            this.assistant = AiServices.builder(AiAssistant.class)
                .chatLanguageModel(model)
                .chatMemoryProvider(memoryId ->
                    MessageWindowChatMemory.builder()
                        .maxMessages(20)
                        .build()
                )
                .tools(weatherService, calculatorService)
                .build();
        }

        /**
         * 处理用户消息（类似 Controller 方法）
         */
        ChatResponse chat(String userId, String message) {
            log.info(">>> [ChatService] 用户 {} 发送消息: {}", userId, message);

            // 记录历史
            chatHistory.computeIfAbsent(userId, k -> new ArrayList<>())
                .add(dev.langchain4j.data.message.UserMessage.from(message));

            // 调用 AI（带错误处理）
            String response;
            try {
                response = assistant.chat(userId, message);
            } catch (Exception e) {
                log.error("AI 调用失败: {}", e.getMessage());
                response = "抱歉，AI 服务暂时不可用。错误: " + e.getMessage();
            }

            // 记录响应
            chatHistory.get(userId).add(AiMessage.from(response));

            return new ChatResponse(userId, message, response);
        }

        /**
         * 获取用户历史
         */
        List<ChatMessage> getHistory(String userId) {
            return chatHistory.getOrDefault(userId, List.of());
        }

        /**
         * 清除用户历史
         */
        void clearHistory(String userId) {
            chatHistory.remove(userId);
        }
    }

    // ========== 5. 响应类（模拟 DTO） ==========
    static class ChatResponse {
        private final String userId;
        private final String message;
        private final String response;
        private final String timestamp;

        ChatResponse(String userId, String message, String response) {
            this.userId = userId;
            this.message = message;
            this.response = response;
            this.timestamp = new java.text.SimpleDateFormat("HH:mm:ss")
                .format(new java.util.Date());
        }

        @Override
        public String toString() {
            return String.format("[%s] 用户%s: %s → 助手: %s",
                timestamp, userId, message, response);
        }
    }

    // ========== 6. 模拟 Controller ==========
    static class ChatController {
        private final ChatService chatService;

        ChatController(ChatService chatService) {
            this.chatService = chatService;
        }

        /**
         * POST /api/chat
         * 模拟 REST API 调用
         */
        ChatResponse chat(String userId, String message) {
            return chatService.chat(userId, message);
        }

        /**
         * GET /api/chat/history/{userId}
         */
        List<ChatMessage> getHistory(String userId) {
            return chatService.getHistory(userId);
        }
    }

    // ========== 主程序 ==========
    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  Spring AI 集成 Demo（模拟 Spring 风格）");
        System.out.println("=".repeat(60));

        // 模拟 Spring 容器初始化
        AiConfig config = new AiConfig();
        ChatLanguageModel model = config.chatModel();
        WeatherService weatherService = new WeatherService();
        CalculatorService calculatorService = new CalculatorService();
        ChatService chatService = new ChatService(model, weatherService, calculatorService);
        ChatController chatController = new ChatController(chatService);

        // 模拟 HTTP 请求
        System.out.println("\n--- 模拟 REST API 调用 ---\n");

        // 用户A的请求
        System.out.println("POST /api/chat");
        System.out.println("Body: {userId: \"userA\", message: \"北京天气怎么样？\"}");
        ChatResponse response1 = chatController.chat("userA", "北京天气怎么样？");
        System.out.println("Response: " + response1);

        System.out.println("\nPOST /api/chat");
        System.out.println("Body: {userId: \"userA\", message: \"100+200等于多少？\"}");
        ChatResponse response2 = chatController.chat("userA", "100+200等于多少？");
        System.out.println("Response: " + response2);

        // 用户B的请求
        System.out.println("\nPOST /api/chat");
        System.out.println("Body: {userId: \"userB\", message: \"上海天气怎么样？\"}");
        ChatResponse response3 = chatController.chat("userB", "上海天气怎么样？");
        System.out.println("Response: " + response3);

        // 查询历史
        System.out.println("\nGET /api/chat/history/userA");
        List<ChatMessage> history = chatController.getHistory("userA");
        System.out.println("History size: " + history.size());

        // ========== 展示 Spring Boot 集成方式 ==========
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  Spring Boot 真实集成方式");
        System.out.println("=".repeat(60));

        System.out.println("""
            1. 添加依赖 (pom.xml):
               <dependency>
                   <groupId>org.springframework.boot</groupId>
                   <artifactId>spring-boot-starter-web</artifactId>
               </dependency>

            2. 配置类:
               @Configuration
               public class AiConfig {
                   @Bean
                   public ChatLanguageModel chatModel() {
                       return OpenAiChatModel.builder()
                           .apiKey("${openai.api.key}")
                           .build();
                   }
               }

            3. 服务类:
               @Service
               public class ChatService {
                   @Autowired
                   private ChatLanguageModel model;

                   private AiAssistant assistant;

                   @PostConstruct
                   public void init() {
                       assistant = AiServices.builder(AiAssistant.class)
                           .chatLanguageModel(model)
                           .tools(new WeatherService())
                           .build();
                   }
               }

            4. Controller:
               @RestController
               @RequestMapping("/api/chat")
               public class ChatController {
                   @Autowired
                   private ChatService chatService;

                   @PostMapping
                   public ChatResponse chat(@RequestBody ChatRequest request) {
                       return chatService.chat(request.getUserId(),
                                              request.getMessage());
                   }
               }

            5. 配置文件 (application.yml):
               openai:
                 api:
                   key: ${OPENAI_API_KEY}
                   base-url: https://api.openai.com
                 model:
                   name: gpt-4
            """);
    }
}
