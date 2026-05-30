package com.hify.modules.workflow.engine.executor;

import com.hify.modules.workflow.engine.ExecutionContext;
import com.hify.modules.workflow.engine.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 条件节点执行器 —— 解析并求值表达式，结果（true/false）写入上下文。
 * <p>
 * 支持的表达式格式：
 * <ul>
 *   <li>{@code {{var}} == someValue} —— 等值比较</li>
 *   <li>{@code {{var}} != someValue} —— 不等比较</li>
 *   <li>{@code true} / {@code false} —— 字面量布尔值</li>
 *   <li>未匹配任何运算符时，非空字符串视为 true</li>
 * </ul>
 */
@Slf4j
@Component
public class ConditionNodeExecutor implements NodeExecutor {

    @Override
    public String nodeType() {
        return "CONDITION";
    }

    @Override
    public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
        ConditionConfig condConfig = (ConditionConfig) config;

        // 1. 模板变量替换
        String expression = ctx.resolve(condConfig.expression());

        // 2. 求值
        boolean result = evaluate(expression);

        // 3. 结果写入上下文
        String varName = condConfig.outputVariable() != null ? condConfig.outputVariable() : "output";
        ctx.set(node.getNodeKey(), varName, result);
        log.info("条件节点执行完成: nodeKey={}, expression={}, result={}",
                node.getNodeKey(), expression, result);
    }

    /**
     * 表达式求值：
     * <ol>
     *   <li>== / != 运算符 → 字符串比较</li>
     *   <li>"true" / "false" 字面量</li>
     *   <li>非空字符串 → true</li>
     * </ol>
     */
    private boolean evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        String expr = expression.trim();

        // 字面量布尔值
        if ("true".equalsIgnoreCase(expr)) {
            return true;
        }
        if ("false".equalsIgnoreCase(expr)) {
            return false;
        }

        // != 运算符
        if (expr.contains("!=")) {
            String[] parts = expr.split("!=", 2);
            return !equalsAfterTrim(parts[0], parts[1]);
        }

        // == 运算符
        if (expr.contains("==")) {
            String[] parts = expr.split("==", 2);
            return equalsAfterTrim(parts[0], parts[1]);
        }

        // 未匹配任何运算符：非空即为 true
        return true;
    }

    private boolean equalsAfterTrim(String left, String right) {
        return left.trim().equals(right.trim());
    }
}
