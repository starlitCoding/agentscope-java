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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/** 使用本地 JSON 文件保存沙箱状态。 */
public class FileSandboxStateRepository implements SandboxStateRepository {

    private final Path stateDir;
    private final ObjectMapper objectMapper;

    /** 创建文件状态仓库，并确保状态根目录存在。 */
    public FileSandboxStateRepository(Path stateDir, ObjectMapper objectMapper) {
        this.stateDir = stateDir.toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
        try {
            Files.createDirectories(this.stateDir);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to create sandbox state dir: " + this.stateDir, e);
        }
    }

    /** 按业务键读取状态文件，不存在时返回空。 */
    @Override
    public Optional<SandboxRecord> find(SandboxKey key) {
        Path path = statePath(key);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(path.toFile(), SandboxRecord.class));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read sandbox state file: " + path, e);
        }
    }

    /** 原子写入状态文件，避免进程中断留下半个 JSON。 */
    @Override
    public SandboxRecord save(SandboxKey key, SandboxRecord record) {
        Path path = statePath(key);
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writeValue(tmp.toFile(), record);
            Files.move(
                    tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return record;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write sandbox state file: " + path, e);
        }
    }

    /** 计算业务键对应的状态文件路径。 */
    private Path statePath(SandboxKey key) {
        return stateDir.resolve(key.safeUserSegment())
                .resolve(key.safeSessionSegment() + ".json")
                .normalize();
    }
}
