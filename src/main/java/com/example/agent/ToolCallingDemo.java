package com.example.agent;

import com.example.common.AppConfig;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

/**
 * 工具调用 Demo - 使用 @Tool 注解
 *
 * 核心知识点：
 * 1. @Tool 注解定义工具
 * 2. LLM 自动决定调用哪个工具
 * 3. 工具结果返回给 LLM 继续推理
 * 4. 使用 AiServices 自动处理工具调用
 *
 * 与 ProductionAgentDemo 的区别：
 * - ProductionAgentDemo：手动解析 TOOL: 格式
 * - ToolCallingDemo：使用 @Tool 注解 + AiServices 自动处理
 */
public class ToolCallingDemo {

    private static final Logger log = LoggerFactory.getLogger(ToolCallingDemo.class);

    /**
     * 工具类 - 使用 @Tool 注解定义工具
     */
    static class MyTools {

        @Tool("获取指定城市的天气信息，包括温度、天气状况和风力")
        public String getWeather(String city) {
            log.info(">>> [天气工具] 查询: {}", city);
            Map<String, String> weatherData = Map.of(
                "北京", "北京今天晴，25°C，微风，空气质量良好",
                "上海", "上海今天多云，28°C，东南风，湿度65%",
                "广州", "广州今天雷阵雨，32°C，湿度80%，注意带伞",
                "深圳", "深圳今天阴转晴，30°C，南风"
            );
            return weatherData.getOrDefault(city, city + "今天晴，22°C，适合出行");
        }

        @Tool("计算数学表达式，支持加减乘除运算")
        public String calculate(String expression) {
            log.info(">>> [计算工具] 计算: {}", expression);
            try {
                expression = expression.replaceAll("\\s+", "");
                if (expression.contains("+")) {
                    String[] parts = expression.split("\\+");
                    double a = Double.parseDouble(parts[0]);
                    double b = Double.parseDouble(parts[1]);
                    return String.valueOf(a + b);
                } else if (expression.contains("-")) {
                    String[] parts = expression.split("-");
                    double a = Double.parseDouble(parts[0]);
                    double b = Double.parseDouble(parts[1]);
                    return String.valueOf(a - b);
                } else if (expression.contains("*")) {
                    String[] parts = expression.split("\\*");
                    double a = Double.parseDouble(parts[0]);
                    double b = Double.parseDouble(parts[1]);
                    return String.valueOf(a * b);
                } else if (expression.contains("/")) {
                    String[] parts = expression.split("/");
                    double a = Double.parseDouble(parts[0]);
                    double b = Double.parseDouble(parts[1]);
                    if (b == 0) return "错误：除数不能为0";
                    return String.valueOf(a / b);
                }
                return "无法解析表达式: " + expression;
            } catch (Exception e) {
                return "计算错误: " + e.getMessage();
            }
        }

        @Tool("搜索知识库，查询技术概念和定义")
        public String searchKnowledge(String query) {
            log.info(">>> [知识库工具] 搜索: {}", query);
            Map<String, String> knowledge = Map.of(
                "Java", "Java是一种面向对象的编程语言，由Sun Microsystems于1995年发布。特点：跨平台、面向对象、安全性高、多线程支持。",
                "Python", "Python是一种解释型、面向对象的高级编程语言，由Guido van Rossum于1991年发布。特点：简洁易读、丰富的标准库、动态类型。",
                "Spring", "Spring是一个开源的Java/Java EE全功能栈应用程序框架。核心特性：IoC容器、AOP支持、声明式事务管理。",
                "Redis", "Redis是一个开源的内存数据结构存储系统，可用作数据库、缓存和消息中间件。支持字符串、哈希、列表、集合等数据结构。",
                "Docker", "Docker是一个开源的应用容器引擎，让开发者可以打包应用及其依赖包到一个可移植的容器中，然后发布到任何流行的Linux机器上。"
            );
            return knowledge.entrySet().stream()
                .filter(e -> query.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("未找到相关信息: " + query);
        }

        @Tool("获取当前日期和时间")
        public String getCurrentDateTime() {
            log.info(">>> [时间工具] 获取当前时间");
            return "当前时间: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
        }
    }

    /**
     * AI 助手接口 - 使用 AiServices 自动处理工具调用
     */
    interface Assistant {
        @SystemMessage("你是一个智能助手，可以使用工具来回答用户问题。请根据用户问题选择合适的工具。")
        String chat(String userMessage);
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

        // 创建工具
        MyTools tools = new MyTools();

        // 使用 AiServices 创建助手（自动处理工具调用）
        Assistant assistant = AiServices.builder(Assistant.class)
            .chatLanguageModel(model)
            .tools(tools)
            .build();

        System.out.println("\n" + "=".repeat(60));
        System.out.println("  工具调用 Demo - @Tool 注解");
        System.out.println("=".repeat(60));

        // 测试用例
        List<String> testQueries = List.of(
            "北京今天天气怎么样？",
            "100 + 200 等于多少？",
            "告诉我什么是Java",
            "现在几点了？",
            "你好，今天心情不错"
        );

        for (String query : testQueries) {
            System.out.println("\n" + "-".repeat(50));
            System.out.println("用户: " + query);
            System.out.println("-".repeat(50));

            try {
                String response = assistant.chat(query);
                System.out.println("助手: " + response);
            } catch (Exception e) {
                System.out.println("错误: " + e.getMessage());
            }
        }
    }
}
