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
package io.agentscope.sandboxservice.dto;

import io.agentscope.sandboxservice.service.SandboxLifecycleStatus;
import java.time.Instant;

/** 返回沙箱生命周期状态和底层 Docker 运行信息。 */
public record SandboxStatusResponse(
        String userId,
        String sessionId,
        SandboxLifecycleStatus status,
        boolean running,
        String containerId,
        String containerName,
        boolean snapshotRestorable,
        String workspaceRoot,
        Instant createdAt,
        Instant updatedAt) {}
