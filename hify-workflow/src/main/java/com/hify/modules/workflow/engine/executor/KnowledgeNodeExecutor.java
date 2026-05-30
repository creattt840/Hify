package com.hify.modules.workflow.engine.executor;

import com.hify.modules.knowledge.api.KnowledgeSearchResult;
import com.hify.modules.knowledge.api.KnowledgeService;
import com.hify.modules.workflow.engine.ExecutionContext;
import com.hify.modules.workflow.engine.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库检索节点执行器 —— 向量相似度搜索，结果拼接为字符串写入上下文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeNodeExecutor implements NodeExecutor {

    private final KnowledgeService knowledgeService;

    @Override
    public String nodeType() {
        return "KNOWLEDGE";
    }

    @Override
    public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
        KnowledgeConfig kbConfig = (KnowledgeConfig) config;

        // 1. 模板变量替换查询文本
        String query = ctx.resolve(kbConfig.query());

        int topK = kbConfig.topK() != null ? kbConfig.topK() : 5;

        // 2. 调用知识库检索
        List<KnowledgeSearchResult> results = knowledgeService.searchChunks(
                kbConfig.knowledgeBaseId(), query, topK);

        // 3. 拼接为字符串
        String formatted = results.stream()
                .map(r -> "[相似度: " + String.format("%.2f", r.getSimilarity()) + "] " + r.getContent())
                .collect(Collectors.joining("\n---\n"));

        // 4. 结果写入上下文
        String varName = kbConfig.outputVariable() != null ? kbConfig.outputVariable() : "results";
        ctx.set(node.getNodeKey(), varName, formatted);
        log.info("知识库检索节点执行完成: nodeKey={}, kbId={}, topK={}, resultCount={}",
                node.getNodeKey(), kbConfig.knowledgeBaseId(), topK, results.size());
    }
}
