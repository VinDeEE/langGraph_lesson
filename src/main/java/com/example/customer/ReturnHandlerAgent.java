package com.example.customer;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 退换货处理 Agent
 *
 * 专门处理退换货请求，提供退货流程和政策说明
 */
public class ReturnHandlerAgent implements NodeAction<CustomerServiceState> {

    private static final Logger log = LoggerFactory.getLogger(ReturnHandlerAgent.class);
    private final OpenAiChatModel model;

    public ReturnHandlerAgent(OpenAiChatModel model) {
        this.model = model;
    }

    @Override
    public Map<String, Object> apply(CustomerServiceState state) {
        String userMessage = state.userMessage().orElse("");

        log.info(">>> [退换货Agent] 正在处理退换货请求...");

        String prompt = """
            你是一个退换货处理专员。用户想要退换货，请按以下流程回复：

            1. 确认退换货原因
            2. 说明退换货政策（7天无理由、质量问题等）
            3. 提供具体操作步骤
            4. 告知预计处理时间

            退换货政策参考：
            - 7天内无理由退换，运费买家承担
            - 质量问题15天内免费退换
            - 退款3-5个工作日到账

            用户请求: %s

            请用清晰、友好的语气回复，不超过200字。
            """.formatted(userMessage);

        String response = model.chat(prompt);
        log.info(">>> [退换货Agent] 处理完成");

        return Map.of(
            CustomerServiceState.RESPONSE, response,
            CustomerServiceState.MESSAGES, "【退换货处理】已生成退换货指引"
        );
    }
}
