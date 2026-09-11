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

import io.agentscope.harness.agent.sandbox.ExecResult;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxException;
import io.agentscope.harness.agent.sandbox.SandboxState;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshot;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import io.agentscope.sandboxservice.dto.SandboxExecResponse;
import io.agentscope.sandboxservice.dto.SandboxStatusResponse;
import io.agentscope.sandboxservice.error.SandboxServiceException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 沙箱生命周期与命令执行核心服务，按 (userId, sessionId) 串行化操作并缓存运行态。 */
public class SandboxLifecycleService {

    private final SandboxProvider provider;
    private final SandboxSnapshotSpec snapshotSpec;
    private final SandboxStateRepository repository;
    private final SandboxOperationLockRegistry lockRegistry;
    private final Map<SandboxKey, SandboxRuntime> runtimes = new ConcurrentHashMap<>();

    /** 创建生命周期服务，依赖沙箱后端、快照策略、状态仓库和锁注册表。 */
    public SandboxLifecycleService(
            SandboxProvider provider,
            SandboxSnapshotSpec snapshotSpec,
            SandboxStateRepository repository,
            SandboxOperationLockRegistry lockRegistry) {
        this.provider = provider;
        this.snapshotSpec = snapshotSpec;
        this.repository = repository;
        this.lockRegistry = lockRegistry;
    }

    /** 创建沙箱：无状态时创建新沙箱，已有状态时按 start 处理恢复。 */
    public SandboxStatusResponse create(SandboxKey key) {
        return start(key);
    }

    /** 启动沙箱：有状态则 resume，无状态则 create，随后保存 RUNNING 状态。 */
    public SandboxStatusResponse start(SandboxKey key) {
        return lockRegistry.withLock(key, () -> doStart(key));
    }

    /** 停止沙箱：保存快照与 STOPPED 状态，但不释放容器，便于长会话复用。 */
    public SandboxStatusResponse stop(SandboxKey key) {
        return lockRegistry.withLock(key, () -> doStop(key));
    }

    /** 关闭沙箱：先保存快照再 shutdown 释放容器，保留状态 JSON 与快照 tar。 */
    public SandboxStatusResponse close(SandboxKey key) {
        return lockRegistry.withLock(key, () -> doClose(key));
    }

    /** 查询沙箱状态，无状态文件时返回空。 */
    public Optional<SandboxStatusResponse> status(SandboxKey key) {
        return lockRegistry.withLock(key, () -> doStatus(key));
    }

    /** 在运行态沙箱内执行命令；沙箱未运行时自动启动。 */
    public SandboxExecResponse exec(SandboxKey key, String command, Integer timeoutSeconds) {
        return lockRegistry.withLock(key, () -> doExec(key, command, timeoutSeconds));
    }

    /** 返回运行态沙箱；未运行时自动启动后返回。 */
    public SandboxRuntime requireRunning(SandboxKey key) {
        return lockRegistry.withLock(
                key,
                () -> {
                    SandboxRuntime runtime = runtimes.get(key);
                    if (runtime != null && runtime.sandbox().isRunning()) {
                        runtime.touch();
                        return runtime;
                    }
                    doStart(key);
                    return runtimes.get(key);
                });
    }

    /** 实现 start 语义，在锁内执行恢复或创建并保存运行状态。 */
    private SandboxStatusResponse doStart(SandboxKey key) {
        SandboxRuntime existing = runtimes.get(key);
        if (existing != null && existing.sandbox().isRunning()) {
            existing.touch();
            return toStatus(key, existing, repository.find(key).orElse(null));
        }

        Optional<SandboxRecord> record = repository.find(key);
        record.ifPresent(value -> validateBackend(key, value));
        Instant createdAt = record.map(SandboxRecord::createdAt).orElse(Instant.now());
        Sandbox sandbox;
        if (record.isPresent() && notBlank(record.get().sandboxStateJson())) {
            sandbox = provider.resume(record.get().sandboxStateJson(), snapshotSpec);
        } else {
            sandbox = createSandbox(key);
        }
        try {
            sandbox.start();
        } catch (Exception e) {
            throw SandboxServiceException.startFailed(key, e);
        }

        SandboxRuntime runtime = new SandboxRuntime(sandbox);
        runtimes.put(key, runtime);
        repository.save(key, toRecord(key, SandboxLifecycleStatus.RUNNING, sandbox, createdAt));
        return toStatus(key, runtime, repository.find(key).orElse(null));
    }

    /** 实现 stop 语义，在锁内停止运行态沙箱并保存 STOPPED 状态。 */
    private SandboxStatusResponse doStop(SandboxKey key) {
        SandboxRuntime runtime = runtimes.get(key);
        if (runtime != null && runtime.sandbox().isRunning()) {
            try {
                runtime.sandbox().stop();
            } catch (Exception e) {
                throw SandboxServiceException.execFailed(key, e);
            }
            SandboxRecord record = repository.find(key).orElse(null);
            Instant createdAt = record != null ? record.createdAt() : Instant.now();
            repository.save(
                    key,
                    toRecord(key, SandboxLifecycleStatus.STOPPED, runtime.sandbox(), createdAt));
            return toStatus(key, runtime, repository.find(key).orElse(null));
        }
        SandboxRecord record =
                repository.find(key).orElseThrow(() -> SandboxServiceException.notFound(key));
        validateBackend(key, record);
        return toStatus(key, null, record);
    }

