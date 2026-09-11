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

import io.agentscope.sandboxservice.service.SandboxBackendType;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 绑定 sandbox-service 配置，集中管理状态目录、快照目录和 Docker 默认参数。 */
@ConfigurationProperties(prefix = "sandbox-service")
public class SandboxServiceProperties {

    private SandboxBackendType backend = SandboxBackendType.DOCKER;
    private Path stateDir =
            Path.of(System.getProperty("user.home"), ".agentscope-sandbox-service", "state");
    private Path snapshotDir =
            Path.of(System.getProperty("user.home"), ".agentscope-sandbox-service", "snapshots");
    private Docker docker = new Docker();
    private Kubernetes kubernetes = new Kubernetes();
    private Exec exec = new Exec();

    /** 返回当前启用的沙箱后端。 */
    public SandboxBackendType getBackend() {
        return backend;
    }

    /** 设置当前启用的沙箱后端。 */
    public void setBackend(SandboxBackendType backend) {
        this.backend = backend != null ? backend : SandboxBackendType.DOCKER;
    }

    /** 返回本地状态 JSON 根目录。 */
    public Path getStateDir() {
        return stateDir;
    }

    /** 设置本地状态 JSON 根目录。 */
    public void setStateDir(Path stateDir) {
        this.stateDir = stateDir;
    }

    /** 返回本地快照 tar 根目录。 */
    public Path getSnapshotDir() {
        return snapshotDir;
    }

    /** 设置本地快照 tar 根目录。 */
    public void setSnapshotDir(Path snapshotDir) {
        this.snapshotDir = snapshotDir;
    }

    /** 返回 Docker 运行配置。 */
    public Docker getDocker() {
        return docker;
    }

    /** 设置 Docker 运行配置。 */
    public void setDocker(Docker docker) {
        this.docker = docker;
    }

    /** 返回 Kubernetes 运行配置。 */
    public Kubernetes getKubernetes() {
        return kubernetes;
    }

    /** 设置 Kubernetes 运行配置。 */
    public void setKubernetes(Kubernetes kubernetes) {
        this.kubernetes = kubernetes != null ? kubernetes : new Kubernetes();
    }

    /** 返回命令执行配置。 */
    public Exec getExec() {
        return exec;
    }

    /** 设置命令执行配置。 */
    public void setExec(Exec exec) {
        this.exec = exec;
    }

    /** Docker 沙箱默认参数。 */
    public static class Docker {
        private String image = "ubuntu:24.04";
        private String workspaceRoot = "/workspace";
        private String network = "none";
        private Long memorySizeBytes = 1073741824L;
        private Long cpuCount = 2L;

        /** 返回 Docker 镜像名。 */
        public String getImage() {
            return image;
        }

        /** 设置 Docker 镜像名。 */
        public void setImage(String image) {
            this.image = image;
        }

        /** 返回容器内工作区根目录。 */
        public String getWorkspaceRoot() {
            return workspaceRoot;
        }

        /** 设置容器内工作区根目录。 */
        public void setWorkspaceRoot(String workspaceRoot) {
            this.workspaceRoot = workspaceRoot;
        }

        /** 返回 Docker 网络模式或网络名。 */
        public String getNetwork() {
            return network;
        }

        /** 设置 Docker 网络模式或网络名。 */
        public void setNetwork(String network) {
            this.network = network;
        }

        /** 返回容器内存限制字节数。 */
        public Long getMemorySizeBytes() {
            return memorySizeBytes;
        }

        /** 设置容器内存限制字节数。 */
        public void setMemorySizeBytes(Long memorySizeBytes) {
            this.memorySizeBytes = memorySizeBytes;
        }

        /** 返回容器 CPU 限制。 */
        public Long getCpuCount() {
            return cpuCount;
        }

        /** 设置容器 CPU 限制。 */
        public void setCpuCount(Long cpuCount) {
            this.cpuCount = cpuCount;
        }
    }

    /** Kubernetes 沙箱默认参数。 */
    public static class Kubernetes {
        private String namespace = "default";
        private String warmPoolName = "agentscope-sandbox";
        private String workspaceRoot = "/workspace";
        private String fileApiBaseDir = "/workspace";
        private String apiUrl;
        private String gatewayName;
        private String gatewayNamespace;
        private String gatewayScheme = "http";
        private Integer serverPort = 8888;
        private Long sandboxReadyTimeoutSeconds = 180L;
        private Long cleanupTimeoutSeconds = 30L;
        private Long requestTimeoutSeconds = 180L;
        private Long perAttemptTimeoutSeconds = 60L;
        private Long portForwardTimeoutSeconds = 30L;

        /** 返回 SandboxClaim 所在命名空间。 */
        public String getNamespace() {
            return namespace;
        }

