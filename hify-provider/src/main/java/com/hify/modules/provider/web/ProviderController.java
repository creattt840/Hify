package com.hify.modules.provider.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.provider.api.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    @GetMapping("/model-configs")
    public Result<List<ModelConfigResponse>> modelConfigs() {
        return Result.ok(modelService.listModelConfigs());
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

    // ── Provider 下的模型配置管理 ──

    @GetMapping("/{id}/model-configs")
    public Result<List<ModelConfigResponse>> providerModelConfigs(@PathVariable Long id) {
        return Result.ok(modelService.listModelConfigsByProviderId(id));
    }

    @PostMapping("/{id}/model-configs")
    public Result<ModelConfigResponse> createModelConfig(@PathVariable Long id,
                                                          @Valid @RequestBody ModelConfigRequest request) {
        return Result.ok(modelService.createModelConfig(id, request));
    }

    @PutMapping("/{providerId}/model-configs/{configId}")
    public Result<ModelConfigResponse> updateModelConfig(@PathVariable Long configId,
                                                          @Valid @RequestBody ModelConfigRequest request) {
        return Result.ok(modelService.updateModelConfig(configId, request));
    }

    @DeleteMapping("/{providerId}/model-configs/{configId}")
    public Result<Void> deleteModelConfig(@PathVariable Long configId) {
        modelService.deleteModelConfig(configId);
        return Result.ok();
    }

}
