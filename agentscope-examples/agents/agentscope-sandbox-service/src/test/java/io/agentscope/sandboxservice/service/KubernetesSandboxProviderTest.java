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
import io.agentscope.extensions.sandbox.kubernetes.KubernetesSandboxClient;
import io.agentscope.extensions.sandbox.kubernetes.KubernetesSandboxClientOptions;
import io.agentscope.extensions.sandbox.kubernetes.KubernetesSandboxState;
import io.agentscope.extensions.sandbox.kubernetes.client.config.DirectConnectionConfig;
import io.agentscope.extensions.sandbox.kubernetes.client.config.GatewayConnectionConfig;
import io.agentscope.extensions.sandbox.kubernetes.client.config.LocalTunnelConnectionConfig;
import io.agentscope.harness.agent.sandbox.ExecResult;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxState;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.snapshot.NoopSnapshotSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import io.agentscope.sandboxservice.config.SandboxServiceProperties;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class KubernetesSandboxProviderTest {

    /** 验证 Kubernetes provider 会用配置创建 Kubernetes options 和工作区根目录。 */
    @Test
    void createsSandboxWithConfiguredKubernetesOptions() {
        SandboxServiceProperties properties = new SandboxServiceProperties();
        properties.getKubernetes().setNamespace("agents");
        properties.getKubernetes().setWarmPoolName("agent-pool");
        properties.getKubernetes().setWorkspaceRoot("/work");
        properties.getKubernetes().setFileApiBaseDir("/work");
        properties.getKubernetes().setServerPort(9999);
        CapturingKubernetesSandboxClient client = new CapturingKubernetesSandboxClient();
        KubernetesSandboxProvider provider =
                new KubernetesSandboxProvider(client, properties.getKubernetes());

        provider.create(SandboxKey.of("alice", "conv-1"), new NoopSnapshotSpec());

        assertThat(provider.backend()).isEqualTo(SandboxBackendType.KUBERNETES);
        assertThat(client.workspaceSpec.getRoot()).isEqualTo("/work");
        assertThat(client.options.getNamespace()).isEqualTo("agents");
        assertThat(client.options.getWarmPoolName()).isEqualTo("agent-pool");
        assertThat(client.options.getWorkspaceRoot()).isEqualTo("/work");
        assertThat(client.options.getFileApiBaseDir()).isEqualTo("/work");
        assertThat(client.options.getServerPort()).isEqualTo(9999);
    }

    /** 验证 Kubernetes 直连配置选择。 */
    @Test
    void connectionConfigUsesDirectWhenApiUrlIsSet() {
        KubernetesSandboxClientOptions options = new KubernetesSandboxClientOptions();
        options.setApiUrl("http://sandbox.local");

        assertThat(KubernetesSandboxClient.toConnectionConfig(options))
                .isInstanceOf(DirectConnectionConfig.class);
    }

    /** 验证 Kubernetes gateway 配置选择。 */
    @Test
    void connectionConfigUsesGatewayWhenGatewayNameIsSet() {
        KubernetesSandboxClientOptions options = new KubernetesSandboxClientOptions();
        options.setGatewayName("sandbox-gateway");

        assertThat(KubernetesSandboxClient.toConnectionConfig(options))
                .isInstanceOf(GatewayConnectionConfig.class);
    }

    /** 验证未配置 direct/gateway 时使用本地端口转发。 */
    @Test
    void connectionConfigUsesLocalTunnelByDefault() {
        KubernetesSandboxClientOptions options = new KubernetesSandboxClientOptions();

        assertThat(KubernetesSandboxClient.toConnectionConfig(options))
                .isInstanceOf(LocalTunnelConnectionConfig.class);
    }

    /** 验证 Kubernetes provider 能从 KubernetesSandboxState 提取运行信息。 */
    @Test
    void describesKubernetesRuntimeState() {
        SandboxServiceProperties properties = new SandboxServiceProperties();
        KubernetesSandboxProvider provider =
                new KubernetesSandboxProvider(
                        new CapturingKubernetesSandboxClient(), properties.getKubernetes());
        KubernetesSandboxState state = new KubernetesSandboxState();
        state.setNamespace("agents");
        state.setClaimName("claim-1");
        state.setSandboxName("sandbox-1");
        state.setWarmPoolName("pool-1");
        state.setPodName("pod-1");
        state.setPodIP("10.0.0.1");
        state.setWorkspaceRoot("/workspace");

        SandboxRuntimeDescriptor descriptor = provider.describe(new StateOnlySandbox(state));

        assertThat(descriptor.backend()).isEqualTo(SandboxBackendType.KUBERNETES);
        assertThat(descriptor.workspaceRoot()).isEqualTo("/workspace");
        assertThat(descriptor.attributes())
                .containsEntry("namespace", "agents")
                .containsEntry("claimName", "claim-1")
                .containsEntry("sandboxName", "sandbox-1")
                .containsEntry("warmPoolName", "pool-1")
                .containsEntry("podName", "pod-1")
                .containsEntry("podIP", "10.0.0.1");
    }

    static class CapturingKubernetesSandboxClient extends KubernetesSandboxClient {
        WorkspaceSpec workspaceSpec;
        KubernetesSandboxClientOptions options;

        /** 记录创建参数并返回最小沙箱对象，避免测试依赖真实 Kubernetes。 */
        public Sandbox create(
                WorkspaceSpec workspaceSpec,
                SandboxSnapshotSpec snapshotSpec,
                KubernetesSandboxClientOptions options) {
            this.workspaceSpec = workspaceSpec;
            this.options = options;
            return new StateOnlySandbox(new KubernetesSandboxState());
        }
    }

    record StateOnlySandbox(KubernetesSandboxState state) implements Sandbox {

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
