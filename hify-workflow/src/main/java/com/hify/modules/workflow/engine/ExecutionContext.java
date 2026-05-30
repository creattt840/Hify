package com.hify.modules.workflow.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作流执行上下文，存储节点间传递的变量。
 * <p>
 * 变量 key 格式: {@code nodeKey.varName}，例如:
 * <ul>
 *   <li>{@code start.userMessage} —— 用户输入，构造时自动写入</li>
 *   <li>{@code llm.output} —— LLM 节点的输出</li>
 *   <li>{@code classify.category} —— 分类节点的结果</li>
 * </ul>
 * <p>
 * 内部用 {@link LinkedHashMap} 保持变量写入顺序，{@link #resolve(String)}
 * 遍历所有变量替换模板占位符 {@code {{nodeKey.varName}}}。
 *
 * @author hify
 */
public class ExecutionContext {

    /** 工作流运行 ID，用于执行记录追踪 */
    private final String workflowRunId;

    /** 变量存储，保持写入顺序 */
    private final LinkedHashMap<String, Object> variables = new LinkedHashMap<>();

    /**
     * @param workflowRunId 工作流运行 ID
     * @param userMessage   用户输入，自动写入 {@code start.userMessage}
     */
    public ExecutionContext(String workflowRunId, String userMessage) {
        this.workflowRunId = workflowRunId;
        this.variables.put("start.userMessage", userMessage);
    }

    public String getWorkflowRunId() {
        return workflowRunId;
    }

    /**
     * 写入变量，key = {@code nodeKey + "." + varName}。
     *
     * @param nodeKey 节点标识
     * @param varName 变量名
     * @param value   变量值
     */
    public void set(String nodeKey, String varName, Object value) {
        this.variables.put(nodeKey + "." + varName, value);
    }

    /**
     * 读取变量。
     *
     * @param nodeKey 节点标识
     * @param varName 变量名
     * @return 变量值，不存在返回 {@code null}
     */
    public Object get(String nodeKey, String varName) {
        return this.variables.get(nodeKey + "." + varName);
    }

    /**
     * 模板渲染：遍历所有变量，将 {@code {{nodeKey.varName}}} 替换为对应值。
     * 变量不存在时保留原始占位符，不报错。
     *
     * @param template 模板字符串，可为 {@code null}
     * @return 渲染后的字符串，template 为 {@code null} 时返回 {@code null}
     */
    public String resolve(String template) {
        if (template == null || !template.contains("{{")) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    /**
     * 返回所有变量的只读视图，用于执行记录落库。
     *
     * @return 不可修改的变量快照
     */
    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }

    @Override
    public String toString() {
        return "ExecutionContext{workflowRunId='" + workflowRunId + "', variables=" + variables + '}';
    }
}
