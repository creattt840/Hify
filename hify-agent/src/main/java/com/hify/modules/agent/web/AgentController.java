package com.hify.modules.agent.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.agent.api.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping
    public Result<AgentResponse> create(@Valid @RequestBody AgentCreateRequest request) {
        return Result.ok(agentService.create(request));
    }

    @GetMapping
    public PageResult<List<AgentResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return agentService.list(page, pageSize);
    }

    @GetMapping("/{id}")
    public Result<AgentDetailResponse> detail(@PathVariable Long id) {
        return Result.ok(agentService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<AgentResponse> update(@PathVariable Long id,
                                        @Valid @RequestBody AgentUpdateRequest request) {
        return Result.ok(agentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        agentService.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/tools")
    public Result<Void> bindTools(@PathVariable Long id,
                                  @Valid @RequestBody AgentToolBindRequest request) {
        agentService.bindTools(id, request);
        return Result.ok();
    }

    @GetMapping("/{id}/tools")
    public Result<List<Long>> getBoundTools(@PathVariable Long id) {
        return Result.ok(agentService.getBoundToolIds(id));
    }
}
