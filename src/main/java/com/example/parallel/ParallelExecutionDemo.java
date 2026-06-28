package com.example.parallel;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 并行执行 Demo
 *
 * 核心知识点：
 * 1. 多个节点从同一个起点出发，同时执行
 * 2. 所有并行节点完成后，汇聚到同一个节点
 * 3. 汇聚节点可以读取所有并行节点的结果
 *
 * 架构：
 *                    ┌─ 知识库查询 ─┐
 *                    │              │
 * 用户查询 → 分发 ──┼─ 历史查询  ──┼─→ 合并结果 → 生成回复 → END
 *                    │              │
 *                    └─ 推荐查询  ─┘
 *
 *                   ◀── 并行执行 ──▶
 *
 * 场景：用户提问时，同时查询多个数据源，合并后生成更全面的回复
 */
public class ParallelExecutionDemo {

    private static final Logger log = LoggerFactory.getLogger(ParallelExecutionDemo.class);

    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  并行执行 Demo");
        System.out.println("=".repeat(60));

        // 创建图 - 不需要 LLM，纯流程演示
        StateGraph<ParallelDemoState> graph = new StateGraph<>(
            ParallelDemoState.SCHEMA,
            ParallelDemoState::new
        )
            // 添加节点
            .addNode("dispatch", node_async(new DispatchNode()))        // 分发节点
            .addNode("query_knowledge", node_async(new QueryKnowledgeNode()))  // 知识库查询
            .addNode("query_history", node_async(new QueryHistoryNode()))      // 历史记录查询
            .addNode("query_recommend", node_async(new QueryRecommendNode())) // 推荐查询
            .addNode("merge_results", node_async(new MergeResultsNode()))      // 合并结果
            .addNode("generate_response", node_async(new GenerateResponseNode())) // 生成回复

            // 关键：并行边的设置
            // 1. 分发节点 → 同时指向多个并行节点
            .addEdge(START, "dispatch")
            .addEdge("dispatch", "query_knowledge")   // 分发 → 知识库
            .addEdge("dispatch", "query_history")     // 分发 → 历史
            .addEdge("dispatch", "query_recommend")   // 分发 → 推荐

            // 2. 所有并行节点 → 汇聚到同一个节点
            .addEdge("query_knowledge", "merge_results")  // 知识库 → 合并
            .addEdge("query_history", "merge_results")    // 历史 → 合并
            .addEdge("query_recommend", "merge_results")  // 推荐 → 合并

            // 3. 合并后继续
            .addEdge("merge_results", "generate_response")
            .addEdge("generate_response", END);

        // 需要在 compile 时配置并发数
//        CompileConfig config = CompileConfig.builder()
//                .maxConcurrency(10)  // 最大并发数
//                .build();

//        CompiledGraph<ParallelDemoState> compiledGraph = graph.compile(config);
        CompiledGraph<ParallelDemoState> compiledGraph = graph.compile();

        // 执行
        Map<String, Object> inputs = Map.of(
            ParallelDemoState.USER_QUERY, "推荐一款适合初学者的钢琴"
        );

        System.out.println("\n用户查询: 推荐一款适合初学者的钢琴");
        System.out.println("\n执行流程（注意并行节点的执行顺序）:\n");

        List<String> executionPath = new ArrayList<>();
        for (var state : compiledGraph.stream(inputs)) {
            executionPath.add(state.node());
            log.info("节点 [{}] 执行完成", state.node());
        }

        System.out.println("\n执行路径: " + String.join(" → ", executionPath));

