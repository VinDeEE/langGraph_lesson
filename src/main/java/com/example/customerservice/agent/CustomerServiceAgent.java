package com.example.customerservice.agent;

import com.example.customerservice.hook.ExecutionTracer;
import com.example.customerservice.service.*;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.CompiledGraph;
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
 * 客服 Agent - 核心编排逻辑
 *
 * 架构：
 * ┌─────────────────────────────────────────────────────────────────┐
│ │                     ReACT 循环                                   │
│ │                                                                 │
│ │   用户输入 ──▶ 意图分析 ──▶ 路由决策 ──▶ 工具调用 ──▶ 结果评估    │
│ │       ▲                                            │            │
│ │       └────────────────────────────────────────────┘            │
│ │                                                                 │
│ │   分支1: 查询订单                                                │
│ │   分支2: 反馈丢件                                                │
│ │   分支3: 投诉多收费                                              │
│ │                                                                 │
└─────────────────────────────────────────────────────────────────┘
 */
public class CustomerServiceAgent {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceAgent.class);

    private final ChatLanguageModel chatModel;
    private final OrderService orderService;
    private final DeliveryService deliveryService;
    private final ComplaintService complaintService;
    private final WorkOrderService workOrderService;
    private final ExecutionTracer tracer;
    private final CustomerServiceAssistant assistant;

    // 状态定义
    static class ServiceState extends AgentState {
        public static final String SESSION_ID = "sessionId";
        public static final String USER_ID = "userId";
        public static final String USER_INPUT = "userInput";
        public static final String INTENT = "intent";
        public static final String CONTEXT = "context";
        public static final String RESPONSE = "response";
        public static final String MESSAGES = "messages";
        public static final String NEXT_ACTION = "nextAction";

        public static final Map<String, Channel<?>> SCHEMA = Map.of(
            SESSION_ID, Channels.base(() -> ""),
            USER_ID, Channels.base(() -> ""),
            USER_INPUT, Channels.base(() -> ""),
            INTENT, Channels.base(() -> ""),
            CONTEXT, Channels.base(() -> ""),
            RESPONSE, Channels.base(() -> ""),
            MESSAGES, Channels.appender(ArrayList::new),
            NEXT_ACTION, Channels.base(() -> "")
        );

        public ServiceState(Map<String, Object> initData) { super(initData); }

        public Optional<String> sessionId() { return value(SESSION_ID); }
        public Optional<String> userId() { return value(USER_ID); }
        public Optional<String> userInput() { return value(USER_INPUT); }
        public Optional<String> intent() { return value(INTENT); }
        public Optional<String> context() { return value(CONTEXT); }
        public Optional<String> response() { return value(RESPONSE); }
        public List<String> messages() { return this.<List<String>>value(MESSAGES).orElse(List.of()); }
        public Optional<String> nextAction() { return value(NEXT_ACTION); }
    }

    // AI 助手接口
    interface CustomerServiceAssistant {
        String chat(@MemoryId String sessionId, @UserMessage String message);
    }

    public CustomerServiceAgent(ChatLanguageModel chatModel,
                                 OrderService orderService,
                                 DeliveryService deliveryService,
                                 ComplaintService complaintService,
                                 WorkOrderService workOrderService,
                                 ExecutionTracer tracer) {
        this.chatModel = chatModel;
        this.orderService = orderService;
        this.deliveryService = deliveryService;
        this.complaintService = complaintService;
        this.workOrderService = workOrderService;
        this.tracer = tracer;

        // 创建 AI 助手（带工具）
        this.assistant = AiServices.builder(CustomerServiceAssistant.class)
            .chatLanguageModel(chatModel)
            .chatMemoryProvider(memoryId ->
                MessageWindowChatMemory.builder()
                    .maxMessages(20)
                    .build()
            )
            .tools(new CustomerServiceTools())
            .build();

        log.info("客服 Agent 初始化完成");
    }

    /**
     * 工具类 - 定义所有可用工具
     */
    class CustomerServiceTools {

        @Tool("验证用户信息，需要手机号和姓名")
        public String validateUser(String phone, String name) {
            tracer.startTrace("session", "tool", "validateUser");
            boolean valid = orderService.validateUser(phone, name);
            tracer.endTrace("session", "tool", "validateUser", valid,
                valid ? "验证通过" : "验证失败");
            return valid ? "用户验证通过" : "用户验证失败，请检查手机号和姓名";
        }

        @Tool("根据手机号查询用户订单列表")
        public String queryOrders(String phone) {
            tracer.startTrace("session", "tool", "queryOrders");
            var orders = orderService.queryOrdersByPhone(phone);
            tracer.endTrace("session", "tool", "queryOrders", true,
                "查询到" + orders.size() + "个订单");

            if (orders.isEmpty()) {
                return "未找到订单";
            }

            StringBuilder sb = new StringBuilder("查询到以下订单：\n");
            for (var order : orders) {
                sb.append(String.format("- 订单号：%s，商品：%s，金额：%s，状态：%s\n",
                    order.get("orderId"), order.get("item"),
                    order.get("amount"), order.get("status")));
            }
            return sb.toString();
        }

        @Tool("查询订单详情")
        public String queryOrderDetail(String orderId) {
            tracer.startTrace("session", "tool", "queryOrderDetail");
            var detail = orderService.queryOrderDetail(orderId);
            tracer.endTrace("session", "tool", "queryOrderDetail", true, orderId);
            return detail.toString();
        }

        @Tool("查询订单费用明细，用于核实是否多收费")
        public String queryOrderCharges(String orderId) {
            tracer.startTrace("session", "tool", "queryOrderCharges");
            var charges = orderService.queryOrderCharges(orderId);
            tracer.endTrace("session", "tool", "queryOrderCharges", true, orderId);
            return String.format("费用明细：商品价：%s，运费：%s，优惠：%s，实付：%s，多收：%s",
                charges.get("itemPrice"), charges.get("shippingFee"),
                charges.get("discount"), charges.get("paymentAmount"),
                charges.get("overCharge"));
        }

        @Tool("查询快递信息")
        public String queryDelivery(String trackingNo) {
            tracer.startTrace("session", "tool", "queryDelivery");
            var delivery = deliveryService.queryDelivery(trackingNo);
            tracer.endTrace("session", "tool", "queryDelivery", true, trackingNo);
            return String.format("快递状态：%s，位置：%s",
                delivery.get("status"), delivery.get("location"));
        }

        @Tool("检查快递是否在柜机中")
        public String checkInCabinet(String trackingNo) {
            tracer.startTrace("session", "tool", "checkInCabinet");
            boolean inCabinet = deliveryService.isInCabinet(trackingNo);
            tracer.endTrace("session", "tool", "checkInCabinet", inCabinet, trackingNo);
            return inCabinet ? "快递在柜机中" : "快递不在柜机中";
        }

        @Tool("获取取件码")
        public String getPickupCode(String trackingNo) {
            tracer.startTrace("session", "tool", "getPickupCode");
            String code = deliveryService.getPickupCode(trackingNo);
            tracer.endTrace("session", "tool", "getPickupCode", !code.isEmpty(), code);
            return code.isEmpty() ? "无取件码" : "取件码：" + code;
        }

        @Tool("创建工单，type可选：丢件、多收费、损坏、延迟")
        public String createWorkOrder(String type, String userId, String description,
                                       String trackingNo) {
            tracer.startTrace("session", "tool", "createWorkOrder");
            String handler = workOrderService.getHandlerByType(type);
            var workOrder = workOrderService.createWorkOrder(
                type, userId, description, trackingNo, handler);
            tracer.endTrace("session", "tool", "createWorkOrder", true,
                workOrder.get("workOrderId"));
            return String.format("工单已创建：%s，类型：%s，处理人：%s",
                workOrder.get("workOrderId"), type, handler);
        }
    }

    /**
     * 处理用户消息
     */
    public String chat(String userId, String message) {
        String sessionId = userId + "_" + System.currentTimeMillis();
        log.info(">>> [客服Agent] 会话 {} 开始，用户: {}", sessionId, userId);

        tracer.startTrace(sessionId, "agent", "start");

        // 使用 ReACT 模式处理
        String response = processWithReACT(sessionId, userId, message);

        tracer.endTrace(sessionId, "agent", "start", true, "处理完成");
        tracer.printSummary(sessionId);

        return response;
    }

    /**
     * ReACT 处理流程
     */
    private String processWithReACT(String sessionId, String userId, String message) {
        // 构建系统提示词
        String systemPrompt = """
            你是一个专业的客服助手，可以处理以下业务：
            1. 查询订单 - 需要验证用户信息后查询
            2. 反馈丢件 - 查询快递状态，判断是否在柜机
            3. 投诉多收费 - 查询费用明细，创建投诉工单

            处理流程：
            - 先分析用户意图
            - 根据意图调用相应工具
            - 根据工具结果回复用户
            - 如果需要，创建工单并转交处理人

            请用友好的语气回复用户。
            """;

        // 调用 AI 助手（ReACT 循环由 AiServices 自动处理）
        String fullMessage = systemPrompt + "\n\n用户消息：" + message;
        String response = assistant.chat(sessionId, fullMessage);

        log.info(">>> [客服Agent] 会话 {} 结束，响应长度: {}", sessionId, response.length());
        return response;
    }
}
