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

import java.util.Map;
import org.junit.jupiter.api.Test;

class SandboxRuntimeDescriptorTest {

    /** 验证运行时描述对象能承载后端类型、工作区和后端特定属性。 */
    @Test
    void describesBackendSpecificRuntimeAttributes() {
        SandboxRuntimeDescriptor descriptor =
                new SandboxRuntimeDescriptor(
                        SandboxBackendType.KUBERNETES,
                        "/workspace",
                        Map.of("namespace", "agents", "podName", "sandbox-0"));

        assertThat(descriptor.backend()).isEqualTo(SandboxBackendType.KUBERNETES);
        assertThat(descriptor.workspaceRoot()).isEqualTo("/workspace");
        assertThat(descriptor.attributes())
                .containsEntry("namespace", "agents")
                .containsEntry("podName", "sandbox-0");
    }
}
