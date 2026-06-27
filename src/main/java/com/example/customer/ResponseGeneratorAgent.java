package com.example.customer;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Agent 3: 回复生成 Agent
 *
 * 职责：综合用户问题、意图、知识库信息，生成最终的客服回复
 * 这是整个流程的最后一步，输出给用户的最终答案
 */
public class ResponseGeneratorAgent implements NodeAction<CustomerServiceState> {

    private static final Logger log = LoggerFactory.getLogger(ResponseGeneratorAgent.class);
    private final OpenAiChatModel model;

    public ResponseGeneratorAgent(OpenAiChatModel model) {
        this.model = model;
    }

    @Override
    public Map<String, Object> apply(CustomerServiceState state) {
        String userMessage = state.userMessage().orElse("");
        String intent = state.intent().orElse("OTHER");
        String knowledge = state.knowledgeResult().orElse("");

        log.info(">>> [回复生成Agent] 正在生成最终回复...");

        String prompt = """
            你是一个专业的客服代表。请根据以下信息生成一段专业、友好的回复。

            用户问题: %s
            用户意图: %s
            知识库参考信息:
            %s

            要求：
            1. 语气亲切友好，体现专业性
            2. 直接回答用户问题，不要说"根据知识库"这类元信息
            3. 回复简洁明了，不超过150字
            4. 如果需要进一步操作，给出清晰的指引
            """.formatted(userMessage, intent, knowledge);

        log.info(">>> [回复生成Agent] prompt：{}", prompt);
        String response = model.chat(prompt);
        log.info(">>> [回复生成Agent] 回复生成完成,结果：{}", response);

        return Map.of(
            CustomerServiceState.RESPONSE, response,
            CustomerServiceState.MESSAGES, "【最终回复】已生成"
        );
    }
}
