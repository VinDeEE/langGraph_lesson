# LangGraph4j 学习计划

## 学习路线图

```
基础篇 → 进阶篇 → 高级篇 → 实战篇
  │         │         │         │
  ▼         ▼         ▼         ▼
状态/节点  条件路由  检查点/持久化  Agent执行器
边/编译    子图     人工介入      工具调用
执行方式   并行执行  流式输出      框架集成
```

---

## 基础篇（已完成 ✅）

### 1. 状态管理（State）
- **核心类：** `AgentState`、`Channel`、`Channels`
- **学习内容：**
  - 状态 Schema 定义
  - `appender` vs `base` 两种 Channel 类型
  - 状态的读取和更新机制
- **Demo：** `CustomerServiceState.java` ✅

### 2. 节点（Nodes）
- **核心类：** `NodeAction<S>`、`AsyncNodeAction<S>`
- **学习内容：**
  - 同步节点 vs 异步节点
  - 节点如何读取状态、返回更新
  - `node_async()` 包装器
- **Demo：** `IntentRecognitionAgent.java` ✅

### 3. 边（Edges）
- **核心方法：** `addEdge()`、`addConditionalEdges()`
- **学习内容：**
  - 普通边（固定流程）
  - 条件边（动态路由）
  - START / END 特殊节点
- **Demo：** `CustomerServiceDemo.java`（固定流程）✅
- **Demo：** `DynamicRoutingDemo.java`（动态路由）✅

### 4. 图编译与执行
- **核心方法：** `compile()`、`stream()`、`invoke()`
- **学习内容：**
  - StateGraph → CompiledGraph 编译过程
  - `stream()` 流式执行，观察每一步
  - `invoke()` 一次性执行，获取最终结果
- **Demo：** 已在上述 Demo 中体现 ✅

---

## 进阶篇

### 5. 条件入口（Conditional Entry Point）✅
- **核心方法：** `addConditionalEntryPoint()`（当前版本用路由节点实现）
- **学习内容：**
  - 根据初始输入动态选择起始节点
  - 与条件边的区别
- **Demo：** `ConditionalEntryDemo.java` ✅
- **场景：** 不同类型的用户请求直接进入不同处理流程

```java
// 实现方式：路由节点 + 条件边
.addNode("router", node_async(state -> {
    String input = state.userInput().orElse("");
    String type = "general";
    if (input.contains("投诉")) type = "complaint";
    else if (input.contains("退货")) type = "return";
    return Map.of("requestType", type);
}))
.addConditionalEdges("router",
    edge_async(state -> state.requestType().orElse("general")),
    Map.of(
        "complaint", "complaint_handler",
        "return", "return_handler",
        "general", "general_handler"
    )
)
```

### 6. 子图（Subgraphs）✅
- **核心方法：** `addNode(id, compiledGraph)`
- **学习内容：**
  - 将复杂逻辑封装为独立子图
  - 子图与父图共享状态
  - 模块化设计
- **Demo：** `SubgraphDemo.java` ✅
- **场景：** 订单处理包含多个子流程（验证、支付、发货）

```java
// 示例代码
StateGraph<SubState> subGraph = new StateGraph<>(...);
subGraph.addNode("step1", ...).addNode("step2", ...);
CompiledGraph<SubState> compiledSub = subGraph.compile();

parentGraph.addNode("order_processing", compiledSub);
```

### 7. 并行执行（Parallel Execution）✅
- **学习内容：**
  - 多个节点同时执行
  - 等待所有并行节点完成
  - 并行结果合并
- **Demo：** `ParallelExecutionDemo.java` ✅
- **场景：** 同时查询多个知识库，合并结果

```java
// 示例代码 - 并行查询多个数据源
graph.addNode("query_db1", ...)
     .addNode("query_db2", ...)
     .addNode("query_db3", ...)
     .addNode("merge_results", ...);

// 并行执行后合并
graph.addEdge(START, "query_db1")
     .addEdge(START, "query_db2")
     .addEdge(START, "query_db3")
     .addEdge("query_db1", "merge_results")
     .addEdge("query_db2", "merge_results")
     .addEdge("query_db3", "merge_results");
```

### 8. 流式输出（Streaming）✅
- **核心方法：** `stream()` 返回 `AsyncGenerator`
- **学习内容：**
  - 逐步观察状态变化
  - LLM 响应的实时输出
  - 流式处理与回调
- **Demo：** `StreamingDemo.java` ✅
- **场景：** 实时显示 AI 回复过程

