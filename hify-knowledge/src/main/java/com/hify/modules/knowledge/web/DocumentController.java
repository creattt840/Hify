package com.hify.modules.knowledge.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.knowledge.api.DocumentChunkResponse;
import com.hify.modules.knowledge.api.DocumentResponse;
import com.hify.modules.knowledge.api.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/api/v1/knowledge-bases/{kbId}/documents")
    public Result<DocumentResponse> upload(@PathVariable Long kbId,
                                            @RequestParam("file") MultipartFile file) {
        return Result.ok(documentService.upload(kbId, file));
    }

    @GetMapping("/api/v1/knowledge-bases/{kbId}/documents")
    public PageResult<List<DocumentResponse>> listDocuments(
            @PathVariable Long kbId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return documentService.listDocuments(kbId, page, size);
    }

    @GetMapping("/api/v1/documents/{id}")
    public Result<DocumentResponse> detail(@PathVariable Long id) {
        return Result.ok(documentService.getDocument(id));
    }

    @GetMapping("/api/v1/documents/{id}/chunks")
    public Result<List<DocumentChunkResponse>> chunks(@PathVariable Long id) {
        return Result.ok(documentService.getChunks(id));
    }

    @DeleteMapping("/api/v1/documents/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return Result.ok();
    }
}
