package com.example.observability;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.hook.EdgeHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 可观测性 Demo
 *
 * 核心知识点：
 * 1. NodeHook - 节点执行钩子（前置/后置）
 * 2. EdgeHook - 边执行钩子
 * 3. 执行追踪 - 记录每个节点的执行时间
 * 4. 性能监控 - 统计各节点耗时
 * 5. 错误追踪 - 捕获和记录异常
 *
 * 生产环境中，这些数据会发送到：
 * - Prometheus（指标）
 * - Jaeger/Zipkin（链路追踪）
 * - ELK（日志）
 */
public class ObservabilityDemo {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityDemo.class);

    // ========== 执行追踪器 ==========
    static class ExecutionTracer {
        private final List<TraceRecord> records = new ArrayList<>();
        private final Map<String, Long> nodeStartTimes = new ConcurrentHashMap<>();

        static class TraceRecord {
            String nodeName;
            long startTime;
            long endTime;
            long duration;
            boolean success;
            String error;

            TraceRecord(String nodeName, long startTime, long endTime, boolean success, String error) {
                this.nodeName = nodeName;
                this.startTime = startTime;
                this.endTime = endTime;
                this.duration = endTime - startTime;
                this.success = success;
                this.error = error;
            }

            @Override
            public String toString() {
                return String.format("[%s] %dms %s %s",
                    nodeName, duration, success ? "✓" : "✗",
                    error != null ? "(" + error + ")" : "");
            }
        }

        void startNode(String nodeName) {
            nodeStartTimes.put(nodeName, System.currentTimeMillis());
            log.debug(">>> [追踪] 节点开始: {}", nodeName);
        }

        void endNode(String nodeName, boolean success, String error) {
            long endTime = System.currentTimeMillis();
            long startTime = nodeStartTimes.getOrDefault(nodeName, endTime);
            records.add(new TraceRecord(nodeName, startTime, endTime, success, error));
            log.debug(">>> [追踪] 节点结束: {} ({}ms)", nodeName, endTime - startTime);
        }

        void printSummary() {
            System.out.println("\n" + "=".repeat(50));
            System.out.println("执行追踪摘要");
            System.out.println("=".repeat(50));

            long totalTime = 0;
            for (TraceRecord record : records) {
                System.out.println("  " + record);
                totalTime += record.duration;
            }

            System.out.println("-".repeat(50));
            System.out.printf("总耗时: %dms%n", totalTime);

            // 统计
            long successCount = records.stream().filter(r -> r.success).count();
            long failCount = records.stream().filter(r -> !r.success).count();
            System.out.printf("成功: %d, 失败: %d%n", successCount, failCount);

            // 最慢的节点
            records.stream()
                .max(Comparator.comparingLong(r -> r.duration))
                .ifPresent(r -> System.out.println("最慢节点: " + r.nodeName + " (" + r.duration + "ms)"));
        }

        List<TraceRecord> getRecords() { return records; }
    }

    // ========== 状态定义 ==========
    static class DemoState extends AgentState {
        public static final String INPUT = "input";
        public static final String RESULT = "result";
        public static final String MESSAGES = "messages";

        public static final Map<String, Channel<?>> SCHEMA = Map.of(
            INPUT, Channels.base(() -> ""),
            RESULT, Channels.base(() -> ""),
            MESSAGES, Channels.appender(ArrayList::new)
        );

        public DemoState(Map<String, Object> initData) { super(initData); }
        public Optional<String> input() { return value(INPUT); }
        public Optional<String> result() { return value(RESULT); }
        public List<String> messages() { return this.<List<String>>value(MESSAGES).orElse(List.of()); }
    }

    // ========== 主程序 ==========
    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  可观测性 Demo");
        System.out.println("=".repeat(60));

        ExecutionTracer tracer = new ExecutionTracer();

        // 创建图
        StateGraph<DemoState> graph = new StateGraph<>(
            DemoState.SCHEMA,
            DemoState::new
        )
            .addNode("step1", node_async(state -> {
                log.info(">>> [Step1] 执行中...");
                Thread.sleep(100); // 模拟耗时
                return Map.of(
                    DemoState.RESULT, "Step1完成",
                    DemoState.MESSAGES, "【Step1】处理完成"
                );
            }))
            .addNode("step2", node_async(state -> {
                log.info(">>> [Step2] 执行中...");
                Thread.sleep(150); // 模拟耗时
                return Map.of(
                    DemoState.RESULT, "Step2完成",
                    DemoState.MESSAGES, "【Step2】处理完成"
                );
            }))
            .addNode("step3", node_async(state -> {
                log.info(">>> [Step3] 执行中...");
                Thread.sleep(80); // 模拟耗时
                return Map.of(
                    DemoState.RESULT, "Step3完成",
                    DemoState.MESSAGES, "【Step3】处理完成"
                );
            }))
            .addEdge(START, "step1")
            .addEdge("step1", "step2")
            .addEdge("step2", "step3")
            .addEdge("step3", END);

        // 添加钩子（可观测性）
        graph.addBeforeCallNodeHook((nodeId, state, config) -> {
            tracer.startNode(nodeId);
            return java.util.concurrent.CompletableFuture.completedFuture(Map.of());
        });

        graph.addAfterCallNodeHook((nodeId, state, config, result) -> {
            tracer.endNode(nodeId, true, null);
            return java.util.concurrent.CompletableFuture.completedFuture(Map.of());
        });

        CompiledGraph<DemoState> compiledGraph = graph.compile();

        // 执行
        Map<String, Object> inputs = Map.of(DemoState.INPUT, "测试输入");

        System.out.println("\n执行流程:");
        for (var state : compiledGraph.stream(inputs)) {
            log.info("节点 [{}] 执行完成", state.node());
        }

        // 打印追踪摘要
        tracer.printSummary();

        // ========== 演示：带错误追踪 ==========
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  带错误追踪的执行");
        System.out.println("=".repeat(60));

        ExecutionTracer errorTracer = new ExecutionTracer();

        StateGraph<DemoState> errorGraph = new StateGraph<>(
            DemoState.SCHEMA,
            DemoState::new
        )
            .addNode("success_step", node_async(state -> {
                log.info(">>> [成功步骤] 执行中...");
                Thread.sleep(50);
                return Map.of(DemoState.MESSAGES, "【成功】处理完成");
            }))
            .addNode("error_step", node_async(state -> {
                log.info(">>> [错误步骤] 执行中...");
                Thread.sleep(30);
                throw new RuntimeException("模拟错误：数据库连接失败");
            }))
            .addNode("recovery_step", node_async(state -> {
                log.info(">>> [恢复步骤] 执行中...");
                return Map.of(DemoState.MESSAGES, "【恢复】错误已恢复");
            }))
            .addEdge(START, "success_step")
            .addEdge("success_step", "error_step")
            .addEdge("error_step", END);

        // 添加带错误处理的钩子
        errorGraph.addBeforeCallNodeHook((nodeId, state, config) -> {
            errorTracer.startNode(nodeId);
            return java.util.concurrent.CompletableFuture.completedFuture(Map.of());
        });

        errorGraph.addAfterCallNodeHook((nodeId, state, config, result) -> {
            errorTracer.endNode(nodeId, true, null);
            return java.util.concurrent.CompletableFuture.completedFuture(Map.of());
        });

        CompiledGraph<DemoState> errorCompiled = errorGraph.compile();

        try {
            for (var state : errorCompiled.stream(Map.of(DemoState.INPUT, "测试"))) {
                log.info("节点 [{}] 执行完成", state.node());
            }
        } catch (Exception e) {
            log.error("执行异常: {}", e.getMessage());
            errorTracer.endNode("error_step", false, e.getMessage());
        }

        errorTracer.printSummary();
    }
}
