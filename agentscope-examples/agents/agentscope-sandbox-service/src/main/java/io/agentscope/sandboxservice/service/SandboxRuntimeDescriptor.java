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

import java.util.Map;

/** 描述一个运行中沙箱的通用信息和后端特定属性。 */
public record SandboxRuntimeDescriptor(
        SandboxBackendType backend, String workspaceRoot, Map<String, Object> attributes) {

    /** 创建描述对象时复制属性表，避免调用方修改内部状态。 */
    public SandboxRuntimeDescriptor {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