```java
// 示例代码
for (var output : compiledGraph.stream(inputs)) {
    System.out.println("节点: " + output.node());
    System.out.println("状态: " + output.state());
    // 实时更新 UI
}
```

---

## 高级篇

### 9. 检查点与持久化（Checkpoints）✅
- **核心类：** `CheckpointSaver`、`MemorySaver`
- **学习内容：**
  - 状态快照保存
  - 从检查点恢复执行
  - 时间旅行调试
- **依赖：**
  ```xml
  <artifactId>langgraph4j-core</artifactId>  <!-- MemorySaver 内置 -->
  ```
- **Demo：** `CheckpointDemo.java` ✅
- **场景：** 长对话中断后恢复

```java
// 示例代码
var saver = new MemorySaver();
var config = CompileConfig.builder()
    .checkpointSaver(saver)
    .build();

var graph = stateGraph.compile(config);

// 保存检查点
var checkpointId = graph.saveCheckpoint(state);

// 从检查点恢复
graph.invoke(checkpointId, newInputs);
```

### 10. 数据库持久化
- **可用模块：**
  | 模块 | 存储 |
  |------|------|
  | `langgraph4j-mysql-saver` | MySQL |
  | `langgraph4j-postgres-saver` | PostgreSQL |
  | `langgraph4j-redis-saver` | Redis |
- **学习内容：**
  - 配置数据库连接
  - 生产环境的状态持久化
- **Demo：** `DatabasePersistenceDemo.java`

### 11. 人工介入（Human-in-the-Loop）✅
- **学习内容：**
  - 暂停执行等待人工审批
  - 人工输入后继续执行
  - 审批节点设计
- **Demo：** `HumanApprovalDemo.java` ✅
- **场景：** 敏感操作需要人工确认

```java
// 示例代码
graph.addNode("auto_process", ...)
     .addNode("human_approval", ...)
     .addNode("execute_action", ...);

// 流程：自动处理 → 人工审批 → 执行
graph.addEdge("auto_process", "human_approval")
     .addConditionalEdges("human_approval",
         edge_async(state -> {
             boolean approved = state.value("approved").orElse(false);
             return approved ? "approved" : "rejected";
         }),
         Map.of("approved", "execute_action", "rejected", END)
     );
```

### 12. 多线程/会话管理（Threads）✅
- **学习内容：**
  - 独立的执行线程
  - 每个线程独立的检查点历史
  - 多用户并发处理
- **Demo：** `MultiThreadDemo.java` ✅
- **场景：** 多用户同时使用客服系统

---

## 实战篇

### 13. Agent 执行器（ReACT Agent）✅
- **核心类：** `AgentExecutor`
- **学习内容：**
  - ReACT（Reasoning + Acting）模式
  - 自动工具调用
  - 循环推理直到完成
- **依赖：**
  ```xml
  <artifactId>langgraph4j-langchain4j</artifactId>
  ```
- **Demo：** `ReActAgentDemo.java` ✅
- **场景：** 自动查询天气、搜索信息、执行计算

```java
// 示例代码
@Tool("获取天气信息")
public String getWeather(String city) {
    return "北京今天晴，25度";
}

var agent = AgentExecutor.builder()
    .chatModel(model)
    .toolsFromObject(this)
    .build();

var result = agent.invoke("今天北京天气怎么样？");
```

### 14. 工具调用（Tool Calling）✅
- **学习内容：**
  - `@Tool` 注解定义工具
  - LLM 自动决定调用哪个工具
  - 工具结果返回给 LLM 继续推理
- **Demo：** `ToolCallingDemo.java` ✅

### 15. LangChain4j 集成 ✅
- **学习内容：**
  - 与 LangChain4j ChatModel 集成
  - 使用 LangChain4j 的工具生态
  - 消息历史管理
- **Demo：** `LangChain4jIntegrationDemo.java` ✅

#### AiServices 接口方法定义规则

**方法签名要求：**
```java
interface Assistant {
    // 1. 最简单：只有一个参数（用户消息）
    String chat(String userMessage);

    // 2. 多参数：需要使用注解标记
    String chat(@MemoryId String userId, @UserMessage String message);

    // 3. 带模板变量：使用 @V 注解
    @UserMessage("你好，我叫{{name}}，今年{{age}}岁")
    String chat(@V("name") String name, @V("age") int age);
}
```

**参数个数要求：**
- 无限制，可以有任意多个参数
- 但每个参数必须有明确的用途（通过注解指定）

**可用注解详解：**

