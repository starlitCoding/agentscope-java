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

import io.agentscope.extensions.sandbox.kubernetes.KubernetesSandboxClient;
import io.agentscope.extensions.sandbox.kubernetes.KubernetesSandboxClientOptions;
import io.agentscope.extensions.sandbox.kubernetes.KubernetesSandboxState;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import io.agentscope.sandboxservice.config.SandboxServiceProperties;
import java.util.LinkedHashMap;
import java.util.Map;

/** 基于 agent-sandbox / Kubernetes 的沙箱后端适配器。 */
public class KubernetesSandboxProvider implements SandboxProvider {

    private final KubernetesSandboxClient client;
    private final SandboxServiceProperties.Kubernetes properties;

    /** 创建 Kubernetes 后端适配器，并持有 Kubernetes 客户端与配置。 */
    public KubernetesSandboxProvider(
            KubernetesSandboxClient client, SandboxServiceProperties.Kubernetes properties) {
        this.client = client;
        this.properties = properties;
    }

    /** 返回当前适配器对应的后端类型。 */
    @Override
    public SandboxBackendType backend() {
        return SandboxBackendType.KUBERNETES;
    }

    /** 按配置创建新的 Kubernetes 沙箱 Claim。 */
    @Override
    public Sandbox create(SandboxKey key, SandboxSnapshotSpec snapshotSpec) {
        WorkspaceSpec workspaceSpec = new WorkspaceSpec();
        workspaceSpec.setRoot(properties.getWorkspaceRoot());
        return client.create(workspaceSpec, snapshotSpec, options());
    }

    /** 从持久化 JSON 状态恢复 Kubernetes 沙箱对象。 */
    @Override
    public Sandbox resume(String sandboxStateJson, SandboxSnapshotSpec snapshotSpec) {
        return client.resume(client.deserializeState(sandboxStateJson));
    }

    /** 序列化 Kubernetes 沙箱状态，供状态仓库保存。 */
    @Override
    public String serializeState(Sandbox sandbox) {
        return client.serializeState(sandbox.getState());
    }

    /** 返回 Kubernetes 沙箱默认工作目录。 */
    @Override
    public String workspaceRoot() {
        return properties.getWorkspaceRoot();
    }

    /** 将 Kubernetes 运行态转换为通用状态描述。 */
    @Override
    public SandboxRuntimeDescriptor describe(Sandbox sandbox) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        String workspaceRoot = workspaceRoot();
        if (sandbox.getState() instanceof KubernetesSandboxState k8sState) {
            putIfPresent(attributes, "namespace", k8sState.getNamespace());
            putIfPresent(attributes, "claimName", k8sState.getClaimName());
            putIfPresent(attributes, "sandboxName", k8sState.getSandboxName());
            putIfPresent(attributes, "warmPoolName", k8sState.getWarmPoolName());
            putIfPresent(attributes, "podName", k8sState.getPodName());
            putIfPresent(attributes, "podIP", k8sState.getPodIP());
            putIfPresent(attributes, "fileApiBaseDir", k8sState.getFileApiBaseDir());
            if (k8sState.getWorkspaceRoot() != null) {
                workspaceRoot = k8sState.getWorkspaceRoot();
            }
        }
        return new SandboxRuntimeDescriptor(backend(), workspaceRoot, attributes);
    }

    /** 基于配置生成 Kubernetes 客户端创建参数。 */
    private KubernetesSandboxClientOptions options() {
        KubernetesSandboxClientOptions options = new KubernetesSandboxClientOptions();
        options.setNamespace(properties.getNamespace());
        options.setWarmPoolName(properties.getWarmPoolName());
        options.setWorkspaceRoot(properties.getWorkspaceRoot());
        options.setFileApiBaseDir(properties.getFileApiBaseDir());
        options.setApiUrl(properties.getApiUrl());
        options.setGatewayName(properties.getGatewayName());
        options.setGatewayNamespace(properties.getGatewayNamespace());
        options.setGatewayScheme(properties.getGatewayScheme());
        if (properties.getServerPort() != null) {
            options.setServerPort(properties.getServerPort());
        }
        if (properties.getSandboxReadyTimeoutSeconds() != null) {
            options.setSandboxReadyTimeoutSeconds(properties.getSandboxReadyTimeoutSeconds());
        }
        if (properties.getCleanupTimeoutSeconds() != null) {
            options.setCleanupTimeoutSeconds(properties.getCleanupTimeoutSeconds());
        }
        if (properties.getRequestTimeoutSeconds() != null) {
            options.setRequestTimeoutSeconds(properties.getRequestTimeoutSeconds());
        }
        if (properties.getPerAttemptTimeoutSeconds() != null) {
            options.setPerAttemptTimeoutSeconds(properties.getPerAttemptTimeoutSeconds());
        }
        if (properties.getPortForwardTimeoutSeconds() != null) {
            options.setPortForwardTimeoutSeconds(properties.getPortForwardTimeoutSeconds());
        }
        return options;
    }

    /** 当值存在时写入运行态属性，避免输出无意义的 null。 */
    private static void putIfPresent(Map<String, Object> attributes, String name, Object value) {
        if (value != null) {
            attributes.put(name, value);
        }
    }
}
