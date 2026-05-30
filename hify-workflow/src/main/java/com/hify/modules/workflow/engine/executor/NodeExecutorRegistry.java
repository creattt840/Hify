package com.hify.modules.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 节点执行器注册表 —— Spring 自动注入所有 NodeExecutor 实现，按 nodeType 分发。
 */
@Component
public class NodeExecutorRegistry {

    private final Map<String, NodeExecutor> registry;

    public NodeExecutorRegistry(List<NodeExecutor> executors) {
        this.registry = executors.stream()
                .collect(Collectors.toMap(
                        NodeExecutor::nodeType,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate NodeExecutor for type: " + a.nodeType());
                        }));
    }

    /**
     * 按节点类型获取执行器。
     *
     * @param nodeType 节点类型标识（大小写不敏感）
     * @return 对应的执行器
     * @throws BizException 未知节点类型时抛出
     */
    public NodeExecutor get(String nodeType) {
        NodeExecutor executor = registry.get(nodeType.toUpperCase());
        if (executor == null) {
            throw new BizException(ErrorCode.UNSUPPORTED_NODE_TYPE, "不支持的节点类型: " + nodeType);
        }
        return executor;
    }

    /** 判断是否支持该节点类型 */
    public boolean contains(String nodeType) {
        return registry.containsKey(nodeType.toUpperCase());
    }
}
