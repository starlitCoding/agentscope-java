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

import io.agentscope.harness.agent.filesystem.sandbox.SandboxBackedFilesystem;
import io.agentscope.harness.agent.sandbox.Sandbox;
import java.time.Instant;

/** 内存中的运行态沙箱，绑定 Harness Sandbox 与文件系统代理。 */
public class SandboxRuntime {

    private final Sandbox sandbox;
    private final SandboxBackedFilesystem filesystem;
    private final Instant startedAt;
    private Instant lastAccessAt;

    /** 创建运行态对象并把文件系统代理绑定到当前沙箱。 */
    public SandboxRuntime(Sandbox sandbox) {
        this.sandbox = sandbox;
        this.filesystem = new SandboxBackedFilesystem();
        this.filesystem.setSandbox(sandbox);
        this.startedAt = Instant.now();
        this.lastAccessAt = this.startedAt;
    }

    /** 返回当前 Harness 沙箱。 */
    public Sandbox sandbox() {
        return sandbox;
    }

    /** 返回绑定当前沙箱的文件系统工具代理。 */
    public SandboxBackedFilesystem filesystem() {
        touch();
        return filesystem;
    }

    /** 返回启动时间。 */
    public Instant startedAt() {
        return startedAt;
    }

    /** 返回最后访问时间。 */
    public Instant lastAccessAt() {
        return lastAccessAt;
    }

    /** 刷新最后访问时间。 */
    public void touch() {
        this.lastAccessAt = Instant.now();
    }
}