| 注解 | 作用位置 | 说明 | 示例 |
|------|---------|------|------|
| `@UserMessage` | 方法/参数 | 定义用户消息模板或标记用户消息参数 | `@UserMessage("你好{{name}}")` |
| `@SystemMessage` | 方法/类 | 定义系统提示词 | `@SystemMessage("你是助手")` |
| `@MemoryId` | 参数 | 标记会话ID，用于多用户隔离 | `@MemoryId String userId` |
| `@V` | 参数 | 定义模板变量名 | `@V("name") String name` |
| `@Moderate` | 方法 | 启用内容审核 | `@Moderate` |
| `@Tool` | 方法 | 定义工具（在工具类中） | `@Tool("获取天气")` |

**@UserMessage 详解：**
```java
// 用法1：标记参数为用户消息
String chat(@UserMessage String message);

// 用法2：定义消息模板（方法级别）
@UserMessage("帮我查{{city}}的天气")
String chat(@V("city") String city);

// 用法3：从资源文件读取模板
@UserMessage(fromResource = "prompt-template.txt")
String chat(@V("name") String name);
```

**@SystemMessage 详解：**
```java
// 用法1：定义系统提示词
@SystemMessage("你是一个友好的助手")
String chat(String message);

// 用法2：带模板变量
@SystemMessage("你是一个{{role}}助手")
String chat(@UserMessage String message, @V("role") String role);

// 用法3：多行提示词
@SystemMessage({
    "你是一个专业的客服。",
    "请用友好的语气回答问题。",
    "如果不确定，请说'我不知道'。"
})
String chat(String message);
```

**@MemoryId 详解：**
```java
// 用法：标记参数为会话标识
String chat(@MemoryId String userId, @UserMessage String message);

// 注意：
// 1. 使用 @MemoryId 时，必须配置 chatMemoryProvider
// 2. 参数类型可以是任意类型（需实现 equals/hashCode）
// 3. 不同 memoryId 的会话完全隔离
```

**@V 详解：**
```java
// 用法：定义模板变量
@UserMessage("你好，我叫{{name}}")
String chat(@V("name") String name);

// 注意：
// 1. {{name}} 是模板占位符
// 2. @V("name") 指定参数对应的变量名
// 3. 如果编译时加了 -parameters 选项，可以省略 @V 的 value
```

**返回值类型：**
| 类型 | 说明 |
|------|------|
| `String` | 返回文本响应 |
| `AiMessage` | 返回完整的消息对象 |
| `TokenStream` | 流式输出 |
| `Future<String>` | 异步调用 |

### 16. Spring AI 集成 ✅
- **学习内容：**
  - Spring Boot 应用集成
  - Spring AI ChatModel 使用
  - 依赖注入方式
- **Demo：** `SpringAiDemo.java` ✅

### 17. 图可视化
- **学习内容：**
  - 生成 PlantUML 图表
  - 生成 Mermaid 图表
  - 调试和文档
- **Demo：** `VisualizationDemo.java`

```java
// 示例代码
String plantUml = graph.toPlantUml();
String mermaid = graph.toMermaid();
System.out.println(mermaid);
```

### 18. OpenTelemetry 可观测性 ✅
- **依赖：**
  ```xml
  <artifactId>langgraph4j-opentelemetry</artifactId>
  ```
- **学习内容：**
  - 追踪节点执行
  - 性能监控
  - 错误追踪
- **Demo：** `ObservabilityDemo.java` ✅

#### Hook 详解（可观测性核心）

**Hook 总览：**

| 方法 | 作用位置 | 触发时机 | 用途 |
|------|---------|---------|------|
| `addBeforeCallNodeHook` | 节点 | 节点执行前 | 日志、计时、权限检查 |
| `addAfterCallNodeHook` | 节点 | 节点执行后 | 日志、计时、结果处理 |
| `addWrapCallNodeHook` | 节点 | 包装整个节点执行 | 事务、重试、熔断 |
| `addBeforeCallEdgeHook` | 边 | 边执行前 | 路由日志 |
| `addAfterCallEdgeHook` | 边 | 边执行后 | 路由结果处理 |
| `addWrapCallEdgeHook` | 边 | 包装整个边执行 | 路由拦截 |

