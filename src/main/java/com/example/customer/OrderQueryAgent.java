package com.example.customer;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 订单查询 Agent
 *
 * 专门处理订单状态查询，模拟查询物流信息
 */
public class OrderQueryAgent implements NodeAction<CustomerServiceState> {

    private static final Logger log = LoggerFactory.getLogger(OrderQueryAgent.class);
    private final OpenAiChatModel model;

    public OrderQueryAgent(OpenAiChatModel model) {
        this.model = model;
    }

    @Override
    public Map<String, Object> apply(CustomerServiceState state) {
        String userMessage = state.userMessage().orElse("");

        log.info(">>> [订单查询Agent] 正在查询订单...");

        String prompt = """
            你是一个订单查询专员。用户想查询订单状态，请按以下方式回复：

            1. 请用户提供订单号（如果没提供）
            2. 如果用户已提供订单号，模拟一个物流状态
            3. 告知预计到达时间

            模拟的物流状态示例：
            - 订单已发货，正在运输中
            - 预计1-3天内送达
            - 当前位置：XX市转运中心

            用户查询: %s

            请用简洁、准确的语气回复，不超过150字。
            """.formatted(userMessage);

        String response = model.chat(prompt);
        log.info(">>> [订单查询Agent] 查询完成");

        return Map.of(
            CustomerServiceState.RESPONSE, response,
            CustomerServiceState.MESSAGES, "【订单查询】已生成查询结果"
        );
    }
}
