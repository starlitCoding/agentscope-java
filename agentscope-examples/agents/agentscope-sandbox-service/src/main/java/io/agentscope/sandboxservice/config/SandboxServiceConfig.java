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

import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClient;
import io.agentscope.harness.agent.sandbox.snapshot.LocalSnapshotSpec;
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
        return new DockerSandboxClient();
    }

    /** 创建本地快照策略，并确保快照目录存在。 */
    @Bean
    public LocalSnapshotSpec localSnapshotSpec(SandboxServiceProperties properties)
            throws IOException {
        Files.createDirectories(properties.getSnapshotDir().toAbsolutePath().normalize());
        return new LocalSnapshotSpec(properties.getSnapshotDir().toAbsolutePath().normalize());
    }
}
