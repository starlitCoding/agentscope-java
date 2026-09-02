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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.agentscope.harness.agent.filesystem.model.EditResult;
import io.agentscope.harness.agent.filesystem.model.FileData;
import io.agentscope.harness.agent.filesystem.model.FileDownloadResponse;
import io.agentscope.harness.agent.filesystem.model.FileUploadResponse;
import io.agentscope.harness.agent.filesystem.model.GlobResult;
import io.agentscope.harness.agent.filesystem.model.GrepResult;
import io.agentscope.harness.agent.filesystem.model.LsResult;
import io.agentscope.harness.agent.filesystem.model.ReadResult;
import io.agentscope.harness.agent.filesystem.model.WriteResult;
import io.agentscope.sandboxservice.config.SandboxServiceProperties;
import io.agentscope.sandboxservice.dto.FileReadResponse;
import io.agentscope.sandboxservice.error.SandboxServiceException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SandboxFileToolServiceTest {

    /** 验证 write 后 read 可以通过文件工具服务读回内容。 */
    @Test
    void writeThenReadUsesRunningSandboxFilesystem() {
        SandboxFileToolService service = serviceWithFakeRuntime();
        SandboxKey key = SandboxKey.of("alice", "conv-1");

        service.write(key, "/workspace/a.txt", "hello\n");
        FileReadResponse response = service.read(key, "/workspace/a.txt", 0, 20);

        assertThat(response.content()).isEqualTo("hello\n");
        assertThat(response.encoding()).isEqualTo("utf-8");
    }

    /** 验证 upload 和 download 使用 base64 包装二进制内容。 */
    @Test
    void uploadThenDownloadReturnsBase64Content() {
        SandboxFileToolService service = serviceWithFakeRuntime();
        SandboxKey key = SandboxKey.of("alice", "conv-1");

        service.upload(key, "/workspace/data.bin", "aGVsbG8K");
        io.agentscope.sandboxservice.dto.FileDownloadResponse response =
                service.download(key, "/workspace/data.bin");

        assertThat(response.base64Content()).isEqualTo("aGVsbG8K");
    }

    /** 验证不存在的文件读取失败会转成 FILE_OPERATION_FAILED。 */
    @Test
    void readFailureThrowsFileOperationError() {
        SandboxFileToolService service = serviceWithFakeRuntime();

        assertThatThrownBy(
                        () ->
                                service.read(
                                        SandboxKey.of("alice", "conv-1"),
                                        "/workspace/missing.txt",
                                        0,
                                        20))
                .isInstanceOf(SandboxServiceException.class)
                .hasMessageContaining("File operation failed");
    }

    /** 验证超出 workspaceRoot 的路径会被拒绝。 */
    @Test
    void rejectsPathOutsideWorkspaceRoot() {
        SandboxFileToolService service = serviceWithFakeRuntime();

        assertThatThrownBy(
                        () -> service.read(SandboxKey.of("alice", "conv-1"), "/etc/passwd", 0, 20))
                .isInstanceOf(SandboxServiceException.class);
    }

    private SandboxFileToolService serviceWithFakeRuntime() {
        SandboxServiceProperties properties = new SandboxServiceProperties();
        return new SandboxFileToolService(new FakeFilesystemOperations(), properties);
    }

    static class FakeFilesystemOperations implements SandboxFilesystemOperations {
        final Map<String, byte[]> files = new HashMap<>();

        public ReadResult read(SandboxKey key, String path, int offset, int limit) {
            byte[] content = files.get(path);
            if (content == null) {
                return ReadResult.fail("file_not_found");
            }
            return ReadResult.success(
                    new FileData(new String(content, StandardCharsets.UTF_8), "utf-8"));
        }

        public WriteResult write(SandboxKey key, String path, String content) {
            files.put(path, content.getBytes(StandardCharsets.UTF_8));
            return WriteResult.ok(path);
        }

        public EditResult edit(
                SandboxKey key,
                String path,
                String oldString,
                String newString,
                boolean replaceAll) {
            return EditResult.ok(path, 1);
        }

        public LsResult list(SandboxKey key, String path) {
            return LsResult.success(List.of());
        }

        public boolean exists(SandboxKey key, String path) {
            return files.containsKey(path);
        }

        public GlobResult glob(SandboxKey key, String path, String pattern) {
            return GlobResult.success(List.of());
        }

        public GrepResult grep(SandboxKey key, String path, String pattern, String glob) {
            return GrepResult.success(List.of());
        }

        public WriteResult delete(SandboxKey key, String path) {
            files.remove(path);
            return WriteResult.ok(path);
        }

        public WriteResult move(SandboxKey key, String fromPath, String toPath) {
            files.put(toPath, files.remove(fromPath));
            return WriteResult.ok(toPath);
        }

        public List<FileUploadResponse> upload(SandboxKey key, String path, byte[] content) {
            files.put(path, content);
            return List.of(FileUploadResponse.success(path));
        }

        public List<FileDownloadResponse> download(SandboxKey key, String path) {
            byte[] content = files.get(path);
            if (content == null) {
                return List.of(FileDownloadResponse.fail(path, "file_not_found"));
            }
            return List.of(FileDownloadResponse.success(path, content));
        }
    }
}
