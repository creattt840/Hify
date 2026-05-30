package com.hify.modules.mcp.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.mcp.api.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mcp-servers")
public class McpController {

    private final McpServerService mcpServerService;
    private final McpClientService mcpClientService;

    public McpController(McpServerService mcpServerService,
                         McpClientService mcpClientService) {
        this.mcpServerService = mcpServerService;
        this.mcpClientService = mcpClientService;
    }

    @PostMapping
    public Result<McpServerResponse> create(@Valid @RequestBody McpServerCreateRequest request) {
        return Result.ok(mcpServerService.create(request));
    }

    @GetMapping("/available-tools")
    public Result<List<McpToolOptionResponse>> listAvailableTools() {
        return Result.ok(mcpServerService.listAvailableTools());
    }

    @GetMapping
    public PageResult<List<McpServerListItemResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return mcpServerService.list(page, pageSize);
    }

    @GetMapping("/{id}")
    public Result<McpServerResponse> getById(@PathVariable Long id) {
        return Result.ok(mcpServerService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<McpServerResponse> update(@PathVariable Long id,
                                            @Valid @RequestBody McpServerUpdateRequest request) {
        return Result.ok(mcpServerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mcpServerService.delete(id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/test")
    public Result<ConnectionTestResult> testConnection(@PathVariable Long id) {
        return Result.ok(mcpServerService.testConnection(id));
    }

    @PostMapping("/{id}/debug")
    public Result<McpDebugResponse> debug(@PathVariable Long id,
                                           @Valid @RequestBody McpDebugRequest request) {
        long start = System.currentTimeMillis();
        Map<String, Object> args = request.getArguments() != null
                ? request.getArguments() : Map.of();
        String result = mcpClientService.callTool(id, request.getToolName(), args);
        int elapsedMs = (int) (System.currentTimeMillis() - start);
        return Result.ok(new McpDebugResponse(result, elapsedMs));
    }
}
