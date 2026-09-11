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

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.sandbox.ExecResult;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxState;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClient;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClientOptions;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxState;
import io.agentscope.harness.agent.sandbox.snapshot.NoopSnapshotSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import io.agentscope.sandboxservice.config.SandboxServiceProperties;
import io.agentscope.sandboxservice.dto.SandboxExecResponse;
import io.agentscope.sandboxservice.dto.SandboxStatusResponse;
import io.agentscope.sandboxservice.error.SandboxServiceException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SandboxLifecycleServiceTest {

    /** 验证无历史状态时会创建新沙箱、启动并保存 RUNNING 状态。 */
    @Test
    void startsNewSandboxWhenNoRecordExists() {
        InMemoryRepository repository = new InMemoryRepository();
        FakeDockerSandboxClient client = new FakeDockerSandboxClient();
        SandboxLifecycleService service = newService(repository, client);

        SandboxStatusResponse response = service.start(SandboxKey.of("alice", "conv-1"));

        assertThat(response.status()).isEqualTo(SandboxLifecycleStatus.RUNNING);
        assertThat(client.createCalls).isEqualTo(1);
        assertThat(client.latestSandbox.started).isTrue();
        assertThat(repository.record).isPresent();
        assertThat(repository.record.orElseThrow().status())
                .isEqualTo(SandboxLifecycleStatus.RUNNING);
    }

    /** 验证有历史状态时会 resume 而不是 create。 */
    @Test
    void resumesSandboxWhenRecordExists() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.record =
                Optional.of(
                        new SandboxRecord(
                                "alice",
                                "conv-1",
                                SandboxBackendType.DOCKER,
                                SandboxLifecycleStatus.STOPPED,
                                "state-json",
                                Instant.now(),
                                Instant.now()));
        FakeDockerSandboxClient client = new FakeDockerSandboxClient();
        SandboxLifecycleService service = newService(repository, client);

        service.start(SandboxKey.of("alice", "conv-1"));

        assertThat(client.resumeCalls).isEqualTo(1);
        assertThat(client.createCalls).isZero();
    }

    /** 验证 stop 会保存快照语义对应的状态并保持容器不 shutdown。 */
    @Test
    void stopPersistsStateWithoutShutdown() {
        InMemoryRepository repository = new InMemoryRepository();
        FakeDockerSandboxClient client = new FakeDockerSandboxClient();
        SandboxLifecycleService service = newService(repository, client);
        SandboxKey key = SandboxKey.of("alice", "conv-1");

        service.start(key);
        service.stop(key);

        assertThat(client.latestSandbox.stopped).isTrue();
        assertThat(client.latestSandbox.shutdown).isFalse();
        assertThat(repository.record.orElseThrow().status())
                .isEqualTo(SandboxLifecycleStatus.STOPPED);
    }

    /** 验证 close 会先保存状态再释放容器。 */
    @Test
    void closeStopsAndShutdownsSandbox() {
        InMemoryRepository repository = new InMemoryRepository();
        FakeDockerSandboxClient client = new FakeDockerSandboxClient();
        SandboxLifecycleService service = newService(repository, client);
        SandboxKey key = SandboxKey.of("alice", "conv-1");

        service.start(key);
        service.close(key);

        assertThat(client.latestSandbox.stopped).isTrue();
        assertThat(client.latestSandbox.shutdown).isTrue();
        assertThat(repository.record.orElseThrow().status())
                .isEqualTo(SandboxLifecycleStatus.CLOSED);
    }

    /** 验证 exec 会复用运行态沙箱并返回命令输出。 */
    @Test
    void execRunsCommandInRunningSandbox() {
        InMemoryRepository repository = new InMemoryRepository();
        FakeDockerSandboxClient client = new FakeDockerSandboxClient();
        SandboxLifecycleService service = newService(repository, client);
        SandboxKey key = SandboxKey.of("alice", "conv-1");
        service.start(key);

        SandboxExecResponse response = service.exec(key, "echo hi", 30);

        assertThat(response.exitCode()).isZero();
        assertThat(response.stdout()).isEqualTo("echo hi");
    }

    /** 验证已有状态后端和当前 provider 不一致时拒绝恢复。 */
    @Test
    void rejectsBackendMismatchWhenRecordExists() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.record =
                Optional.of(
                        new SandboxRecord(
                                "alice",
                                "conv-1",
                                SandboxBackendType.DOCKER,
                                SandboxLifecycleStatus.STOPPED,
                                "state-json",
                                Instant.now(),
                                Instant.now()));
        SandboxLifecycleService service =
                new SandboxLifecycleService(
                        new FakeProvider(SandboxBackendType.KUBERNETES),
                        new NoopSnapshotSpec(),
                        repository,
                        new SandboxOperationLockRegistry());

        assertThatThrownBy(() -> service.start(SandboxKey.of("alice", "conv-1")))
                .isInstanceOf(SandboxServiceException.class)
                .hasMessageContaining("backend mismatch");
    }

    private SandboxLifecycleService newService(
            InMemoryRepository repository, FakeDockerSandboxClient client) {
        SandboxServiceProperties properties = new SandboxServiceProperties();
        return new SandboxLifecycleService(
                new DockerSandboxProvider(client, properties.getDocker()),
                new NoopSnapshotSpec(),
                repository,
                new SandboxOperationLockRegistry());
    }

    static class InMemoryRepository implements SandboxStateRepository {
        Optional<SandboxRecord> record = Optional.empty();

        public Optional<SandboxRecord> find(SandboxKey key) {
            return record;
        }

        public SandboxRecord save(SandboxKey key, SandboxRecord record) {
            this.record = Optional.of(record);
            return record;
        }
    }

    static class FakeDockerSandboxClient extends DockerSandboxClient {
        int createCalls;
        int resumeCalls;
        FakeSandbox latestSandbox;

        public Sandbox create(
                WorkspaceSpec workspaceSpec,
                SandboxSnapshotSpec snapshotSpec,
                DockerSandboxClientOptions options) {
            createCalls++;
            latestSandbox = new FakeSandbox();
            return latestSandbox;
        }

        public Sandbox resume(SandboxState state) {
            resumeCalls++;
            latestSandbox = new FakeSandbox();
            return latestSandbox;
        }

        public void delete(Sandbox sandbox) {}

        public String serializeState(SandboxState state) {
            return "state-json";
        }

        public SandboxState deserializeState(String json) {
            DockerSandboxState state = new DockerSandboxState();
            state.setSessionId("resumed-session");
            state.setWorkspaceSpec(new WorkspaceSpec());
            state.setWorkspaceRoot("/workspace");
            state.setImage("ubuntu:24.04");
            return state;
        }

        public SandboxState deserializeState(String json, SandboxSnapshotSpec snapshotSpec) {
            return deserializeState(json);
        }
    }

    static class FakeSandbox implements Sandbox {
        boolean started;
        boolean stopped;
        boolean shutdown;
        DockerSandboxState state = new DockerSandboxState();

        public void start() {
            started = true;
        }

        public void stop() {
            stopped = true;
        }

        public void shutdown() {
            shutdown = true;
        }

        public void close() {
            stop();
            shutdown();
        }

        public boolean isRunning() {
            return started && !stopped;
        }

        public SandboxState getState() {
            return state;
        }

        public ExecResult exec(
                RuntimeContext runtimeContext, String command, Integer timeoutSeconds) {
            return new ExecResult(0, command, "", false);
        }

        public InputStream persistWorkspace() {
            return new ByteArrayInputStream(new byte[0]);
        }

        public void hydrateWorkspace(InputStream archive) {}
    }

    record FakeProvider(SandboxBackendType backend) implements SandboxProvider {

        /** 创建测试沙箱。 */
        public Sandbox create(SandboxKey key, SandboxSnapshotSpec snapshotSpec) {
            return new FakeSandbox();
        }

        /** 恢复测试沙箱。 */
        public Sandbox resume(String sandboxStateJson, SandboxSnapshotSpec snapshotSpec) {
            return new FakeSandbox();
        }

        /** 返回固定测试状态 JSON。 */
        public String serializeState(Sandbox sandbox) {
            return "state-json";
        }

        /** 返回测试工作目录。 */
        public String workspaceRoot() {
            return "/workspace";
        }

        /** 返回测试运行态描述。 */
        public SandboxRuntimeDescriptor describe(Sandbox sandbox) {
            return new SandboxRuntimeDescriptor(backend, "/workspace", Map.of());
        }
    }
}