    /** 实现 close 语义，在锁内保存状态并释放容器。 */
    private SandboxStatusResponse doClose(SandboxKey key) {
        SandboxRuntime runtime = runtimes.get(key);
        SandboxRecord record = repository.find(key).orElse(null);
        validateBackend(key, record);
        Instant createdAt = record != null ? record.createdAt() : Instant.now();
        Sandbox sandbox = runtime != null ? runtime.sandbox() : null;
        if (sandbox == null && record != null && notBlank(record.sandboxStateJson())) {
            sandbox = provider.resume(record.sandboxStateJson(), snapshotSpec);
        }
        if (sandbox != null) {
            try {
                sandbox.close();
            } catch (Exception e) {
                throw SandboxServiceException.execFailed(key, e);
            }
            runtimes.remove(key);
            repository.save(key, toRecord(key, SandboxLifecycleStatus.CLOSED, sandbox, createdAt));
            record = repository.find(key).orElse(record);
        } else if (record != null) {
            repository.save(
                    key,
                    new SandboxRecord(
                            record.userId(),
                            record.sessionId(),
                            record.backend(),
                            SandboxLifecycleStatus.CLOSED,
                            record.sandboxStateJson(),
                            record.createdAt(),
                            Instant.now()));
            record = repository.find(key).orElse(record);
        }
        return toStatus(key, null, record);
    }

    /** 实现 status 语义，在锁内读取状态仓库并组装响应。 */
    private Optional<SandboxStatusResponse> doStatus(SandboxKey key) {
        Optional<SandboxRecord> record = repository.find(key);
        if (record.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toStatus(key, runtimes.get(key), record.get()));
    }

    /** 实现 exec 语义，在锁内确保沙箱运行并执行命令。 */
    private SandboxExecResponse doExec(SandboxKey key, String command, Integer timeoutSeconds) {
        SandboxRuntime runtime = ensureRunning(key);
        try {
            ExecResult result = runtime.sandbox().exec(null, command, timeoutSeconds);
            return new SandboxExecResponse(
                    result.exitCode(),
                    result.stdout(),
                    result.stderr(),
                    result.combinedOutput(),
                    result.truncated());
        } catch (SandboxException.ExecTimeoutException e) {
            throw new SandboxServiceException(
                    SandboxServiceException.Code.SANDBOX_EXEC_TIMEOUT,
                    "Command timed out",
                    e.getMessage(),
                    key.userId(),
                    key.sessionId());
        } catch (Exception e) {
            throw SandboxServiceException.execFailed(key, e);
        }
    }

    /** 确保沙箱处于运行态；已在内存且运行则直接返回，否则走 start 逻辑。 */
    private SandboxRuntime ensureRunning(SandboxKey key) {
        SandboxRuntime runtime = runtimes.get(key);
        if (runtime != null && runtime.sandbox().isRunning()) {
            runtime.touch();
            return runtime;
        }
        doStart(key);
        return runtimes.get(key);
    }

    /** 使用当前后端配置创建新的沙箱。 */
    private Sandbox createSandbox(SandboxKey key) {
        try {
            return provider.create(key, snapshotSpec);
        } catch (Exception e) {
            throw SandboxServiceException.startFailed(key, e);
        }
    }

    /** 把沙箱当前状态序列化后包装为状态记录。 */
    private SandboxRecord toRecord(
            SandboxKey key, SandboxLifecycleStatus status, Sandbox sandbox, Instant createdAt) {
        String stateJson = provider.serializeState(sandbox);
        return new SandboxRecord(
                key.userId(),
                key.sessionId(),
                provider.backend(),
                status,
                stateJson,
                createdAt,
                Instant.now());
    }

    /** 组装状态响应，尽量从运行态沙箱读取后端运行信息和快照可恢复性。 */
    private SandboxStatusResponse toStatus(
            SandboxKey key, SandboxRuntime runtime, SandboxRecord record) {
        boolean running = runtime != null && runtime.sandbox().isRunning();
        SandboxRuntimeDescriptor descriptor =
                runtime != null
                        ? provider.describe(runtime.sandbox())
                        : new SandboxRuntimeDescriptor(
                                provider.backend(), provider.workspaceRoot(), Map.of());
        boolean snapshotRestorable = false;
        if (runtime != null) {
            SandboxState state = runtime.sandbox().getState();
            SandboxSnapshot snapshot = state.getSnapshot();
            if (snapshot != null) {
                try {
                    snapshotRestorable = snapshot.isRestorable();
                } catch (Exception e) {
                    snapshotRestorable = false;
                }
            }
        }
        SandboxLifecycleStatus status =
                record != null
                        ? record.status()
                        : (running
                                ? SandboxLifecycleStatus.RUNNING
                                : SandboxLifecycleStatus.STOPPED);
        return new SandboxStatusResponse(
                key.userId(),
                key.sessionId(),
                record != null ? record.backend() : descriptor.backend(),
                status,
                running,
                snapshotRestorable,
                descriptor.workspaceRoot(),
                descriptor.attributes(),
                record != null ? record.createdAt() : null,
                record != null ? record.updatedAt() : null);
    }

    /** 校验持久化状态的后端类型与当前 provider 一致，避免跨后端恢复。 */
    private void validateBackend(SandboxKey key, SandboxRecord record) {
        if (record != null && record.backend() != provider.backend()) {
            throw SandboxServiceException.backendMismatch(
                    key, record.backend(), provider.backend());
        }
    }

    /** 判断字符串非空，用于区分可恢复状态与无状态场景。 */
    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
