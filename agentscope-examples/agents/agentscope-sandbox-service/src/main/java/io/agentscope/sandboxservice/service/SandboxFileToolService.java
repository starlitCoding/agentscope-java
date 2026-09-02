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
package io.agentscope.sandboxservice.service;

import io.agentscope.harness.agent.filesystem.model.EditResult;
import io.agentscope.harness.agent.filesystem.model.FileData;
import io.agentscope.harness.agent.filesystem.model.FileUploadResponse;
import io.agentscope.harness.agent.filesystem.model.ReadResult;
import io.agentscope.harness.agent.filesystem.model.WriteResult;
import io.agentscope.sandboxservice.config.SandboxServiceProperties;
import io.agentscope.sandboxservice.dto.FileDownloadResponse;
import io.agentscope.sandboxservice.dto.FileExistsResponse;
import io.agentscope.sandboxservice.dto.FileGlobResponse;
import io.agentscope.sandboxservice.dto.FileGrepResponse;
import io.agentscope.sandboxservice.dto.FileListResponse;
import io.agentscope.sandboxservice.dto.FileReadResponse;
import io.agentscope.sandboxservice.error.SandboxServiceException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/** 文件工具服务，校验路径并封装 Harness 文件系统操作的返回结果。 */
public class SandboxFileToolService {

    private final SandboxFilesystemOperations filesystemOperations;
    private final SandboxServiceProperties properties;

    /** 创建文件工具服务，依赖底层文件系统操作和配置。 */
    public SandboxFileToolService(
            SandboxFilesystemOperations filesystemOperations, SandboxServiceProperties properties) {
        this.filesystemOperations = filesystemOperations;
        this.properties = properties;
    }

    /** 读取沙箱内文件内容，返回文本与编码信息。 */
    public FileReadResponse read(SandboxKey key, String path, int offset, int limit) {
        validatePath(key, path);
        ReadResult result = filesystemOperations.read(key, path, offset, limit);
        if (!result.isSuccess()) {
            throw SandboxServiceException.fileOperation(key, result.error());
        }
        FileData data = result.fileData();
        return new FileReadResponse(path, data.content(), data.encoding());
    }

    /** 写入沙箱内新文件。 */
    public void write(SandboxKey key, String path, String content) {
        validatePath(key, path);
        WriteResult result = filesystemOperations.write(key, path, content == null ? "" : content);
        if (!result.isSuccess()) {
            throw SandboxServiceException.fileOperation(key, result.error());
        }
    }

    /** 编辑沙箱内已有文本文件。 */
    public void edit(
            SandboxKey key, String path, String oldString, String newString, boolean replaceAll) {
        validatePath(key, path);
        EditResult result = filesystemOperations.edit(key, path, oldString, newString, replaceAll);
        if (!result.isSuccess()) {
            throw SandboxServiceException.fileOperation(key, result.error());
        }
    }

    /** 列出沙箱内目录内容。 */
    public FileListResponse list(SandboxKey key, String path) {
        validatePath(key, path);
        return new FileListResponse(filesystemOperations.list(key, path).entries());
    }

    /** 判断沙箱内路径是否存在。 */
    public FileExistsResponse exists(SandboxKey key, String path) {
        validatePath(key, path);
        return new FileExistsResponse(path, filesystemOperations.exists(key, path));
    }

    /** 按文件名模式查找沙箱内文件。 */
    public FileGlobResponse glob(SandboxKey key, String path, String pattern) {
        validatePath(key, path);
        return new FileGlobResponse(filesystemOperations.glob(key, path, pattern).matches());
    }

    /** 在沙箱内按文本内容搜索文件。 */
    public FileGrepResponse grep(SandboxKey key, String path, String pattern, String glob) {
        validatePath(key, path);
        return new FileGrepResponse(filesystemOperations.grep(key, path, pattern, glob).matches());
    }

    /** 删除沙箱内路径。 */
    public void delete(SandboxKey key, String path) {
        validatePath(key, path);
        WriteResult result = filesystemOperations.delete(key, path);
        if (!result.isSuccess()) {
            throw SandboxServiceException.fileOperation(key, result.error());
        }
    }

    /** 移动沙箱内路径。 */
    public void move(SandboxKey key, String fromPath, String toPath) {
        validatePath(key, fromPath);
        validatePath(key, toPath);
        WriteResult result = filesystemOperations.move(key, fromPath, toPath);
        if (!result.isSuccess()) {
            throw SandboxServiceException.fileOperation(key, result.error());
        }
    }

    /** 上传单个文件到沙箱，内容由 Base64 解码。 */
    public void upload(SandboxKey key, String path, String base64Content) {
        validatePath(key, path);
        byte[] content;
        try {
            content = Base64.getDecoder().decode(base64Content);
        } catch (IllegalArgumentException e) {
            throw SandboxServiceException.invalid(key, "base64Content is not valid base64");
        }
        List<FileUploadResponse> results = filesystemOperations.upload(key, path, content);
        for (FileUploadResponse result : results) {
            if (!result.isSuccess()) {
                throw SandboxServiceException.fileOperation(key, result.error());
            }
        }
    }

    /** 从沙箱下载单个文件，内容使用 Base64 包装。 */
    public FileDownloadResponse download(SandboxKey key, String path) {
        validatePath(key, path);
        List<io.agentscope.harness.agent.filesystem.model.FileDownloadResponse> results =
                filesystemOperations.download(key, path);
        for (io.agentscope.harness.agent.filesystem.model.FileDownloadResponse result : results) {
            if (!result.isSuccess()) {
                throw SandboxServiceException.fileOperation(key, result.error());
            }
            return new FileDownloadResponse(
                    path, Base64.getEncoder().encodeToString(result.content()));
        }
        throw SandboxServiceException.fileOperation(key, "No download result returned");
    }

    /** 校验路径非空且位于配置的 workspaceRoot 之下，避免访问容器内其他区域。 */
    private void validatePath(SandboxKey key, String path) {
        if (path == null || path.isBlank()) {
            throw SandboxServiceException.invalid(key, "path must not be blank");
        }
        String workspaceRoot = properties.getDocker().getWorkspaceRoot();
        Path root = Path.of(workspaceRoot);
        if (!Path.of(path).normalize().startsWith(root.normalize())) {
            throw SandboxServiceException.invalid(
                    key, "path must start with workspaceRoot: " + workspaceRoot);
        }
    }
}
