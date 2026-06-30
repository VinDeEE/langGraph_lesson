package com.example.routing;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 条件入口 Demo
 *
 * 核心知识点：
 * 1. 条件入口 = 根据输入动态选择起始节点
 * 2. 当前版本没有 addConditionalEntryPoint，用路由节点实现
 * 3. 与条件边的区别：条件边是中间路由，条件入口是起点路由
 *
 * 架构：
 *                    ┌─────────────────┐
 *               ┌───▶│ 投诉处理流程     │───┐
 *               │    └─────────────────┘   │
 *               │                          │
 * 用户输入 ──▶ 路由 ──┼───▶ 咨询处理流程     │───┼──▶ 输出
 *               │    └─────────────────┘   │
 *               │                          │
 *               │    ┌─────────────────┐   │
 *               └───▶│ 退换货处理流程   │───┘
 *                    └─────────────────┘
 *
 * 场景：客服系统根据用户输入类型，直接进入不同的处理流程
 */
public class ConditionalEntryDemo {

    private static final Logger log = LoggerFactory.getLogger(ConditionalEntryDemo.class);

    // 状态定义
    static class CustomerState extends AgentState {
        public static final String USER_INPUT = "userInput";
        public static final String REQUEST_TYPE = "requestType";
        public static final String RESPONSE = "response";
        public static final String MESSAGES = "messages";

        public static final Map<String, Channel<?>> SCHEMA = Map.of(
            USER_INPUT, Channels.base(() -> ""),
            REQUEST_TYPE, Channels.base(() -> ""),
            RESPONSE, Channels.base(() -> ""),
            MESSAGES, Channels.appender(ArrayList::new)
        );

        public CustomerState(Map<String, Object> initData) { super(initData); }
        public Optional<String> userInput() { return value(USER_INPUT); }
        public Optional<String> requestType() { return value(REQUEST_TYPE); }
        public Optional<String> response() { return value(RESPONSE); }
        public List<String> messages() { return this.<List<String>>value(MESSAGES).orElse(List.of()); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  条件入口 Demo");
        System.out.println("=".repeat(60));

        // 创建图
        StateGraph<CustomerState> graph = new StateGraph<>(
            CustomerState.SCHEMA,
            CustomerState::new
        )
            // 路由节点：根据输入判断类型
            .addNode("router", node_async(state -> {
                String input = state.userInput().orElse("");
                log.info(">>> [路由] 分析输入: {}", input);

                // 根据关键词判断请求类型
                String type = "general";
                if (input.contains("投诉") || input.contains("不满") || input.contains("差")) {
                    type = "complaint";
                } else if (input.contains("退货") || input.contains("换货") || input.contains("退款")) {
                    type = "return";
                } else if (input.contains("订单") || input.contains("物流") || input.contains("发货")) {
                    type = "order";
                }

                log.info(">>> [路由] 识别类型: {}", type);
                return Map.of(
                    CustomerState.REQUEST_TYPE, type,
                    CustomerState.MESSAGES, "【路由】识别为: " + type
                );
            }))

            // 投诉处理流程
            .addNode("complaint_receive", node_async(state -> {
                log.info(">>> [投诉] 接收投诉");
                return Map.of(CustomerState.MESSAGES, "【投诉】已接收投诉");
            }))
            .addNode("complaint_analyze", node_async(state -> {
                log.info(">>> [投诉] 分析投诉");
                return Map.of(CustomerState.MESSAGES, "【投诉】投诉分析完成");
            }))
            .addNode("complaint_resolve", node_async(state -> {
                log.info(">>> [投诉] 解决投诉");
                return Map.of(
                    CustomerState.RESPONSE, "您的投诉我们已受理，我们会尽快处理并给您满意的答复。",
                    CustomerState.MESSAGES, "【投诉】投诉已解决"
                );
            }))

            // 退换货处理流程
            .addNode("return_receive", node_async(state -> {
                log.info(">>> [退换货] 接收请求");
                return Map.of(CustomerState.MESSAGES, "【退换货】已接收请求");
            }))
            .addNode("return_check", node_async(state -> {
                log.info(">>> [退换货] 检查条件");
                return Map.of(CustomerState.MESSAGES, "【退换货】条件检查通过");
            }))
            .addNode("return_process", node_async(state -> {
                log.info(">>> [退换货] 处理退换货");
                return Map.of(
                    CustomerState.RESPONSE, "您的退换货申请已通过，请将商品寄回，我们收到后会尽快处理。",
                    CustomerState.MESSAGES, "【退换货】退换货已处理"
                );
            }))

            // 订单查询流程
            .addNode("order_query", node_async(state -> {
                log.info(">>> [订单] 查询订单");
                return Map.of(
                    CustomerState.RESPONSE, "您的订单已发货，预计明天到达，物流单号：SF1234567890",
                    CustomerState.MESSAGES, "【订单】订单查询完成"
                );
            }))

            // 通用咨询流程
            .addNode("general_consult", node_async(state -> {
                log.info(">>> [咨询] 处理咨询");
                return Map.of(
                    CustomerState.RESPONSE, "您好，请问有什么可以帮助您的？",
                    CustomerState.MESSAGES, "【咨询】咨询处理完成"
                );
            }))

            // 核心：路由节点 → 条件边 → 不同入口
            .addEdge(START, "router")
            .addConditionalEdges("router",
                edge_async(state -> {
                    String type = state.requestType().orElse("general");
                    log.info(">>> [条件入口] 路由到: {}", type);
                    return type;
                }),
                Map.of(
                    "complaint", "complaint_receive",
                    "return", "return_receive",
                    "order", "order_query",
                    "general", "general_consult"
                )
            )

            // 投诉流程内部
            .addEdge("complaint_receive", "complaint_analyze")
            .addEdge("complaint_analyze", "complaint_resolve")
            .addEdge("complaint_resolve", END)

            // 退换货流程内部
            .addEdge("return_receive", "return_check")
            .addEdge("return_check", "return_process")
            .addEdge("return_process", END)

            // 订单和咨询直接结束
            .addEdge("order_query", END)
            .addEdge("general_consult", END);

        CompiledGraph<CustomerState> compiledGraph = graph.compile();

        // 测试用例
        List<String> testInputs = List.of(
            "我要投诉！你们的服务太差了",
            "我想退货，买的东西有问题",
            "我的订单什么时候到？",
            "你好，请问有什么优惠活动？"
        );

        for (String input : testInputs) {
            System.out.println("\n" + "-".repeat(50));
            System.out.println("用户: " + input);
            System.out.println("-".repeat(50));

            Map<String, Object> inputs = Map.of(CustomerState.USER_INPUT, input);

            List<String> path = new ArrayList<>();
            for (var state : compiledGraph.stream(inputs)) {
                path.add(state.node());
            }
            System.out.println("执行路径: " + String.join(" → ", path));

            var result = compiledGraph.invoke(inputs);
            result.ifPresent(state -> {
                System.out.println("回复: " + state.response().orElse("无"));
            });
        }
    }
}
