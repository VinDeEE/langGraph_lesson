package com.example.streaming;

import com.example.common.AppConfig;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * LLM 流式输出 Demo
 *
 * 核心知识点：
 * 1. 使用 OpenAiStreamingChatModel 实现逐 token 输出
 * 2. StreamingChatResponseHandler 接收每个 token
 * 3. 结合 stream() 实现完整的流式处理链
 *
 * 效果：LLM 每生成一个 token，就立即输出，而不是等全部生成完
 */
public class LLMStreamingDemo {

    private static final Logger log = LoggerFactory.getLogger(LLMStreamingDemo.class);

    // 状态定义
    static class StreamState extends AgentState {
        public static final String USER_INPUT = "userInput";
        public static final String FULL_RESPONSE = "fullResponse";
        public static final String MESSAGES = "messages";

        public static final Map<String, Channel<?>> SCHEMA = Map.of(
            USER_INPUT, Channels.base(() -> ""),
            FULL_RESPONSE, Channels.base(() -> ""),
            MESSAGES, Channels.appender(ArrayList::new)
        );

        public StreamState(Map<String, Object> initData) { super(initData); }
        public Optional<String> userInput() { return value(USER_INPUT); }
        public Optional<String> fullResponse() { return value(FULL_RESPONSE); }
        public List<String> messages() { return this.<List<String>>value(MESSAGES).orElse(List.of()); }
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

        // 核心：使用 OpenAiStreamingChatModel 而不是 OpenAiChatModel
        StreamingChatLanguageModel streamingModel = OpenAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .modelName(modelName)
            .timeout(Duration.ofSeconds(60))
            .build();

        System.out.println("\n" + "=".repeat(60));
        System.out.println("  LLM 流式输出 Demo");
        System.out.println("=".repeat(60));

        // ========== 示例1：直接使用流式 API ==========
        System.out.println("\n--- 示例1：直接流式调用 ---\n");
        directStreaming(streamingModel);

        // ========== 示例2：结合 LangGraph ==========
        System.out.println("\n\n--- 示例2：结合 LangGraph 的 stream() ---\n");
        streamingWithGraph(streamingModel);

        // 强制退出（OkHttp 连接池会阻止 JVM 退出）
        System.exit(0);
    }

    /**
     * 示例1：直接使用流式 API
     */
    static void directStreaming(StreamingChatLanguageModel model) throws Exception {
        String userMessage = "用一句话介绍什么是Java虚拟机";

        System.out.println("用户: " + userMessage);
        System.out.print("\nAI: ");

        // 核心：用 CompletableFuture 等待流式输出完成
        CompletableFuture<String> future = new CompletableFuture<>();

        // 核心：generate() 方法传入 StreamingResponseHandler
        model.generate(
            UserMessage.from(userMessage),
            new StreamingResponseHandler() {
                StringBuilder fullResponse = new StringBuilder();

                @Override
                public void onNext(String token) {
                    // 核心：每收到一个 token 就立即输出
                    System.out.print(token);
                    fullResponse.append(token);
                }

                @Override
                public void onComplete(Response response) {
                    // 流式输出完成
                    System.out.println();
                    future.complete(fullResponse.toString());
                }

                @Override
                public void onError(Throwable error) {
                    System.err.println("\n错误: " + error.getMessage());
                    future.completeExceptionally(error);
                }
            }
        );

        // 等待流式输出完成
        future.get();
    }

    /**
     * 示例2：结合 LangGraph 的 stream()
     */
    static void streamingWithGraph(StreamingChatLanguageModel model) throws Exception {
        // 创建包含 LLM 调用的图
        StateGraph<StreamState> graph = new StateGraph<>(
            StreamState.SCHEMA,
            StreamState::new
        )
            .addNode("prepare", node_async(state -> {
                log.info(">>> [准备] 预处理输入");
                String input = state.userInput().orElse("");
                return Map.of(
                    StreamState.MESSAGES, "【准备】输入已处理"
                );
            }))
            .addNode("llm_call", node_async(state -> {
                String input = state.userInput().orElse("");
                log.info(">>> [LLM] 开始流式生成...");

                // 核心：在节点内部使用流式 API
                CompletableFuture<String> future = new CompletableFuture<>();
                StringBuilder fullResponse = new StringBuilder();

                model.generate(
                    UserMessage.from(input),
                    new StreamingResponseHandler() {
                        @Override
                        public void onNext(String token) {
                            // 每个 token 实时输出
                            System.out.print(token);
                            fullResponse.append(token);
                        }

                        @Override
                        public void onComplete(Response response) {
                            future.complete(fullResponse.toString());
                        }

                        @Override
                        public void onError(Throwable error) {
                            future.completeExceptionally(error);
                        }
                    }
                );

                // 等待完成
                String result = future.get();
                return Map.of(
                    StreamState.FULL_RESPONSE, result,
                    StreamState.MESSAGES, "【LLM】生成完成"
                );
            }))
            .addNode("finish", node_async(state -> {
                log.info(">>> [完成] 处理结束");
                return Map.of(StreamState.MESSAGES, "【完成】流程结束");
            }))
            .addEdge(START, "prepare")
            .addEdge("prepare", "llm_call")
            .addEdge("llm_call", "finish")
            .addEdge("finish", END);

        CompiledGraph<StreamState> compiledGraph = graph.compile();

        // 使用 graph.stream() 观察每个节点的执行
        Map<String, Object> inputs = Map.of(
            StreamState.USER_INPUT, "用一句话介绍什么是设计模式"
        );

        System.out.println("用户: 用一句话介绍什么是设计模式");
        System.out.print("\nAI: ");

        // stream() 可以看到每个节点的执行时机
        for (var output : compiledGraph.stream(inputs)) {
            if (!output.node().equals("llm_call") && !output.node().equals("__END__")) {
                // 非 LLM 节点的日志（LLM 节点的输出已经在 handler 中打印了）
                log.info("节点 [{}] 执行完成", output.node());
            }
        }

        // 获取最终结果
        var finalState = compiledGraph.invoke(inputs);
        finalState.ifPresent(state -> {
            System.out.println("\n\n最终回复长度: " + state.fullResponse().map(String::length).orElse(0) + " 字符");
        });
    }
}
