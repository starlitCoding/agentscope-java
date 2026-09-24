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
package io.agentscope.sandboxservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.extensions.sandbox.kubernetes.KubernetesHarnessSandboxJacksonModule;
import io.agentscope.extensions.sandbox.kubernetes.KubernetesSandboxClient;
import io.agentscope.extensions.sandbox.kubernetes.KubernetesSandboxClientOptions;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClient;
import io.agentscope.harness.agent.sandbox.json.HarnessSandboxJacksonModule;
import io.agentscope.harness.agent.sandbox.snapshot.LocalSnapshotSpec;
import io.agentscope.sandboxservice.service.DockerSandboxProvider;
import io.agentscope.sandboxservice.service.FileSandboxStateRepository;
import io.agentscope.sandboxservice.service.HarnessSandboxFilesystemOperations;
import io.agentscope.sandboxservice.service.KubernetesSandboxProvider;
import io.agentscope.sandboxservice.service.SandboxBackendType;
import io.agentscope.sandboxservice.service.SandboxFileToolService;
import io.agentscope.sandboxservice.service.SandboxFilesystemOperations;
import io.agentscope.sandboxservice.service.SandboxLifecycleService;
import io.agentscope.sandboxservice.service.SandboxOperationLockRegistry;
import io.agentscope.sandboxservice.service.SandboxProvider;
import io.agentscope.sandboxservice.service.SandboxStateRepository;
import java.io.IOException;
import java.nio.file.Files;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 创建沙箱服务需要的基础 Bean，并确保本地状态目录存在。 */
@Configuration
public class SandboxServiceConfig {

    /** 创建 Docker 沙箱客户端。 */
    @Bean
    public DockerSandboxClient dockerSandboxClient() {
        return new DockerSandboxClient(sandboxObjectMapper());
    }

    /** 创建 Kubernetes 沙箱客户端，默认参数承载超时等全局配置。 */
    @Bean
    public KubernetesSandboxClient kubernetesSandboxClient(SandboxServiceProperties properties) {
        return new KubernetesSandboxClient(
                kubernetesOptions(properties.getKubernetes()), sandboxObjectMapper());
    }

    /** 根据配置选择实际沙箱后端。 */
    @Bean
    public SandboxProvider sandboxProvider(
            DockerSandboxClient dockerSandboxClient,
            KubernetesSandboxClient kubernetesSandboxClient,
            SandboxServiceProperties properties) {
        if (properties.getBackend() == SandboxBackendType.KUBERNETES) {
            return new KubernetesSandboxProvider(
                    kubernetesSandboxClient, properties.getKubernetes());
        }
        return new DockerSandboxProvider(dockerSandboxClient, properties.getDocker());
    }

    /** 创建本地快照策略，并确保快照目录存在。 */
    @Bean
    public LocalSnapshotSpec localSnapshotSpec(SandboxServiceProperties properties)
            throws IOException {
        Files.createDirectories(properties.getSnapshotDir().toAbsolutePath().normalize());
        return new LocalSnapshotSpec(properties.getSnapshotDir().toAbsolutePath().normalize());
    }

    /** 创建本地状态仓库，并确保状态目录存在。 */
    @Bean
    public SandboxStateRepository sandboxStateRepository(SandboxServiceProperties properties) {
        return new FileSandboxStateRepository(properties.getStateDir(), sandboxObjectMapper());
    }

    /** 创建按业务键串行化操作互斥锁的注册表。 */
    @Bean
    public SandboxOperationLockRegistry sandboxOperationLockRegistry() {
        return new SandboxOperationLockRegistry();
    }

    /** 创建沙箱生命周期服务，串起 Docker 客户端、快照策略、状态仓库和锁。 */
    @Bean
    public SandboxLifecycleService sandboxLifecycleService(
            SandboxProvider sandboxProvider,
            LocalSnapshotSpec localSnapshotSpec,
            SandboxStateRepository sandboxStateRepository,
            SandboxOperationLockRegistry sandboxOperationLockRegistry) {
        return new SandboxLifecycleService(
                sandboxProvider,
                localSnapshotSpec,
                sandboxStateRepository,
                sandboxOperationLockRegistry);
    }

    /** 创建 Harness 文件系统操作适配器，绑定生命周期服务。 */
    @Bean
    public SandboxFilesystemOperations sandboxFilesystemOperations(
            SandboxLifecycleService sandboxLifecycleService) {
        return new HarnessSandboxFilesystemOperations(sandboxLifecycleService);
    }

    /** 创建文件工具服务，封装文件系统操作与路径校验。 */
    @Bean
    public SandboxFileToolService sandboxFileToolService(
            SandboxFilesystemOperations sandboxFilesystemOperations,
            SandboxServiceProperties properties) {
        return new SandboxFileToolService(sandboxFilesystemOperations, properties);
    }

    /** 创建同时支持 Docker 与 Kubernetes 状态 JSON 的 ObjectMapper。 */
    private static ObjectMapper sandboxObjectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .registerModule(new HarnessSandboxJacksonModule())
                .registerModule(new KubernetesHarnessSandboxJacksonModule());
    }

    /** 把 Spring Boot 配置转换为 Kubernetes 客户端默认参数。 */
    private static KubernetesSandboxClientOptions kubernetesOptions(
            SandboxServiceProperties.Kubernetes properties) {
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
}
