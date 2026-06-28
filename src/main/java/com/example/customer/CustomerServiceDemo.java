package com.example.customer;

import com.example.common.AppConfig;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * LangGraph4j 智能客服 Demo
 *
 * 架构说明：
 * ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
 * │  意图识别Agent   │────▶│  知识库Agent     │────▶│  回复生成Agent   │
 * │ IntentRecognition│     │ KnowledgeBase   │     │ResponseGenerator│
 * └─────────────────┘     └─────────────────┘     └─────────────────┘
 *
 * 流程：用户输入 → 意图识别 → 知识库检索 → 生成回复 → 输出
 */
public class CustomerServiceDemo {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceDemo.class);

    public static void main(String[] args) throws Exception {
        // 1. 加载配置
        Properties config = AppConfig.loadConfig();
        String apiKey = config.getProperty(AppConfig.OPENAI_API_KEY);
        String baseUrl = config.getProperty(AppConfig.OPENAI_API_BASE_URL);
        String modelName = config.getProperty(AppConfig.OPENAI_MODEL_NAME);

        // 检查 API Key 是否已配置
        if (!AppConfig.isApiKeyConfigured(apiKey)) {
            log.error("请先在 src/main/resources/{} 中配置你的 OpenAI API Key！", AppConfig.CONFIG_FILE);
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
        var knowledgeAgent = new KnowledgeBaseAgent(model);
        var responseAgent = new ResponseGeneratorAgent(model);

        // 4. 构建状态图（StateGraph）
        //    这是 LangGraph 的核心：定义节点和边的关系
        StateGraph<CustomerServiceState> graph = new StateGraph<>(
            CustomerServiceState.SCHEMA,
            CustomerServiceState::new
        )
            // 添加节点：每个节点是一个 Agent
            .addNode("intent_recognition", node_async(intentAgent))
            .addNode("knowledge_base", node_async(knowledgeAgent))
            .addNode("response_generator", node_async(responseAgent))

            // 添加边：定义执行顺序
            .addEdge(START, "intent_recognition")          // 开始 → 意图识别
            .addEdge("intent_recognition", "knowledge_base")  // 意图识别 → 知识库
            .addEdge("knowledge_base", "response_generator")   // 知识库 → 回复生成
            .addEdge("response_generator", END);               // 回复生成 → 结束


        // 5. 编译图
        CompiledGraph<CustomerServiceState> compiledGraph = graph.compile();
        log.info("状态图编译完成");

        // 6. 运行示例
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  LangGraph4j 智能客服 Demo");
        System.out.println("=".repeat(60));

        // 测试用例列表
        List<String> testMessages = List.of(
            "这款手机的电池续航怎么样？",
            "我的订单什么时候能到？已经等了一周了",
            "我想退货，买的东西和描述不一样"
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

        // 准备初始状态
        Map<String, Object> inputs = Map.of(
            CustomerServiceState.USER_MESSAGE, userMessage
        );

        // 流式执行图，每一步都会输出状态变化
        log.info("开始执行流程...");
        for (var state : graph.stream(inputs)) {
            // 每个 state 是一个 NodeOutput，包含节点名和更新的状态
            log.info("节点 [{}] 执行完成", state.node());
        }

        // 获取最终状态
        var finalStateOpt = graph.invoke(inputs);

        // 输出结果
        finalStateOpt.ifPresent(finalState -> {
            System.out.println("\n处理流程:");
            finalState.messages().forEach(msg -> System.out.println("  " + msg));

            System.out.println("\n客服回复:");
            finalState.response().ifPresent(resp -> System.out.println("  " + resp));
        });
    }

}