        // 输出最终结果
        var finalState = compiledGraph.invoke(inputs);
        finalState.ifPresent(state -> {
            System.out.println("\n" + "=".repeat(50));
            System.out.println("各数据源查询结果:");
            System.out.println("=".repeat(50));
            System.out.println("知识库: " + state.knowledgeResult().orElse("N/A"));
            System.out.println("历史记录: " + state.historyResult().orElse("N/A"));
            System.out.println("推荐: " + state.recommendResult().orElse("N/A"));

            System.out.println("\n" + "=".repeat(50));
            System.out.println("合并后结果:");
            System.out.println("=".repeat(50));
            System.out.println(state.mergedResult().orElse("N/A"));

            System.out.println("\n" + "=".repeat(50));
            System.out.println("最终回复:");
            System.out.println("=".repeat(50));
            System.out.println(state.finalResponse().orElse("N/A"));
        });
    }

    // ========== 节点实现 ==========

    /**
     * 分发节点
     * 核心点：这个节点执行后，会同时触发多个后续节点
     */
    static class DispatchNode implements NodeAction<ParallelDemoState> {
        @Override
        public Map<String, Object> apply(ParallelDemoState state) throws Exception {
            String query = state.userQuery().orElse("");
            log.info(">>> [分发] 收到查询: {}, 开始并行分发", query);
            return Map.of(
                ParallelDemoState.MESSAGES, "【分发】查询已分发到多个数据源"
            );
        }
    }

    /**
     * 知识库查询节点（并行节点1）
     * 模拟耗时操作
     */
    static class QueryKnowledgeNode implements NodeAction<ParallelDemoState> {
        @Override
        public Map<String, Object> apply(ParallelDemoState state) throws Exception {
            log.info(">>> [知识库] 开始查询...");
            Thread.sleep(100); // 模拟耗时
            log.info(">>> [知识库] 查询完成");
            return Map.of(
                ParallelDemoState.KNOWLEDGE_RESULT, "雅马哈P45适合初学者，88键配重键盘，价格约3000元",
                ParallelDemoState.MESSAGES, "【知识库】查询完成"
            );
        }
    }

    /**
     * 历史记录查询节点（并行节点2）
     * 模拟耗时操作
     */
    static class QueryHistoryNode implements NodeAction<ParallelDemoState> {
        @Override
        public Map<String, Object> apply(ParallelDemoState state) throws Exception {
            log.info(">>> [历史] 开始查询...");
            Thread.sleep(150); // 模拟耗时（比知识库慢）
            log.info(">>> [历史] 查询完成");
            return Map.of(
                ParallelDemoState.HISTORY_RESULT, "用户之前咨询过吉他，对雅马哈品牌有好感",
                ParallelDemoState.MESSAGES, "【历史】查询完成"
            );
        }
    }

    /**
     * 推荐查询节点（并行节点3）
     * 模拟耗时操作
     */
    static class QueryRecommendNode implements NodeAction<ParallelDemoState> {
        @Override
        public Map<String, Object> apply(ParallelDemoState state) throws Exception {
            log.info(">>> [推荐] 开始查询...");
            Thread.sleep(80); // 模拟耗时（最快）
            log.info(">>> [推荐] 查询完成");
            return Map.of(
                ParallelDemoState.RECOMMEND_RESULT, "热销榜：雅马哈P45、卡西欧PX-S1100、罗兰FP-30X",
                ParallelDemoState.MESSAGES, "【推荐】查询完成"
            );
        }
    }

    /**
     * 合并结果节点
     * 核心点：这个节点会等待所有并行节点完成后才执行
     * 可以读取所有并行节点写入的状态
     */
    static class MergeResultsNode implements NodeAction<ParallelDemoState> {
        @Override
        public Map<String, Object> apply(ParallelDemoState state) throws Exception {
            log.info(">>> [合并] 所有并行节点完成，开始合并结果");

            String knowledge = state.knowledgeResult().orElse("");
            String history = state.historyResult().orElse("");
            String recommend = state.recommendResult().orElse("");

            String merged = String.format("""
                [知识库] %s
                [用户历史] %s
                [热销推荐] %s
                """, knowledge, history, recommend);

            return Map.of(
                ParallelDemoState.MERGED_RESULT, merged,
                ParallelDemoState.MESSAGES, "【合并】结果已合并"
            );
        }
    }

    /**
     * 生成回复节点
     */
    static class GenerateResponseNode implements NodeAction<ParallelDemoState> {
        @Override
        public Map<String, Object> apply(ParallelDemoState state) throws Exception {
            log.info(">>> [回复] 生成最终回复");

            String response = "根据您的需求，推荐雅马哈P45电子钢琴。" +
                "这款琴专为初学者设计，88键配重键盘手感接近真琴，价格约3000元，性价比很高。" +
                "您之前咨询过吉他，雅马哈的品质值得信赖。目前这款琴在热销榜排名前三。";

            return Map.of(
                ParallelDemoState.FINAL_RESPONSE, response,
                ParallelDemoState.MESSAGES, "【回复】已生成"
            );
        }
    }
}
