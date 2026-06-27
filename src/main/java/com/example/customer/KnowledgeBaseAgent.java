package com.example.customer;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Agent 2: 知识库查询 Agent
 *
 * 职责：根据意图从知识库中检索相关信息
 * 在实际项目中，这里会接入向量数据库或搜索引擎
 * 这里用 LLM 模拟知识库检索
 */
public class KnowledgeBaseAgent implements NodeAction<CustomerServiceState> {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseAgent.class);
    private final OpenAiChatModel model;

    public KnowledgeBaseAgent(OpenAiChatModel model) {
        this.model = model;
    }

    @Override
    public Map<String, Object> apply(CustomerServiceState state) {
        String userMessage = state.userMessage().orElse("");
        String intent = state.intent().orElse("OTHER");

        log.info(">>> [知识库Agent] 正在检索知识库，意图: {}", intent);

        String prompt = """
            你是一个知识库查询专家。根据用户的问题和识别出的意图，提供相关的知识库信息。

            意图分类: %s

            请根据意图提供相应的标准回复信息：
            - PRODUCT_INQUIRY: 提供商品咨询的标准话术和常见信息
            - ORDER_STATUS: 提供订单查询的标准流程
            - COMPLAINT: 提供投诉处理的标准流程和安抚话术
            - RETURN_EXCHANGE: 提供退换货政策和流程
            - OTHER: 提供通用客服话术

            请用简洁的要点形式输出，便于后续生成回复。

            用户问题: %s
            """.formatted(intent, userMessage);

        log.info(">>> [知识库Agent] prompt：{}", prompt);
        String knowledge = model.chat(prompt);
        log.info(">>> [知识库Agent] 检索完成，结果:{}", knowledge);

        return Map.of(
            CustomerServiceState.KNOWLEDGE_RESULT, knowledge,
            CustomerServiceState.MESSAGES, "【知识库检索】已完成"
        );
    }
}
