package com.example.assistant;

import com.example.common.AppConfig;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.CompiledGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 智能任务助手 Demo
 *
 * 展示 Agent 的真正价值：
 * 1. 自然语言理解 - 模糊输入也能理解
 * 2. 动态决策 - 根据解析结果决定是否追问或执行
 * 3. 智能工具调用 - LLM 决定调用哪些工具
 * 4. 自适应回复 - 根据上下文生成个性化回复
 *
 * 架构：
 *                    ┌─────────────┐
 *               ┌───▶│  追问澄清    │───┐
 *               │    └─────────────┘   │
 *               │                      │
 * 用户输入 → 任务解析 ──────────────▶ 工具执行 → 回复生成 → END
 *               │                      │
 *               └──────────────────────┘
 *                    (信息完整时)
 */
public class TaskAssistantDemo {

    private static final Logger log = LoggerFactory.getLogger(TaskAssistantDemo.class);

    public static void main(String[] args) throws Exception {
        Properties config = AppConfig.loadConfig();
        String apiKey = config.getProperty(AppConfig.OPENAI_API_KEY);
        String baseUrl = config.getProperty(AppConfig.OPENAI_API_BASE_URL);
        String modelName = config.getProperty(AppConfig.OPENAI_MODEL_NAME);

        if (!AppConfig.isApiKeyConfigured(apiKey)) {
            log.error("请先在 src/main/resources/{} 中配置你的 OpenAI API Key！", AppConfig.CONFIG_FILE);
            return;
        }

        OpenAiChatModel model = OpenAiChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .modelName(modelName)
            .timeout(Duration.ofSeconds(60))
            .build();

        // 创建 Agent
        var parsingAgent = new TaskParsingAgent(model);
        var clarificationAgent = new ClarificationAgent(model);
        var toolAgent = new ToolExecutionAgent(model);
        var responseAgent = new ResponseGenerationAgent(model);

        // 构建图
        StateGraph<TaskAssistantState> graph = new StateGraph<>(
            TaskAssistantState.SCHEMA,
            TaskAssistantState::new
        )
            .addNode("parse_task", node_async(parsingAgent))
            .addNode("clarify", node_async(clarificationAgent))
            .addNode("execute_tools", node_async(toolAgent))
            .addNode("generate_response", node_async(responseAgent))

            // 流程
            .addEdge(START, "parse_task")

            // 条件路由：根据是否需要追问决定下一步
            .addConditionalEdges("parse_task",
                edge_async(state -> {
                    String nextAction = state.nextAction().orElse("execute");
                    log.info(">>> [路由] 下一步: {}", nextAction);
                    return nextAction;
                }),
                Map.of(
                    "clarify", "clarify",
                    "execute", "execute_tools"
                )
            )

            // 追问后结束（等待用户补充信息）
            .addEdge("clarify", END)

            // 执行工具 → 生成回复
            .addEdge("execute_tools", "generate_response")
            .addEdge("generate_response", END);

        CompiledGraph<TaskAssistantState> compiledGraph = graph.compile();

        // 运行测试
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  智能任务助手 Demo");
        System.out.println("=".repeat(60));

        // 测试用例展示 Agent 的价值
        List<String> testCases = List.of(
            // 完整信息 → 直接执行
            "帮我订明天下午4点的会议室，大概6个人用",

            // 模糊信息 → 需要追问
            "明天有个会",

            // 复杂请求 → 需要理解+多工具调用
            "下周三要给客户做产品演示，帮我准备一下",

            // 情绪化表达 → 需要理解情绪
            "烦死了，又要开会，帮我把明天的会都推掉"
        );

        for (String input : testCases) {
            runAssistant(compiledGraph, input);
        }
    }

    private static void runAssistant(CompiledGraph<TaskAssistantState> graph, String userInput) {
        System.out.println("\n" + "-".repeat(50));
        System.out.println("用户: " + userInput);
        System.out.println("-".repeat(50));

        Map<String, Object> inputs = Map.of(TaskAssistantState.USER_INPUT, userInput);

        List<String> executionPath = new ArrayList<>();
        for (var state : graph.stream(inputs)) {
            executionPath.add(state.node());
            log.info("节点 [{}] 执行完成", state.node());
        }

        System.out.println("\n执行路径: " + String.join(" → ", executionPath));

        var finalState = graph.invoke(inputs);
        finalState.ifPresent(state -> {
            System.out.println("\n处理流程:");
            state.messages().forEach(msg -> System.out.println("  " + msg));

            System.out.println("\n助手回复:");
            state.finalResponse().ifPresent(resp -> System.out.println("  " + resp));
        });
    }

}
