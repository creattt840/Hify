package com.hify.modules.knowledge.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.knowledge.api.KnowledgeBaseCreateRequest;
import com.hify.modules.knowledge.api.KnowledgeBaseResponse;
import com.hify.modules.knowledge.api.KnowledgeBaseUpdateRequest;
import com.hify.modules.knowledge.api.KnowledgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @PostMapping
    public Result<KnowledgeBaseResponse> create(@Valid @RequestBody KnowledgeBaseCreateRequest request) {
        return Result.ok(knowledgeService.create(request));
    }

    @GetMapping
    public PageResult<List<KnowledgeBaseResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name) {
        return knowledgeService.list(page, size, name);
    }

    @GetMapping("/{id}")
    public Result<KnowledgeBaseResponse> detail(@PathVariable Long id) {
        return Result.ok(knowledgeService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<KnowledgeBaseResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody KnowledgeBaseUpdateRequest request) {
        return Result.ok(knowledgeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return Result.ok();
    }
}