**1. NodeHook.BeforeCall（节点前置钩子）：**
```java
// 接口定义
interface BeforeCall<State> {
    CompletableFuture<Map<String, Object>> applyBefore(
        String nodeId,      // 节点名称
        State state,        // 当前状态
        RunnableConfig config  // 运行配置
    );
}

// 用法：全局钩子（所有节点）
graph.addBeforeCallNodeHook((nodeId, state, config) -> {
    log.info("节点 {} 即将执行", nodeId);
    return CompletableFuture.completedFuture(Map.of());  // 返回空Map不修改状态
});

// 用法：指定节点钩子
graph.addBeforeCallNodeHook("step1", (nodeId, state, config) -> {
    log.info("step1 即将执行");
    return CompletableFuture.completedFuture(Map.of());
});
```

**2. NodeHook.AfterCall（节点后置钩子）：**
```java
// 接口定义
interface AfterCall<State> {
    CompletableFuture<Map<String, Object>> applyAfter(
        String nodeId,           // 节点名称
        State state,             // 当前状态
        RunnableConfig config,   // 运行配置
        Map<String, Object> lastResult  // 节点执行结果
    );
}

// 用法
graph.addAfterCallNodeHook((nodeId, state, config, result) -> {
    log.info("节点 {} 执行完成，结果: {}", nodeId, result);
    return CompletableFuture.completedFuture(Map.of());
});
```

**3. NodeHook.WrapCall（节点包装钩子）：**
```java
// 接口定义
interface WrapCall<State> {
    CompletableFuture<Map<String, Object>> applyWrap(
        String nodeId,                          // 节点名称
        State state,                            // 当前状态
        RunnableConfig config,                  // 运行配置
        AsyncNodeActionWithConfig<State> action // 原始节点动作
    );
}

// 用法：实现重试逻辑
graph.addWrapCallNodeHook((nodeId, state, config, action) -> {
    // 可以在执行前做处理
    log.info("包装节点 {}", nodeId);

    // 调用原始动作
    return action.apply(state, config).thenApply(result -> {
        // 可以在执行后做处理
        log.info("节点 {} 完成", nodeId);
        return result;
    });
});

// 用法：实现事务
graph.addWrapCallNodeHook((nodeId, state, config, action) -> {
   beginTransaction();
    return action.apply(state, config).thenApply(result -> {
        commitTransaction();
        return result;
    }).exceptionally(ex -> {
        rollbackTransaction();
        throw new RuntimeException(ex);
    });
});
```

**4. EdgeHook.BeforeCall（边前置钩子）：**
```java
// 接口定义
interface BeforeCall<State> {
    CompletableFuture<Command> applyBefore(
        String sourceId,       // 源节点名称
        State state,           // 当前状态
        RunnableConfig config  // 运行配置
    );
}

// 用法
graph.addBeforeCallEdgeHook((sourceId, state, config) -> {
    log.info("从 {} 出发的边即将执行", sourceId);
    return CompletableFuture.completedFuture(null);  // 返回null不修改路由
});
```

**5. EdgeHook.AfterCall（边后置钩子）：**
```java
// 接口定义
interface AfterCall<State> {
    CompletableFuture<Command> applyAfter(
        String sourceId,       // 源节点名称
        State state,           // 当前状态
        RunnableConfig config, // 运行配置
        Command lastResult     // 边执行结果（路由目标）
    );
}

// 用法
graph.addAfterCallEdgeHook((sourceId, state, config, result) -> {
    log.info("从 {} 路由到 {}", sourceId, result.gotoNode());
    return CompletableFuture.completedFuture(null);
});
```

**6. EdgeHook.WrapCall（边包装钩子）：**
```java
// 接口定义
interface WrapCall<State> {
    CompletableFuture<Command> applyWrap(
        String sourceId,                       // 源节点名称
        State state,                           // 当前状态
        RunnableConfig config,                 // 运行配置
        AsyncCommandAction<State> action       // 原始边动作
    );
}

// 用法：实现路由拦截
graph.addWrapCallEdgeHook((sourceId, state, config, action) -> {
    log.info("拦截从 {} 出发的路由", sourceId);

    // 可以修改路由逻辑
    return action.apply(state, config).thenApply(command -> {
        // 可以修改路由目标
        log.info("最终路由到: {}", command.gotoNode());
        return command;
    });
});
```

**实际应用示例：**
```java
// 完整的可观测性实现
graph.addBeforeCallNodeHook((nodeId, state, config) -> {
    MDC.put("nodeId", nodeId);  // 日志上下文
    tracer.startNode(nodeId);    // 开始计时
    return CompletableFuture.completedFuture(Map.of());
});

graph.addAfterCallNodeHook((nodeId, state, config, result) -> {
    tracer.endNode(nodeId, true, null);  // 结束计时
    metrics.recordNodeExecution(nodeId); // 记录指标
    MDC.remove("nodeId");                // 清理上下文
    return CompletableFuture.completedFuture(Map.of());
});

graph.addWrapCallNodeHook((nodeId, state, config, action) -> {
    try {
        return action.apply(state, config);
    } catch (Exception e) {
        tracer.endNode(nodeId, false, e.getMessage());  // 记录错误
        throw e;
    }
});
```

