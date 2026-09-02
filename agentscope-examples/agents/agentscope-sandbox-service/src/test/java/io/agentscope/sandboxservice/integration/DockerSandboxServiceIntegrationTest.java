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
package io.agentscope.sandboxservice.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.sandboxservice.dto.FileReadResponse;
import io.agentscope.sandboxservice.dto.SandboxExecResponse;
import io.agentscope.sandboxservice.service.SandboxFileToolService;
import io.agentscope.sandboxservice.service.SandboxKey;
import io.agentscope.sandboxservice.service.SandboxLifecycleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 验证真实 Docker 沙箱的创建、快照保存和恢复能力，默认关闭，需显式开启。 */
@Tag("docker")
@SpringBootTest(
        properties = {
            "sandbox-service.state-dir=${java.io.tmpdir}/agentscope-sandbox-service-it/state",
            "sandbox-service.snapshot-dir=${java.io.tmpdir}/agentscope-sandbox-service-it/snapshots"
        })
@EnabledIfSystemProperty(named = "sandbox.integration.docker", matches = "true")
class DockerSandboxServiceIntegrationTest {

    @Autowired SandboxLifecycleService lifecycleService;
    @Autowired SandboxFileToolService fileToolService;

    /** 验证真实 Docker 沙箱能执行命令、保存快照、关闭后再次恢复文件。 */
    @Test
    void restoresWorkspaceFromSnapshotAfterCloseAndRestart() {
        SandboxKey key = SandboxKey.of("it-user", "it-session");
        lifecycleService.start(key);
        SandboxExecResponse exec =
                lifecycleService.exec(key, "echo restored > /workspace/restored.txt", 30);
        assertThat(exec.exitCode()).isZero();

        lifecycleService.stop(key);
        lifecycleService.close(key);
        lifecycleService.start(key);

        FileReadResponse read = fileToolService.read(key, "/workspace/restored.txt", 0, 10);
        assertThat(read.content()).contains("restored");

        lifecycleService.close(key);
    }
}
