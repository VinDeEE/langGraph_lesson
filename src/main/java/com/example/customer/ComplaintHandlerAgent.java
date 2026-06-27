package com.example.customer;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 投诉处理 Agent
 *
 * 专门处理投诉类问题，使用安抚话术和补偿方案
 */
public class ComplaintHandlerAgent implements NodeAction<CustomerServiceState> {

    private static final Logger log = LoggerFactory.getLogger(ComplaintHandlerAgent.class);
    private final OpenAiChatModel model;

    public ComplaintHandlerAgent(OpenAiChatModel model) {
        this.model = model;
    }

    @Override
    public Map<String, Object> apply(CustomerServiceState state) {
        String userMessage = state.userMessage().orElse("");

        log.info(">>> [投诉处理Agent] 正在处理投诉...");

        String prompt = """
            你是一个专业的投诉处理专员。用户正在投诉，请用以下策略回复：

            1. 首先表达诚挚的歉意
            2. 表示理解用户的不满
            3. 提供具体的解决方案（如补偿、退款、换货等）
            4. 给出后续跟进承诺

            用户投诉内容: %s

            请用温和、专业的语气回复，不超过200字。
            """.formatted(userMessage);

        String response = model.chat(prompt);
        log.info(">>> [投诉处理Agent] 处理完成");

        return Map.of(
            CustomerServiceState.RESPONSE, response,
            CustomerServiceState.MESSAGES, "【投诉处理】已生成安抚回复"
        );
    }
}
