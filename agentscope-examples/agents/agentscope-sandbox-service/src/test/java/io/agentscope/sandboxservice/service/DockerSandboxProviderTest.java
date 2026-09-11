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
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class DockerSandboxProviderTest {

    /** 验证 Docker provider 会用配置创建 Docker options 和工作区根目录。 */
    @Test
    void createsSandboxWithConfiguredDockerOptions() {
        SandboxServiceProperties properties = new SandboxServiceProperties();
        properties.getDocker().setImage("ubuntu:22.04");
        properties.getDocker().setWorkspaceRoot("/work");
        properties.getDocker().setNetwork("none");
        properties.getDocker().setMemorySizeBytes(2048L);
        properties.getDocker().setCpuCount(3L);
        CapturingDockerSandboxClient client = new CapturingDockerSandboxClient();
        DockerSandboxProvider provider = new DockerSandboxProvider(client, properties.getDocker());

        provider.create(SandboxKey.of("alice", "conv-1"), new NoopSnapshotSpec());

        assertThat(provider.backend()).isEqualTo(SandboxBackendType.DOCKER);
        assertThat(client.workspaceSpec.getRoot()).isEqualTo("/work");
        assertThat(client.options.getImage()).isEqualTo("ubuntu:22.04");
        assertThat(client.options.getWorkspaceRoot()).isEqualTo("/work");
        assertThat(client.options.getNetwork()).isEqualTo("none");
        assertThat(client.options.getMemorySizeBytes()).isEqualTo(2048L);
        assertThat(client.options.getCpuCount()).isEqualTo(3L);
    }

    /** 验证 Docker provider 能从 DockerSandboxState 提取容器运行信息。 */
    @Test
    void describesDockerRuntimeState() {
        SandboxServiceProperties properties = new SandboxServiceProperties();
        DockerSandboxProvider provider =
                new DockerSandboxProvider(
                        new CapturingDockerSandboxClient(), properties.getDocker());
        DockerSandboxState state = new DockerSandboxState();
        state.setWorkspaceRoot("/workspace");
        state.setContainerId("container-1");
        state.setContainerName("sandbox-1");

        SandboxRuntimeDescriptor descriptor = provider.describe(new StateOnlySandbox(state));

        assertThat(descriptor.backend()).isEqualTo(SandboxBackendType.DOCKER);
        assertThat(descriptor.workspaceRoot()).isEqualTo("/workspace");
        assertThat(descriptor.attributes())
                .containsEntry("containerId", "container-1")
                .containsEntry("containerName", "sandbox-1");
    }

    static class CapturingDockerSandboxClient extends DockerSandboxClient {
        WorkspaceSpec workspaceSpec;
        DockerSandboxClientOptions options;

        /** 记录创建参数并返回最小沙箱对象，避免测试依赖真实 Docker。 */
        public Sandbox create(
                WorkspaceSpec workspaceSpec,
                SandboxSnapshotSpec snapshotSpec,
                DockerSandboxClientOptions options) {
            this.workspaceSpec = workspaceSpec;
            this.options = options;
            return new StateOnlySandbox(new DockerSandboxState());
        }
    }

    record StateOnlySandbox(DockerSandboxState state) implements Sandbox {

        /** 启动测试沙箱，无需真实资源。 */
        public void start() {}

        /** 停止测试沙箱，无需真实资源。 */
        public void stop() {}

        /** 关闭测试沙箱，无需真实资源。 */
        public void close() {}

        /** 返回测试沙箱运行状态。 */
        public boolean isRunning() {
            return true;
        }

        /** 返回测试状态对象。 */
        public SandboxState getState() {
            return state;
        }

        /** 返回空命令执行结果，避免真实 shell 调用。 */
        public ExecResult exec(
                RuntimeContext runtimeContext, String command, Integer timeoutSeconds) {
            return new ExecResult(0, "", "", false);
        }

        /** 返回空工作区归档。 */
        public InputStream persistWorkspace() {
            return new ByteArrayInputStream(new byte[0]);
        }

        /** 接收工作区归档但不做真实处理。 */
        public void hydrateWorkspace(InputStream archive) {}
    }
}
