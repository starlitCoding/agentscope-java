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

import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClient;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClientOptions;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxState;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import io.agentscope.sandboxservice.config.SandboxServiceProperties;
import java.util.LinkedHashMap;
import java.util.Map;

/** 基于本地 Docker 的沙箱后端适配器。 */
public class DockerSandboxProvider implements SandboxProvider {

    private final DockerSandboxClient client;
    private final SandboxServiceProperties.Docker properties;

    /** 创建 Docker 后端适配器，并持有 Docker 客户端与配置。 */
    public DockerSandboxProvider(
            DockerSandboxClient client, SandboxServiceProperties.Docker properties) {
        this.client = client;
        this.properties = properties;
    }

    /** 返回当前适配器对应的后端类型。 */
    @Override
    public SandboxBackendType backend() {
        return SandboxBackendType.DOCKER;
    }

    /** 按配置创建新的 Docker 沙箱。 */
    @Override
    public Sandbox create(SandboxKey key, SandboxSnapshotSpec snapshotSpec) {
        WorkspaceSpec workspaceSpec = new WorkspaceSpec();
        workspaceSpec.setRoot(properties.getWorkspaceRoot());
        return client.create(workspaceSpec, snapshotSpec, options());
    }

    /** 从持久化 JSON 状态恢复 Docker 沙箱对象。 */
    @Override
    public Sandbox resume(String sandboxStateJson, SandboxSnapshotSpec snapshotSpec) {
        return client.resume(client.deserializeState(sandboxStateJson, snapshotSpec));
    }

    /** 序列化 Docker 沙箱状态，供状态仓库保存。 */
    @Override
    public String serializeState(Sandbox sandbox) {
        return client.serializeState(sandbox.getState());
    }

    /** 返回 Docker 沙箱默认工作目录。 */
    @Override
    public String workspaceRoot() {
        return properties.getWorkspaceRoot();
    }

    /** 将 Docker 运行态转换为通用状态描述。 */
    @Override
    public SandboxRuntimeDescriptor describe(Sandbox sandbox) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        String workspaceRoot = workspaceRoot();
        if (sandbox.getState() instanceof DockerSandboxState dockerState) {
            putIfPresent(attributes, "containerId", dockerState.getContainerId());
            putIfPresent(attributes, "containerName", dockerState.getContainerName());
            if (dockerState.getWorkspaceRoot() != null) {
                workspaceRoot = dockerState.getWorkspaceRoot();
            }
        }
        return new SandboxRuntimeDescriptor(backend(), workspaceRoot, attributes);
    }

    /** 基于配置生成 Docker 客户端创建参数。 */
    private DockerSandboxClientOptions options() {
        DockerSandboxClientOptions options = new DockerSandboxClientOptions();
        options.setImage(properties.getImage());
        options.setWorkspaceRoot(properties.getWorkspaceRoot());
        options.setNetwork(properties.getNetwork());
        options.setMemorySizeBytes(properties.getMemorySizeBytes());
        options.setCpuCount(properties.getCpuCount());
        return options;
    }

    /** 当值存在时写入运行态属性，避免输出无意义的 null。 */
    private static void putIfPresent(Map<String, Object> attributes, String name, Object value) {
        if (value != null) {
            attributes.put(name, value);
        }
    }
}
