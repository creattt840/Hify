package com.hify.modules.provider.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.provider.api.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ModelService modelService;

    @PostMapping
    public Result<ProviderResponse> create(@Valid @RequestBody ProviderCreateRequest request) {
        return Result.ok(modelService.create(request));
    }

    @GetMapping
    public PageResult<List<ProviderResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return modelService.list(page, pageSize);
    }

    @GetMapping("/{id}")
    public Result<ProviderDetailResponse> detail(@PathVariable Long id) {
        return Result.ok(modelService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<ProviderResponse> update(@PathVariable Long id,
                                           @Valid @RequestBody ProviderUpdateRequest request) {
        return Result.ok(modelService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        modelService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/test-connection")
    public Result<ConnectionTestResult> testConnection(@PathVariable Long id) {
        return Result.ok(modelService.testConnection(id));
    }
}
