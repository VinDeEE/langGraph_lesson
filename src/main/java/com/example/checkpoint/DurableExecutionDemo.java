package com.example.checkpoint;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * Durable Execution Demo - 崩溃恢复演示
 *
 * 场景：订单处理流程
 * 创建订单 → 扣库存 → 支付 → 发货 → 完成
 *
 * 演示：
 * 1. 第一次执行：在"支付"步骤崩溃
 * 2. 第二次执行：自动从"支付"步骤恢复，跳过已完成的步骤
 *
 * 核心价值：
 * - 不需要手动记录执行到哪一步
 * - 不需要手动判断哪些步骤已完成
 * - 框架自动处理恢复逻辑
 */
public class DurableExecutionDemo {

    private static final Logger log = LoggerFactory.getLogger(DurableExecutionDemo.class);

    // 状态定义
    static class OrderState extends AgentState {
        public static final String ORDER_ID = "orderId";
        public static final String CURRENT_STEP = "currentStep";
        public static final String COMPLETED_STEPS = "completedSteps";
        public static final String RESULT = "result";
        public static final String MESSAGES = "messages";

        // 模拟崩溃控制
        public static final String CRASH_AT_STEP = "crashAtStep";

        public static final Map<String, Channel<?>> SCHEMA = Map.of(
            ORDER_ID, Channels.base(() -> ""),
            CURRENT_STEP, Channels.base(() -> ""),
            COMPLETED_STEPS, Channels.base(() -> ""),
            RESULT, Channels.base(() -> ""),
            MESSAGES, Channels.appender(ArrayList::new),
            CRASH_AT_STEP, Channels.base(() -> "")
        );

        public OrderState(Map<String, Object> initData) { super(initData); }
        public Optional<String> orderId() { return value(ORDER_ID); }
        public Optional<String> currentStep() { return value(CURRENT_STEP); }
        public Optional<String> completedSteps() { return value(COMPLETED_STEPS); }
        public Optional<String> result() { return value(RESULT); }
        public Optional<String> crashAtStep() { return value(CRASH_AT_STEP); }
        public List<String> messages() { return this.<List<String>>value(MESSAGES).orElse(List.of()); }
    }

    // 记录每个步骤是否执行过
    static Map<String, Boolean> stepExecuted = new HashMap<>();

    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  Durable Execution - 崩溃恢复演示");
        System.out.println("=".repeat(60));

        // 创建 MemorySaver
        MemorySaver saver = new MemorySaver();

        // 创建订单处理流程
        StateGraph<OrderState> stateGraph = new StateGraph<>(
            OrderState.SCHEMA,
            OrderState::new
        )
            .addNode("create_order", node_async(state -> {
                return executeStep(state, "create_order", "创建订单");
            }))
            .addNode("deduct_inventory", node_async(state -> {
                return executeStep(state, "deduct_inventory", "扣减库存");
            }))
            .addNode("process_payment", node_async(state -> {
                return executeStep(state, "process_payment", "处理支付");
            }))
            .addNode("ship_order", node_async(state -> {
                return executeStep(state, "ship_order", "发货");
            }))
            .addNode("complete", node_async(state -> {
                return executeStep(state, "complete", "完成");
            }))
            .addEdge(START, "create_order")
            .addEdge("create_order", "deduct_inventory")
            .addEdge("deduct_inventory", "process_payment")
            .addEdge("process_payment", "ship_order")
            .addEdge("ship_order", "complete")
            .addEdge("complete", END);

        // 编译（配置检查点）
        CompileConfig compileConfig = CompileConfig.builder()
            .checkpointSaver(saver)
            .build();

        CompiledGraph<OrderState> graph = stateGraph.compile(compileConfig);

        String threadId = "order-001";

        // ========== 第一次执行：在"支付"步骤崩溃 ==========
        System.out.println("\n" + "=".repeat(50));
        System.out.println("第一次执行（模拟在「支付」步骤崩溃）");
        System.out.println("=".repeat(50));

        stepExecuted.clear();  // 重置执行记录

        Map<String, Object> inputs1 = Map.of(
            OrderState.ORDER_ID, "ORD-2024-001",
            OrderState.CRASH_AT_STEP, "process_payment"  // 在这里崩溃
        );

        RunnableConfig config = RunnableConfig.builder()
            .threadId(threadId)
            .build();

        try {
            graph.invoke(inputs1, config);
        } catch (Exception e) {
            System.out.println("\n💥 程序崩溃了！异常: " + e.getMessage());
        }

        // 查看已保存的状态
        System.out.println("\n--- 已保存的检查点状态 ---");
        printCheckpointState(graph, config);

        // ========== 第二次执行：自动恢复 ==========
        System.out.println("\n" + "=".repeat(50));
        System.out.println("第二次执行（自动从「支付」步骤恢复）");
        System.out.println("=".repeat(50));

        stepExecuted.clear();  // 重置执行记录

        // 核心：先更新状态，移除崩溃标记
        Map<String, Object> updateData = Map.of(
            OrderState.CRASH_AT_STEP, ""  // 不再崩溃
        );
        graph.updateState(config, updateData);

        // 使用 GraphInput.resume() 恢复执行
        // 框架会从最后的检查点继续，跳过已完成的步骤
        graph.invoke(GraphInput.resume(), config);

        System.out.println("\n✅ 订单处理完成！");
    }

    /**
     * 执行步骤（带崩溃模拟）
     */
    static Map<String, Object> executeStep(OrderState state, String stepName, String stepDesc) throws Exception {
        String crashAt = state.crashAtStep().orElse("");

        // 检查是否需要在这个步骤崩溃
        if (stepName.equals(crashAt)) {
            log.info(">>> [{}] 模拟崩溃！", stepName);
            throw new RuntimeException("模拟崩溃: " + stepName + " 执行失败");
        }

        // 记录执行
        stepExecuted.put(stepName, true);
        log.info(">>> [{}] {}", stepName, stepDesc);

        // 模拟耗时
        Thread.sleep(100);

        String completed = state.completedSteps().orElse("");
        String newCompleted = completed.isEmpty() ? stepName : completed + "," + stepName;

        return Map.of(
            OrderState.CURRENT_STEP, stepName,
            OrderState.COMPLETED_STEPS, newCompleted,
            OrderState.RESULT, stepDesc + " 完成",
            OrderState.MESSAGES, "【" + stepName + "】" + stepDesc + " - 已完成"
        );
    }

    /**
     * 打印检查点状态
     */
    static void printCheckpointState(CompiledGraph<OrderState> graph, RunnableConfig config) {
        try {
            StateSnapshot<OrderState> snapshot = graph.getState(config);
            System.out.println("当前步骤: " + snapshot.state().currentStep().orElse("无"));
            System.out.println("已完成步骤: " + snapshot.state().completedSteps().orElse("无"));
            System.out.println("执行日志:");
            snapshot.state().messages().forEach(msg -> System.out.println("  " + msg));
        } catch (Exception e) {
            System.out.println("暂无检查点数据");
        }
    }
}
