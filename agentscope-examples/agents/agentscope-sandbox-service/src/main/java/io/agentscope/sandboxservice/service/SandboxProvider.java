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

import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;

/** 隔离具体沙箱后端差异，为生命周期服务提供统一创建、恢复和状态序列化能力。 */
public interface SandboxProvider {

    /** 返回当前 provider 对应的沙箱后端类型。 */
    SandboxBackendType backend();

    /** 根据业务 key 和快照策略创建新的沙箱实例。 */
    Sandbox create(SandboxKey key, SandboxSnapshotSpec snapshotSpec);

    /** 从持久化状态 JSON 和当前快照策略恢复沙箱实例。 */
    Sandbox resume(String sandboxStateJson, SandboxSnapshotSpec snapshotSpec);

    /** 把沙箱当前状态序列化为 JSON。 */
    String serializeState(Sandbox sandbox);

    /** 返回该后端默认工作区根目录。 */
    String workspaceRoot();

    /** 提取对外展示的后端运行时信息。 */
    SandboxRuntimeDescriptor describe(Sandbox sandbox);
}
