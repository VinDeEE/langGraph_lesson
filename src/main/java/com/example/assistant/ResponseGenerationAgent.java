package com.example.assistant;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 回复生成 Agent
 *
 * 价值体现：自适应回复
 * - 根据工具执行结果生成总结
 * - 根据用户情绪调整语气
 * - 智能组合信息，而不是简单模板填充
 */
public class ResponseGenerationAgent implements NodeAction<TaskAssistantState> {

    private static final Logger log = LoggerFactory.getLogger(ResponseGenerationAgent.class);
    private final OpenAiChatModel model;

    public ResponseGenerationAgent(OpenAiChatModel model) {
        this.model = model;
    }

    @Override
    public Map<String, Object> apply(TaskAssistantState state) throws Exception {
        String userInput = state.userInput().orElse("");
        String parsedTask = state.parsedTask().orElse("{}");
        String toolResults = String.join("\n", state.toolResults());

        log.info(">>> [回复生成Agent] 生成最终回复");

        String prompt = """
            你是一个专业的任务助手。根据用户输入、任务解析和工具执行结果，生成友好的回复。

            用户原始输入: %s

            任务解析:
            %s

            工具执行结果:
            %s

            要求：
            1. 用自然、友好的语气回复
            2. 总结已完成的操作
            3. 如果有后续步骤，给出清晰指引
            4. 如果用户情绪不好，先表示理解再给解决方案
            5. 回复简洁，不超过200字
            """.formatted(userInput, parsedTask, toolResults);

        String response = model.chat(prompt);
        log.info(">>> [回复生成Agent] 回复生成完成");

        return Map.of(
            TaskAssistantState.FINAL_RESPONSE, response,
            TaskAssistantState.MESSAGES, "【回复生成】已完成"
        );
    }
}
