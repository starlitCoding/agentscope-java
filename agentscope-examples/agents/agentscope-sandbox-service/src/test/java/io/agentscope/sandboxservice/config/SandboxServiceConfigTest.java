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

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClient;
import io.agentscope.sandboxservice.service.SandboxProvider;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SandboxServiceConfigTest {

    @TempDir Path tempDir;

    /** 验证没有外部 ObjectMapper Bean 时配置类仍能创建 Docker 后端。 */
    @Test
    void createsDockerProviderWithoutExternalObjectMapperBean() {
        SandboxServiceProperties properties = new SandboxServiceProperties();
        properties.setStateDir(tempDir.resolve("state"));
        properties.setSnapshotDir(tempDir.resolve("snapshots"));

        new ApplicationContextRunner()
                .withUserConfiguration(SandboxServiceConfig.class)
                .withBean(SandboxServiceProperties.class, () -> properties)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(DockerSandboxClient.class);
                            assertThat(context).hasSingleBean(SandboxProvider.class);
                        });
    }
}
