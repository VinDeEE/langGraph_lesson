package com.example.assistant;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 追问澄清 Agent
 *
 * 价值体现：智能追问
 * - 不是机械地问固定问题
 * - 根据已知信息推断还缺什么
 * - 用自然的方式追问，像真人助手一样
 */
public class ClarificationAgent implements NodeAction<TaskAssistantState> {

    private static final Logger log = LoggerFactory.getLogger(ClarificationAgent.class);
    private final OpenAiChatModel model;

    public ClarificationAgent(OpenAiChatModel model) {
        this.model = model;
    }

    @Override
    public Map<String, Object> apply(TaskAssistantState state) throws Exception {
        String userInput = state.userInput().orElse("");
        String parsedTask = state.parsedTask().orElse("{}");

        log.info(">>> [追问Agent] 生成追问问题");

        String prompt = """
            你是一个任务助手。用户的信息不完整，需要追问。

            用户输入: %s

            已解析的信息:
            %s

            请生成一个自然的追问问题，要求：
            1. 语气友好、自然
            2. 一次只问一个关键缺失信息
            3. 可以给出选项让用户选择
            4. 如果能推断默认值，可以提出来让用户确认

            直接输出追问问题，不要输出其他内容。
            """.formatted(userInput, parsedTask);

        String question = model.chat(prompt);
        log.info(">>> [追问Agent] 追问: {}", question);

        return Map.of(
            TaskAssistantState.FINAL_RESPONSE, question,
            TaskAssistantState.MESSAGES, "【追问澄清】需要更多信息"
        );
    }
}
