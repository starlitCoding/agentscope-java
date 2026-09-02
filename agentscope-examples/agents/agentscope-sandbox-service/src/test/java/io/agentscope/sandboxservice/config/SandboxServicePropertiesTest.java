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

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class SandboxServicePropertiesTest {

    /** 验证默认 application.yml 能绑定到配置对象，避免启动后 Docker 参数为空。 */
    @Test
    void bindsDefaultSandboxServiceProperties() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        environment
                .getPropertySources()
                .addFirst(loader.load("test", new ClassPathResource("application.yml")).get(0));

        SandboxServiceProperties properties =
                Binder.get(environment)
                        .bind("sandbox-service", Bindable.of(SandboxServiceProperties.class))
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "sandbox-service properties not bound"));

        assertThat(properties.getDocker().getImage()).isEqualTo("ubuntu:24.04");
        assertThat(properties.getDocker().getWorkspaceRoot()).isEqualTo("/workspace");
        assertThat(properties.getDocker().getNetwork()).isEqualTo("none");
        assertThat(properties.getDocker().getMemorySizeBytes()).isEqualTo(1073741824L);
        assertThat(properties.getDocker().getCpuCount()).isEqualTo(2L);
        assertThat(properties.getExec().getDefaultTimeoutSeconds()).isEqualTo(120);
    }
}
