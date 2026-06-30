package com.example.customerservice.hook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 执行追踪器 - 记录 Agent 执行过程
 *
 * 用于：
 * 1. 性能监控
 * 2. 调试追踪
 * 3. 审计日志
 */
public class ExecutionTracer {

    private static final Logger log = LoggerFactory.getLogger(ExecutionTracer.class);

    private final List<TraceRecord> records = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Long> startTimes = new ConcurrentHashMap<>();

    /**
     * 追踪记录
     */
    public static class TraceRecord {
        private final String sessionId;
        private final String node;
        private final String action;
        private final long timestamp;
        private final long duration;
        private final boolean success;
        private final String detail;

        public TraceRecord(String sessionId, String node, String action,
                          long timestamp, long duration, boolean success, String detail) {
            this.sessionId = sessionId;
            this.node = node;
            this.action = action;
            this.timestamp = timestamp;
            this.duration = duration;
            this.success = success;
            this.detail = detail;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s.%s %dms %s %s",
                sessionId, node, action, duration,
                success ? "✓" : "✗",
                detail != null ? detail : "");
        }

        // Getters
        public String getSessionId() { return sessionId; }
        public String getNode() { return node; }
        public String getAction() { return action; }
        public long getTimestamp() { return timestamp; }
        public long getDuration() { return duration; }
        public boolean isSuccess() { return success; }
        public String getDetail() { return detail; }
    }

    /**
     * 开始追踪
     */
    public void startTrace(String sessionId, String node, String action) {
        String key = sessionId + "." + node + "." + action;
        startTimes.put(key, System.currentTimeMillis());
        log.debug("[追踪] 开始: {}.{}", node, action);
    }

    /**
     * 结束追踪
     */
    public void endTrace(String sessionId, String node, String action,
                         boolean success, String detail) {
        String key = sessionId + "." + node + "." + action;
        long startTime = startTimes.getOrDefault(key, System.currentTimeMillis());
        long duration = System.currentTimeMillis() - startTime;

        TraceRecord record = new TraceRecord(
            sessionId, node, action, startTime, duration, success, detail
        );
        records.add(record);

        log.info("[追踪] {}", record);
    }

    /**
     * 记录工具调用
     */
    public void traceToolCall(String sessionId, String toolName,
                              String arguments, String result, boolean success) {
        endTrace(sessionId, "tool", toolName, success,
            String.format("args=%s, result=%s", arguments, result));
    }

    /**
     * 记录路由决策
     */
    public void traceRouting(String sessionId, String from, String to, String reason) {
        endTrace(sessionId, "router", from, true,
            String.format("→ %s (%s)", to, reason));
    }

    /**
     * 获取会话追踪记录
     */
    public List<TraceRecord> getSessionRecords(String sessionId) {
        return records.stream()
            .filter(r -> r.getSessionId().equals(sessionId))
            .toList();
    }

    /**
     * 打印追踪摘要
     */
    public void printSummary(String sessionId) {
        List<TraceRecord> sessionRecords = getSessionRecords(sessionId);

        System.out.println("\n" + "=".repeat(50));
        System.out.println("执行追踪摘要 - 会话: " + sessionId);
        System.out.println("=".repeat(50));

        long totalTime = 0;
        int successCount = 0;
        int failCount = 0;

        for (TraceRecord record : sessionRecords) {
            System.out.println("  " + record);
            totalTime += record.getDuration();
            if (record.isSuccess()) successCount++;
            else failCount++;
        }

        System.out.println("-".repeat(50));
        System.out.printf("总耗时: %dms | 成功: %d | 失败: %d%n",
            totalTime, successCount, failCount);
    }
}
