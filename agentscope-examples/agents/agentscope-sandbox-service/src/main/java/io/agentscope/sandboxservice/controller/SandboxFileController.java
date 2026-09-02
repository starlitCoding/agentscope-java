/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.agentscope.sandboxservice.controller;

import io.agentscope.sandboxservice.dto.FileDownloadResponse;
import io.agentscope.sandboxservice.dto.FileEditRequest;
import io.agentscope.sandboxservice.dto.FileExistsResponse;
import io.agentscope.sandboxservice.dto.FileGlobResponse;
import io.agentscope.sandboxservice.dto.FileGrepResponse;
import io.agentscope.sandboxservice.dto.FileListResponse;
import io.agentscope.sandboxservice.dto.FileMoveRequest;
import io.agentscope.sandboxservice.dto.FileReadResponse;
import io.agentscope.sandboxservice.dto.FileUploadRequest;
import io.agentscope.sandboxservice.dto.FileWriteRequest;
import io.agentscope.sandboxservice.service.SandboxFileToolService;
import io.agentscope.sandboxservice.service.SandboxKey;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 沙箱内文件工具 REST API。 */
@RestController
@RequestMapping("/api/sandboxes/files")
public class SandboxFileController {

    private final SandboxFileToolService fileToolService;

    /** 创建控制器，依赖文件工具服务。 */
    public SandboxFileController(SandboxFileToolService fileToolService) {
        this.fileToolService = fileToolService;
    }

    /** 读取沙箱内文件内容。 */
    @GetMapping("/read")
    public FileReadResponse read(
            @RequestParam String userId,
            @RequestParam String sessionId,
            @RequestParam String path,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "200") int limit) {
        return fileToolService.read(SandboxKey.of(userId, sessionId), path, offset, limit);
    }

    /** 写入沙箱内新文件。 */
    @PutMapping("/write")
    public void write(@Valid @RequestBody FileWriteRequest request) {
        fileToolService.write(
                SandboxKey.of(request.userId(), request.sessionId()),
                request.path(),
                request.content());
    }

    /** 编辑沙箱内已有文本文件。 */
    @PatchMapping("/edit")
    public void edit(@Valid @RequestBody FileEditRequest request) {
        fileToolService.edit(
                SandboxKey.of(request.userId(), request.sessionId()),
                request.path(),
                request.oldString(),
                request.newString(),
                request.replaceAll());
    }

    /** 列出沙箱内目录内容。 */
    @GetMapping("/list")
    public FileListResponse list(
            @RequestParam String userId,
            @RequestParam String sessionId,
            @RequestParam String path) {
        return fileToolService.list(SandboxKey.of(userId, sessionId), path);
    }

    /** 判断沙箱内路径是否存在。 */
    @GetMapping("/exists")
    public FileExistsResponse exists(
            @RequestParam String userId,
            @RequestParam String sessionId,
            @RequestParam String path) {
        return fileToolService.exists(SandboxKey.of(userId, sessionId), path);
    }

    /** 按文件名模式查找沙箱内文件。 */
    @GetMapping("/glob")
    public FileGlobResponse glob(
            @RequestParam String userId,
            @RequestParam String sessionId,
            @RequestParam String path,
            @RequestParam(required = false) String pattern) {
        return fileToolService.glob(SandboxKey.of(userId, sessionId), path, pattern);
    }

    /** 在沙箱内按文本内容搜索文件。 */
    @GetMapping("/grep")
    public FileGrepResponse grep(
            @RequestParam String userId,
            @RequestParam String sessionId,
            @RequestParam String path,
            @RequestParam String pattern,
            @RequestParam(required = false) String glob) {
        return fileToolService.grep(SandboxKey.of(userId, sessionId), path, pattern, glob);
    }

    /** 删除沙箱内路径。 */
    @DeleteMapping
    public void delete(
            @RequestParam String userId,
            @RequestParam String sessionId,
            @RequestParam String path) {
        fileToolService.delete(SandboxKey.of(userId, sessionId), path);
    }

    /** 移动沙箱内路径。 */
    @PostMapping("/move")
    public void move(@Valid @RequestBody FileMoveRequest request) {
        fileToolService.move(
                SandboxKey.of(request.userId(), request.sessionId()),
                request.fromPath(),
                request.toPath());
    }

    /** 上传文件到沙箱，内容使用 Base64 包装。 */
    @PostMapping("/upload")
    public void upload(@Valid @RequestBody FileUploadRequest request) {
        fileToolService.upload(
                SandboxKey.of(request.userId(), request.sessionId()),
                request.path(),
                request.base64Content());
    }

    /** 从沙箱下载文件，内容使用 Base64 包装。 */
    @GetMapping("/download")
    public FileDownloadResponse download(
            @RequestParam String userId,
            @RequestParam String sessionId,
            @RequestParam String path) {
        return fileToolService.download(SandboxKey.of(userId, sessionId), path);
    }
}
