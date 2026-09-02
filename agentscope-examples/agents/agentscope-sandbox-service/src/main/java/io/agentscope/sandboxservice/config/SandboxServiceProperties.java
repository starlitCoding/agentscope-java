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

import java.nio.file.Path;

/** 绑定 sandbox-service 配置，集中管理状态目录、快照目录和 Docker 默认参数。 */
public class SandboxServiceProperties {

    private Path stateDir =
            Path.of(System.getProperty("user.home"), ".agentscope-sandbox-service", "state");
    private Path snapshotDir =
            Path.of(System.getProperty("user.home"), ".agentscope-sandbox-service", "snapshots");
    private Docker docker = new Docker();
    private Exec exec = new Exec();

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
