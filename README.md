# LangGraph4j 智能客服 Demo

## 项目简介

这是一个基于 LangGraph4j 的多 Agent 协作示例，模拟智能客服场景。

## 架构图

```
用户输入
   │
   ▼
┌─────────────────┐
│  意图识别 Agent   │  ← 分析用户意图（咨询/投诉/退换货等）
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  知识库查询 Agent │  ← 根据意图检索相关知识
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  回复生成 Agent   │  ← 综合信息生成最终回复
└────────┬────────┘
         │
         ▼
      输出回复
```

## 核心概念

| 概念 | 说明 |
|------|------|
| StateGraph | 状态图，定义节点和边的关系 |
| State | 共享状态，在节点间传递数据 |
| Node | 节点，执行具体任务的 Agent |
| Edge | 边，定义节点间的执行顺序 |

## 快速开始

### 1. 配置 API Key

编辑 `src/main/resources/application.properties`：

```properties
openai.api.key=sk-你的API密钥
openai.api.base-url=https://api.openai.com
openai.model.name=gpt-4o-mini
```

**支持的 API 服务：**
- OpenAI 官方：`https://api.openai.com`
- 兼容 OpenAI 的第三方服务（如 DeepSeek、Moonshot 等）

### 2. 运行项目

```bash
mvn compile exec:java -Dexec.mainClass="com.example.customer.CustomerServiceDemo"
```

或者在 IDE 中直接运行 `CustomerServiceDemo.main()`

### 3. 查看输出

程序会依次处理 3 个测试用例，展示每个 Agent 的处理过程。

## 项目结构

```
src/main/java/com/example/customer/
├── CustomerServiceState.java      # 状态定义
├── IntentRecognitionAgent.java    # Agent 1: 意图识别
├── KnowledgeBaseAgent.java        # Agent 2: 知识库查询
├── ResponseGeneratorAgent.java    # Agent 3: 回复生成
└── CustomerServiceDemo.java       # 主程序入口
```

## 学习要点

1. **状态管理**：通过 `State` 在节点间共享数据
2. **节点定义**：实现 `NodeAction` 接口定义每个 Agent 的逻辑
3. **图编排**：使用 `StateGraph` 定义执行流程
4. **流式执行**：通过 `stream()` 观察每一步的状态变化

## 扩展方向

- 添加条件边：根据意图类型走不同的处理分支
- 接入真实知识库：用 RAG 替代模拟的知识库
- 添加人工审核节点：对敏感回复进行人工确认
- 持久化状态：使用 Checkpoint 保存对话历史
