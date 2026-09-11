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

import io.agentscope.sandboxservice.service.SandboxBackendType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class SandboxServicePropertiesTest {

    /** 验证默认 application.yml 能绑定到配置对象，避免启动后 Docker 与 Kubernetes 参数为空。 */
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

        assertThat(properties.getBackend()).isEqualTo(SandboxBackendType.DOCKER);
        assertThat(properties.getDocker().getImage()).isEqualTo("ubuntu:24.04");
        assertThat(properties.getDocker().getWorkspaceRoot()).isEqualTo("/workspace");
        assertThat(properties.getDocker().getNetwork()).isEqualTo("none");
        assertThat(properties.getDocker().getMemorySizeBytes()).isEqualTo(1073741824L);
        assertThat(properties.getDocker().getCpuCount()).isEqualTo(2L);
        assertThat(properties.getKubernetes().getNamespace()).isEqualTo("default");
        assertThat(properties.getKubernetes().getWarmPoolName()).isEqualTo("agentscope-sandbox");
        assertThat(properties.getKubernetes().getWorkspaceRoot()).isEqualTo("/workspace");
        assertThat(properties.getKubernetes().getFileApiBaseDir()).isEqualTo("/workspace");
        assertThat(properties.getKubernetes().getGatewayScheme()).isEqualTo("http");
        assertThat(properties.getKubernetes().getServerPort()).isEqualTo(8888);
        assertThat(properties.getExec().getDefaultTimeoutSeconds()).isEqualTo(120);
    }

    /** 验证配置文件可以切换到 Kubernetes 后端并绑定连接参数。 */
    @Test
    void bindsKubernetesBackendOverride() {
        MockEnvironment environment =
                new MockEnvironment()
                        .withProperty("sandbox-service.backend", "kubernetes")
                        .withProperty("sandbox-service.kubernetes.namespace", "agents")
                        .withProperty("sandbox-service.kubernetes.warm-pool-name", "agent-pool")
                        .withProperty("sandbox-service.kubernetes.api-url", "http://sandbox.local")
                        .withProperty("sandbox-service.kubernetes.gateway-name", "sandbox-gateway")
                        .withProperty("sandbox-service.kubernetes.gateway-namespace", "infra")
                        .withProperty("sandbox-service.kubernetes.gateway-scheme", "https")
                        .withProperty("sandbox-service.kubernetes.server-port", "8888")
                        .withProperty(
                                "sandbox-service.kubernetes.sandbox-ready-timeout-seconds", "90")
                        .withProperty("sandbox-service.kubernetes.cleanup-timeout-seconds", "31")
                        .withProperty("sandbox-service.kubernetes.request-timeout-seconds", "91")
                        .withProperty(
                                "sandbox-service.kubernetes.per-attempt-timeout-seconds", "20")
                        .withProperty(
                                "sandbox-service.kubernetes.port-forward-timeout-seconds", "11");

        SandboxServiceProperties properties =
                Binder.get(environment)
                        .bind("sandbox-service", Bindable.of(SandboxServiceProperties.class))
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "sandbox-service properties not bound"));

        assertThat(properties.getBackend()).isEqualTo(SandboxBackendType.KUBERNETES);
        assertThat(properties.getKubernetes().getNamespace()).isEqualTo("agents");
        assertThat(properties.getKubernetes().getWarmPoolName()).isEqualTo("agent-pool");
        assertThat(properties.getKubernetes().getApiUrl()).isEqualTo("http://sandbox.local");
        assertThat(properties.getKubernetes().getGatewayName()).isEqualTo("sandbox-gateway");
        assertThat(properties.getKubernetes().getGatewayNamespace()).isEqualTo("infra");
        assertThat(properties.getKubernetes().getGatewayScheme()).isEqualTo("https");
        assertThat(properties.getKubernetes().getServerPort()).isEqualTo(8888);
        assertThat(properties.getKubernetes().getSandboxReadyTimeoutSeconds()).isEqualTo(90);
        assertThat(properties.getKubernetes().getCleanupTimeoutSeconds()).isEqualTo(31);
        assertThat(properties.getKubernetes().getRequestTimeoutSeconds()).isEqualTo(91);
        assertThat(properties.getKubernetes().getPerAttemptTimeoutSeconds()).isEqualTo(20);
        assertThat(properties.getKubernetes().getPortForwardTimeoutSeconds()).isEqualTo(11);
    }
}
