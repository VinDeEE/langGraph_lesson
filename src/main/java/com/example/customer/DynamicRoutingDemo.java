package com.example.customer;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * LangGraph4j 动态路由 Demo
 *
 * 架构说明：
 *                         ┌─────────────────┐
 *                    ┌───▶│  投诉处理Agent    │───┐
 *                    │    └─────────────────┘   │
 *                    │                          │
 *                    │    ┌─────────────────┐   │
 * ┌──────────┐   ┌──┴──┐│  退换货Agent     │───┤   ┌──────────┐
 * │ 用户输入  │──▶│意图  │└─────────────────┘   ├──▶│ 统一输出  │
 * └──────────┘   │识别  │┌─────────────────┐   │   └──────────┘
 *                └──┬──┘│  订单查询Agent    │───┤
 *                   │   └─────────────────┘   │
 *                   │   ┌─────────────────┐   │
 *                   └──▶│  通用知识库Agent  │───┘
 *                       └─────────────────┘
 *
 * 核心区别：意图识别后，通过路由节点动态选择处理分支
 */
public class DynamicRoutingDemo {

    private static final Logger log = LoggerFactory.getLogger(DynamicRoutingDemo.class);

    public static void main(String[] args) throws Exception {
        // 1. 加载配置
        Properties config = loadConfig();
        String apiKey = config.getProperty("openai.api.key");
        String baseUrl = config.getProperty("openai.api.base-url");
        String modelName = config.getProperty("openai.model.name");

        if (apiKey == null || apiKey.equals("sk-your-api-key-here")) {
            log.error("请先在 src/main/resources/application.properties 中配置你的 OpenAI API Key！");
            return;
        }

        // 2. 初始化 LLM 模型
        OpenAiChatModel model = OpenAiChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .modelName(modelName)
            .timeout(Duration.ofSeconds(60))
            .build();

        log.info("LLM 模型初始化完成: {}", modelName);

        // 3. 创建各个 Agent 节点
        var intentAgent = new IntentRecognitionAgent(model);
        var routingNode = new RoutingNode();
        var complaintAgent = new ComplaintHandlerAgent(model);
        var returnAgent = new ReturnHandlerAgent(model);
        var orderAgent = new OrderQueryAgent(model);
        var knowledgeAgent = new KnowledgeBaseAgent(model);
        var responseAgent = new ResponseGeneratorAgent(model);

        // 4. 构建状态图 - 动态路由版本
        StateGraph<CustomerServiceState> graph = new StateGraph<>(
            CustomerServiceState.SCHEMA,
            CustomerServiceState::new
        )
            // 添加节点
            .addNode("intent_recognition", node_async(intentAgent))
            .addNode("router", node_async(routingNode))
            .addNode("complaint_handler", node_async(complaintAgent))
            .addNode("return_handler", node_async(returnAgent))
            .addNode("order_query", node_async(orderAgent))
            .addNode("knowledge_base", node_async(knowledgeAgent))
            .addNode("response_generator", node_async(responseAgent))

            // 固定边：开始 → 意图识别 → 路由节点
            .addEdge(START, "intent_recognition")
            .addEdge("intent_recognition", "router")

            // 核心：条件边 - 根据路由节点的结果动态选择处理节点
            .addConditionalEdges("router",
                edge_async(state -> {
                    // 从状态中获取路由目标
                    String nextNode = state.nextNode().orElse("knowledge_base");
                    log.info(">>> [动态路由] 路由到: {}", nextNode);
                    return nextNode;
                }),
                // 路由映射：所有可能的目标节点
                Map.of(
                    "complaint_handler", "complaint_handler",
                    "return_handler", "return_handler",
                    "order_query", "order_query",
                    "knowledge_base", "knowledge_base"
                )
            )

            // 通用知识库需要经过回复生成节点
            .addEdge("knowledge_base", "response_generator")

            // 所有处理节点最终都到 END
            .addEdge("complaint_handler", END)
            .addEdge("return_handler", END)
            .addEdge("order_query", END)
            .addEdge("response_generator", END);

        // 5. 编译图
        CompiledGraph<CustomerServiceState> compiledGraph = graph.compile();
        log.info("状态图编译完成");

        // 6. 运行示例
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  LangGraph4j 动态路由 Demo");
        System.out.println("=".repeat(60));

        // 测试用例 - 覆盖不同意图
        List<String> testMessages = List.of(
            "我要投诉！你们的服务太差了！",
            "我想退货，买的东西和描述不一样",
            "我的订单什么时候能到？",
            "这款手机的电池续航怎么样？"
        );

        for (String message : testMessages) {
            runCustomerService(compiledGraph, message);
        }
    }

    /**
     * 运行一次客服流程
     */
    private static void runCustomerService(CompiledGraph<CustomerServiceState> graph, String userMessage) {
        System.out.println("\n" + "-".repeat(50));
        System.out.println("用户: " + userMessage);
        System.out.println("-".repeat(50));

        Map<String, Object> inputs = Map.of(
            CustomerServiceState.USER_MESSAGE, userMessage
        );

        // 流式执行
        log.info("开始执行流程...");
        List<String> executionPath = new ArrayList<>();

        for (var state : graph.stream(inputs)) {
            executionPath.add(state.node());
            log.info("节点 [{}] 执行完成", state.node());
        }

        // 输出执行路径
        System.out.println("\n执行路径: " + String.join(" → ", executionPath));

        // 获取最终状态
        var finalStateOpt = graph.invoke(inputs);
        finalStateOpt.ifPresent(finalState -> {
            System.out.println("\n处理流程:");
            finalState.messages().forEach(msg -> System.out.println("  " + msg));

            System.out.println("\n客服回复:");
            finalState.response().ifPresent(resp -> System.out.println("  " + resp));
        });
    }

    private static Properties loadConfig() throws IOException {
        Properties props = new Properties();
        try (InputStream is = DynamicRoutingDemo.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (is != null) {
                props.load(is);
            }
        }
        return props;
    }
}
