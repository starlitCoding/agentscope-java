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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSandboxStateRepositoryTest {

    @TempDir Path tempDir;

    /** 验证状态能保存到本地文件并按原始业务键加载回来。 */
    @Test
    void savesAndLoadsRecordBySandboxKey() {
        FileSandboxStateRepository repository =
                new FileSandboxStateRepository(
                        tempDir, new ObjectMapper().findAndRegisterModules());
        SandboxKey key = SandboxKey.of("alice", "conv-1");
        SandboxRecord record =
                new SandboxRecord(
                        "alice",
                        "conv-1",
                        SandboxLifecycleStatus.STOPPED,
                        "{\"type\":\"docker\"}",
                        Instant.parse("2026-09-02T10:00:00Z"),
                        Instant.parse("2026-09-02T10:01:00Z"));

        repository.save(key, record);

        assertThat(repository.find(key)).contains(record);
    }

    /** 验证 userId 和 sessionId 不会被直接拼成路径，避免路径穿越。 */
    @Test
    void encodesUnsafeUserAndSessionSegments() throws Exception {
        FileSandboxStateRepository repository =
                new FileSandboxStateRepository(
                        tempDir, new ObjectMapper().findAndRegisterModules());
        SandboxKey key = SandboxKey.of("../alice", "conv/1");
        SandboxRecord record =
                new SandboxRecord(
                        "../alice",
                        "conv/1",
                        SandboxLifecycleStatus.RUNNING,
                        "{\"type\":\"docker\"}",
                        Instant.parse("2026-09-02T10:00:00Z"),
                        Instant.parse("2026-09-02T10:01:00Z"));

        repository.save(key, record);

        assertThat(repository.find(key)).contains(record);
        assertThat(Files.exists(tempDir.resolve("../alice"))).isFalse();
    }

    /** 验证缺失状态文件时返回空，便于生命周期服务创建新沙箱。 */
    @Test
    void returnsEmptyWhenRecordDoesNotExist() {
        FileSandboxStateRepository repository =
                new FileSandboxStateRepository(
                        tempDir, new ObjectMapper().findAndRegisterModules());

        assertThat(repository.find(SandboxKey.of("bob", "conv-2"))).isEmpty();
    }

    /** 验证空业务键会被拒绝，避免生成不可定位的状态文件。 */
    @Test
    void rejectsBlankSandboxKeyValues() {
        assertThatThrownBy(() -> SandboxKey.of(" ", "conv-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SandboxKey.of("alice", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
