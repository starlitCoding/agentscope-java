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
import io.agentscope.harness.agent.filesystem.model.FileDownloadResponse;
import io.agentscope.harness.agent.filesystem.model.FileUploadResponse;
import io.agentscope.harness.agent.filesystem.model.GlobResult;
import io.agentscope.harness.agent.filesystem.model.GrepResult;
import io.agentscope.harness.agent.filesystem.model.LsResult;
import io.agentscope.harness.agent.filesystem.model.ReadResult;
import io.agentscope.harness.agent.filesystem.model.WriteResult;
import io.agentscope.harness.agent.filesystem.sandbox.SandboxBackedFilesystem;
import java.util.List;
import java.util.Map;

/** 把文件工具调用委托给当前运行沙箱绑定的 Harness 文件系统代理。 */
public class HarnessSandboxFilesystemOperations implements SandboxFilesystemOperations {

    private final SandboxLifecycleService lifecycleService;

    /** 创建 Harness 文件系统适配器。 */
    public HarnessSandboxFilesystemOperations(SandboxLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    /** 返回当前业务键对应的运行态文件系统代理。 */
    private SandboxBackedFilesystem filesystem(SandboxKey key) {
        return lifecycleService.requireRunning(key).filesystem();
    }

    /** 读取沙箱内文件内容。 */
    @Override
    public ReadResult read(SandboxKey key, String path, int offset, int limit) {
        return filesystem(key).read(null, path, offset, limit);
    }

    /** 写入沙箱内新文件。 */
    @Override
    public WriteResult write(SandboxKey key, String path, String content) {
        return filesystem(key).write(null, path, content);
    }

    /** 编辑沙箱内已有文本文件。 */
    @Override
    public EditResult edit(
            SandboxKey key, String path, String oldString, String newString, boolean replaceAll) {
        return filesystem(key).edit(null, path, oldString, newString, replaceAll);
    }

    /** 列出沙箱内目录内容。 */
    @Override
    public LsResult list(SandboxKey key, String path) {
        return filesystem(key).ls(null, path);
    }

    /** 判断沙箱内路径是否存在。 */
    @Override
    public boolean exists(SandboxKey key, String path) {
        return filesystem(key).exists(null, path);
    }

    /** 按文件名模式查找沙箱内文件。 */
    @Override
    public GlobResult glob(SandboxKey key, String path, String pattern) {
        return filesystem(key).glob(null, pattern, path);
    }

    /** 在沙箱内按文本内容搜索文件。 */
    @Override
    public GrepResult grep(SandboxKey key, String path, String pattern, String glob) {
        return filesystem(key).grep(null, pattern, path, glob);
    }

    /** 删除沙箱内路径。 */
    @Override
    public WriteResult delete(SandboxKey key, String path) {
        return filesystem(key).delete(null, path);
    }

    /** 移动沙箱内路径。 */
    @Override
    public WriteResult move(SandboxKey key, String fromPath, String toPath) {
        return filesystem(key).move(null, fromPath, toPath);
    }

    /** 上传单个文件到沙箱。 */
    @Override
    public List<FileUploadResponse> upload(SandboxKey key, String path, byte[] content) {
        return filesystem(key).uploadFiles(null, List.of(Map.entry(path, content)));
    }

    /** 从沙箱下载单个文件。 */
    @Override
    public List<FileDownloadResponse> download(SandboxKey key, String path) {
        return filesystem(key).downloadFiles(null, List.of(path));
    }
}