        /** 设置 SandboxClaim 所在命名空间。 */
        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        /** 返回 SandboxWarmPool 名称。 */
        public String getWarmPoolName() {
            return warmPoolName;
        }

        /** 设置 SandboxWarmPool 名称。 */
        public void setWarmPoolName(String warmPoolName) {
            this.warmPoolName = warmPoolName;
        }

        /** 返回沙箱内工作区根目录。 */
        public String getWorkspaceRoot() {
            return workspaceRoot;
        }

        /** 设置沙箱内工作区根目录。 */
        public void setWorkspaceRoot(String workspaceRoot) {
            this.workspaceRoot = workspaceRoot;
        }

        /** 返回运行时文件 API 根目录。 */
        public String getFileApiBaseDir() {
            return fileApiBaseDir;
        }

        /** 设置运行时文件 API 根目录。 */
        public void setFileApiBaseDir(String fileApiBaseDir) {
            this.fileApiBaseDir = fileApiBaseDir;
        }

        /** 返回直连运行时 API 的地址。 */
        public String getApiUrl() {
            return apiUrl;
        }

        /** 设置直连运行时 API 的地址。 */
        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        /** 返回 Gateway API 的 Gateway 名称。 */
        public String getGatewayName() {
            return gatewayName;
        }

        /** 设置 Gateway API 的 Gateway 名称。 */
        public void setGatewayName(String gatewayName) {
            this.gatewayName = gatewayName;
        }

        /** 返回 Gateway 所在命名空间。 */
        public String getGatewayNamespace() {
            return gatewayNamespace;
        }

        /** 设置 Gateway 所在命名空间。 */
        public void setGatewayNamespace(String gatewayNamespace) {
            this.gatewayNamespace = gatewayNamespace;
        }

        /** 返回 Gateway 访问协议。 */
        public String getGatewayScheme() {
            return gatewayScheme;
        }

        /** 设置 Gateway 访问协议。 */
        public void setGatewayScheme(String gatewayScheme) {
            this.gatewayScheme = gatewayScheme;
        }

        /** 返回运行时 HTTP 服务端口。 */
        public Integer getServerPort() {
            return serverPort;
        }

        /** 设置运行时 HTTP 服务端口。 */
        public void setServerPort(Integer serverPort) {
            this.serverPort = serverPort;
        }

        /** 返回等待 Sandbox ready 的超时时间。 */
        public Long getSandboxReadyTimeoutSeconds() {
            return sandboxReadyTimeoutSeconds;
        }

        /** 设置等待 Sandbox ready 的超时时间。 */
        public void setSandboxReadyTimeoutSeconds(Long sandboxReadyTimeoutSeconds) {
            this.sandboxReadyTimeoutSeconds = sandboxReadyTimeoutSeconds;
        }

        /** 返回清理 Kubernetes 沙箱资源的超时时间。 */
        public Long getCleanupTimeoutSeconds() {
            return cleanupTimeoutSeconds;
        }

        /** 设置清理 Kubernetes 沙箱资源的超时时间。 */
        public void setCleanupTimeoutSeconds(Long cleanupTimeoutSeconds) {
            this.cleanupTimeoutSeconds = cleanupTimeoutSeconds;
        }

        /** 返回运行时请求总超时时间。 */
        public Long getRequestTimeoutSeconds() {
            return requestTimeoutSeconds;
        }

        /** 设置运行时请求总超时时间。 */
        public void setRequestTimeoutSeconds(Long requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
        }

        /** 返回单次运行时请求尝试的超时时间。 */
        public Long getPerAttemptTimeoutSeconds() {
            return perAttemptTimeoutSeconds;
        }

        /** 设置单次运行时请求尝试的超时时间。 */
        public void setPerAttemptTimeoutSeconds(Long perAttemptTimeoutSeconds) {
            this.perAttemptTimeoutSeconds = perAttemptTimeoutSeconds;
        }

        /** 返回 port-forward 建立连接的超时时间。 */
        public Long getPortForwardTimeoutSeconds() {
            return portForwardTimeoutSeconds;
        }

        /** 设置 port-forward 建立连接的超时时间。 */
        public void setPortForwardTimeoutSeconds(Long portForwardTimeoutSeconds) {
            this.portForwardTimeoutSeconds = portForwardTimeoutSeconds;
        }
    }

    /** 命令执行默认参数。 */
    public static class Exec {
        private Integer defaultTimeoutSeconds = 120;

        /** 返回默认命令超时时间。 */
        public Integer getDefaultTimeoutSeconds() {
            return defaultTimeoutSeconds;
        }

        /** 设置默认命令超时时间。 */
        public void setDefaultTimeoutSeconds(Integer defaultTimeoutSeconds) {
            this.defaultTimeoutSeconds = defaultTimeoutSeconds;
        }
    }
}
