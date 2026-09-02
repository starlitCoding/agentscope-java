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
import java.util.List;

/** 隔离文件工具服务和 Harness 文件系统代理，便于单元测试替换底层实现。 */
public interface SandboxFilesystemOperations {

    /** 读取沙箱内文件内容。 */
    ReadResult read(SandboxKey key, String path, int offset, int limit);

    /** 写入沙箱内新文件。 */
    WriteResult write(SandboxKey key, String path, String content);

    /** 编辑沙箱内已有文本文件。 */
    EditResult edit(
            SandboxKey key, String path, String oldString, String newString, boolean replaceAll);

    /** 列出沙箱内目录内容。 */
    LsResult list(SandboxKey key, String path);

    /** 判断沙箱内路径是否存在。 */
    boolean exists(SandboxKey key, String path);

    /** 按文件名模式查找沙箱内文件。 */
    GlobResult glob(SandboxKey key, String path, String pattern);

    /** 在沙箱内按文本内容搜索文件。 */
    GrepResult grep(SandboxKey key, String path, String pattern, String glob);

    /** 删除沙箱内路径。 */
    WriteResult delete(SandboxKey key, String path);

    /** 移动沙箱内路径。 */
    WriteResult move(SandboxKey key, String fromPath, String toPath);

    /** 上传单个文件到沙箱。 */
    List<FileUploadResponse> upload(SandboxKey key, String path, byte[] content);

    /** 从沙箱下载单个文件。 */
    List<FileDownloadResponse> download(SandboxKey key, String path);
}
