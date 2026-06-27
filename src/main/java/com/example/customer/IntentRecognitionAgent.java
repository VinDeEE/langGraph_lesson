package com.example.customer;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Agent 1: 意图识别 Agent
 *
 * 职责：分析用户的输入，识别用户的真实意图
 * 输出：意图分类（如：咨询、投诉、退换货、其他）
 */
public class IntentRecognitionAgent implements NodeAction<CustomerServiceState> {

    private static final Logger log = LoggerFactory.getLogger(IntentRecognitionAgent.class);
    private final OpenAiChatModel model;

    public IntentRecognitionAgent(OpenAiChatModel model) {
        this.model = model;
    }

    @Override
    public Map<String, Object> apply(CustomerServiceState state) {
        String userMessage = state.userMessage()
            .orElseThrow(() -> new IllegalStateException("用户消息不能为空"));

        log.info(">>> [意图识别Agent] 正在分析用户意图: {}", userMessage);

        String prompt = """
            你是一个意图识别专家。请分析用户的输入，判断其意图类别。

            可选的意图类别：
            - PRODUCT_INQUIRY: 商品咨询（询问商品信息、价格、规格等）
            - ORDER_STATUS: 订单状态（查询物流、发货时间等）
            - COMPLAINT: 投诉（对服务或商品不满）
            - RETURN_EXCHANGE: 退换货（要求退货、换货、退款）
            - OTHER: 其他（无法归类的问题）

            请只返回意图类别，不要返回其他内容。

            用户输入：%s
            """.formatted(userMessage);

        String intent = model.chat(prompt);
        log.info(">>> [意图识别Agent] 识别结果: {}", intent);

        return Map.of(
            CustomerServiceState.INTENT, intent.trim(),
            CustomerServiceState.MESSAGES, "【意图识别】" + intent.trim()
        );
    }
}