---

## 完整 Demo 清单

| 序号 | Demo 名称 | 对应知识点 | 完成状态 | 优先级 |
|------|-----------|-----------|----------|--------|
| 1 | `CustomerServiceState.java` | 状态管理 | ✅ 已完成 | ⭐⭐⭐⭐⭐ |
| 2 | `IntentRecognitionAgent.java` | 节点定义 | ✅ 已完成 | ⭐⭐⭐⭐⭐ |
| 3 | `CustomerServiceDemo.java` | 固定流程图 | ✅ 已完成 | ⭐⭐⭐⭐⭐ |
| 4 | `DynamicRoutingDemo.java` | 条件路由 | ✅ 已完成 | ⭐⭐⭐⭐⭐ |
| 5 | `SubgraphDemo.java` | 子图 | ✅ 已完成 | ⭐⭐⭐⭐ |
| 6 | `ParallelExecutionDemo.java` | 并行执行 | ✅ 已完成 | ⭐⭐⭐⭐ |
| 7 | `StreamingDemo.java` | 流式输出 | ✅ 已完成 | ⭐⭐⭐ |
| 8 | `LLMStreamingDemo.java` | LLM 流式输出 | ✅ 已完成 | ⭐⭐⭐⭐ |
| 9 | `WebStreamingDemo.java` | Web 流式输出 | ✅ 已完成 | ⭐⭐⭐ |
| 10 | `CheckpointDemo.java` | 检查点 | ✅ 已完成 | ⭐⭐⭐⭐⭐ |
| 11 | `DurableExecutionDemo.java` | 持久化执行 | ✅ 已完成 | ⭐⭐⭐⭐⭐ |
| 12 | `HumanApprovalDemo.java` | 人工介入 | ✅ 已完成 | ⭐⭐⭐⭐ |
| 13 | `MultiThreadDemo.java` | 多线程管理 | ✅ 已完成 | ⭐⭐⭐⭐ |
| 14 | `GraphVisualizationDemo.java` | 图可视化 | ✅ 已完成 | ⭐⭐ |
| 15 | `ReActAgentDemo.java` | Agent 执行器 | ✅ 已完成 | ⭐⭐⭐⭐⭐ |
| 16 | `ProductionAgentDemo.java` | 生产级 Agent | ✅ 已完成 | ⭐⭐⭐⭐⭐ |
| 17 | `ConditionalEntryDemo.java` | 条件入口 | ✅ 已完成 | ⭐⭐⭐ |
| 18 | `ToolCallingDemo.java` | 工具调用 | ✅ 已完成 | ⭐⭐⭐⭐ |
| 19 | `LangChain4jIntegrationDemo.java` | LangChain4j 集成 | ✅ 已完成 | ⭐⭐⭐ |
| 20 | `SpringAiDemo.java` | Spring AI 集成 | ✅ 已完成 | ⭐⭐ |
| 21 | `ObservabilityDemo.java` | 可观测性 | ✅ 已完成 | ⭐ |

---

## 学习建议

### 第一阶段：巩固基础（1-2天）
- 复习已完成的 4 个 Demo
- 理解状态流转机制
- 手动画出每个 Demo 的执行流程图

### 第二阶段：进阶特性（3-5天）
- 完成条件入口、子图、并行执行 Demo
- 理解模块化设计思想
- 尝试组合多个特性

### 第三阶段：高级功能（3-5天）
- 完成检查点、人工介入 Demo
- 理解状态持久化
- 实现中断恢复逻辑

### 第四阶段：实战应用（5-7天）
- 完成 Agent 执行器、工具调用 Demo
- 构建完整的 ReACT Agent
- 集成真实 LLM 和工具

### 第五阶段：生产实践（持续）
- 数据库持久化
- 可观测性
- 性能优化

---

## 参考资源

- [LangGraph4j GitHub](https://github.com/bsorrentino/langgraph4j)
- [LangGraph4j Documentation](https://bsorrentino.github.io/langgraph4j/)
- [LangChain4j](https://github.com/langchain4j/langchain4j)
- [Spring AI](https://spring.io/projects/spring-ai)
