package com.example.streaming;

import com.example.common.AppConfig;
import dev.langchain4j.model.openai.OpenAiChatModel;
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

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 流式输出 Demo
 *
 * 核心知识点：
 * 1. stream() 返回 AsyncGenerator，逐步产出每个节点执行后的状态
 * 2. 可以实时观察每个节点的执行过程和状态变化
 * 3. 适合用于 UI 更新、日志记录、调试
 *
 * 与 invoke() 的区别：
 * - stream(): 边执行边输出，实时性好
 * - invoke(): 全部执行完才返回，简单直接
 */
public class StreamingDemo {

    private static final Logger log = LoggerFactory.getLogger(StreamingDemo.class);

    // 简化的状态定义
    static class SimpleState extends AgentState {
        public static final String INPUT = "input";
        public static final String STEP1_RESULT = "step1Result";
        public static final String STEP2_RESULT = "step2Result";
        public static final String FINAL = "final";
        public static final String MESSAGES = "messages";

        public static final Map<String, Channel<?>> SCHEMA = Map.of(
            INPUT, Channels.base(() -> ""),
            STEP1_RESULT, Channels.base(() -> ""),
            STEP2_RESULT, Channels.base(() -> ""),
            FINAL, Channels.base(() -> ""),
            MESSAGES, Channels.appender(ArrayList::new)
        );

        public SimpleState(Map<String, Object> initData) { super(initData); }
        public Optional<String> input() { return value(INPUT); }
        public Optional<String> step1Result() { return value(STEP1_RESULT); }
        public Optional<String> step2Result() { return value(STEP2_RESULT); }
        public Optional<String> finalResult() { return value(FINAL); }
        public List<String> messages() { return this.<List<String>>value(MESSAGES).orElse(List.of()); }
    }

    public static void main(String[] args) throws Exception {
        // 不需要 LLM，纯流程演示
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  流式输出 Demo");
        System.out.println("=".repeat(60));

        // 创建图
        StateGraph<SimpleState> graph = new StateGraph<>(
            SimpleState.SCHEMA,
            SimpleState::new
        )
            .addNode("step1", node_async(state -> {
                log.info(">>> [Step1] 执行中...");
                Thread.sleep(100);
                return Map.of(
                    SimpleState.STEP1_RESULT, "Step1完成",
                    SimpleState.MESSAGES, "【Step1】处理完成"
                );
            }))
            .addNode("step2", node_async(state -> {
                log.info(">>> [Step2] 执行中...");
                Thread.sleep(150);
                return Map.of(
                    SimpleState.STEP2_RESULT, "Step2完成",
                    SimpleState.MESSAGES, "【Step2】处理完成"
                );
            }))
            .addNode("step3", node_async(state -> {
                log.info(">>> [Step3] 执行中...");
                Thread.sleep(80);
                return Map.of(
                    SimpleState.FINAL, "全部完成",
                    SimpleState.MESSAGES, "【Step3】最终处理"
                );
            }))
            .addEdge(START, "step1")
            .addEdge("step1", "step2")
            .addEdge("step2", "step3")
            .addEdge("step3", END);

        CompiledGraph<SimpleState> compiledGraph = graph.compile();

        Map<String, Object> inputs = Map.of(SimpleState.INPUT, "测试输入");

        // ========== 核心：stream() 流式执行 ==========
        System.out.println("\n--- stream() 流式执行 ---\n");

        int step = 0;
        for (var output : compiledGraph.stream(inputs)) {
            step++;
            // output 包含：
            // - node(): 执行完成的节点名
            // - state(): 当前状态的快照
            System.out.printf("步骤 %d: 节点 [%s] 执行完成%n", step, output.node());
            System.out.printf("  状态: %s%n", output.state().data());
            System.out.println();
        }

        // ========== 对比：invoke() 一次性执行 ==========
        System.out.println("--- invoke() 一次性执行 ---\n");

        var finalState = compiledGraph.invoke(inputs);
        finalState.ifPresent(state -> {
            System.out.println("最终状态: " + state.data());
        });

        // ========== 实际应用场景：实时进度更新 ==========
        System.out.println("\n--- 实际应用：实时进度更新 ---\n");

        runWithProgress(compiledGraph, inputs);
    }

    /**
     * 实际应用场景：用 stream() 实现实时进度更新
     */
    static void runWithProgress(CompiledGraph<SimpleState> graph, Map<String, Object> inputs) {
        Map<String, String> nodeDescriptions = Map.of(
            "step1", "正在分析输入...",
            "step2", "正在处理数据...",
            "step3", "正在生成结果..."
        );

        System.out.println("开始处理:");
        System.out.println("[          ] 0%");

        int totalSteps = 3;
        int currentStep = 0;

        for (var output : graph.stream(inputs)) {
            currentStep++;
            String node = output.node();
            String desc = nodeDescriptions.getOrDefault(node, "处理中...");
            int progress = (currentStep * 100) / totalSteps;

            // 生成进度条
            int filled = Math.max(0, Math.min(10, progress / 10));
            int empty = 10 - filled;
            String progressBar = "█".repeat(filled) + "░".repeat(empty);

            System.out.printf("\r[%s] %d%% - %s", progressBar, progress, desc);
        }

        System.out.println("\n处理完成！");
    }
}
