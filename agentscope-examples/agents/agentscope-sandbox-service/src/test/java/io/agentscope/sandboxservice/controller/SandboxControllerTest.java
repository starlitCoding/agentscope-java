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
package io.agentscope.sandboxservice.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.agentscope.sandboxservice.dto.SandboxExecResponse;
import io.agentscope.sandboxservice.dto.SandboxStatusResponse;
import io.agentscope.sandboxservice.service.SandboxKey;
import io.agentscope.sandboxservice.service.SandboxLifecycleService;
import io.agentscope.sandboxservice.service.SandboxLifecycleStatus;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 验证生命周周期 API 的请求与响应映射。 */
@WebMvcTest(SandboxController.class)
class SandboxControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean SandboxLifecycleService service;

    /** 验证 start API 使用 userId 和 sessionId 启动沙箱并返回状态。 */
    @Test
    void startReturnsSandboxStatus() throws Exception {
        given(service.start(SandboxKey.of("alice", "conv-1")))
                .willReturn(
                        new SandboxStatusResponse(
                                "alice",
                                "conv-1",
                                SandboxLifecycleStatus.RUNNING,
                                true,
                                "container-1",
                                "sandbox-1",
                                false,
                                "/workspace",
                                Instant.parse("2026-09-02T10:00:00Z"),
                                Instant.parse("2026-09-02T10:01:00Z")));

        mvc.perform(
                        post("/api/sandboxes/start")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"userId\":\"alice\",\"sessionId\":\"conv-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.containerId").value("container-1"));
    }

    /** 验证 status API 查询沙箱状态。 */
    @Test
    void statusReturnsSandboxStatus() throws Exception {
        given(service.status(SandboxKey.of("alice", "conv-1")))
                .willReturn(
                        Optional.of(
                                new SandboxStatusResponse(
                                        "alice",
                                        "conv-1",
                                        SandboxLifecycleStatus.RUNNING,
                                        true,
                                        "container-1",
                                        "sandbox-1",
                                        false,
                                        "/workspace",
                                        Instant.parse("2026-09-02T10:00:00Z"),
                                        Instant.parse("2026-09-02T10:01:00Z"))));

        mvc.perform(
                        get("/api/sandboxes/status")
                                .param("userId", "alice")
                                .param("sessionId", "conv-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true));
    }

    /** 验证 stop API 停止并保存快照。 */
    @Test
    void stopReturnsSandboxStatus() throws Exception {
        given(service.stop(SandboxKey.of("alice", "conv-1")))
                .willReturn(
                        new SandboxStatusResponse(
                                "alice",
                                "conv-1",
                                SandboxLifecycleStatus.STOPPED,
                                false,
                                "container-1",
                                "sandbox-1",
                                true,
                                "/workspace",
                                Instant.parse("2026-09-02T10:00:00Z"),
                                Instant.parse("2026-09-02T10:02:00Z")));

        mvc.perform(
                        post("/api/sandboxes/stop")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"userId\":\"alice\",\"sessionId\":\"conv-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"));
    }

    /** 验证 DELETE API 关闭沙箱。 */
    @Test
    void closeReturnsSandboxStatus() throws Exception {
        given(service.close(SandboxKey.of("alice", "conv-1")))
                .willReturn(
                        new SandboxStatusResponse(
                                "alice",
                                "conv-1",
                                SandboxLifecycleStatus.CLOSED,
                                false,
                                null,
                                null,
                                true,
                                "/workspace",
                                Instant.parse("2026-09-02T10:00:00Z"),
                                Instant.parse("2026-09-02T10:03:00Z")));

        mvc.perform(
                        delete("/api/sandboxes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"userId\":\"alice\",\"sessionId\":\"conv-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    /** 验证 exec API 返回命令 exitCode 和输出，而不是把非业务异常吞掉。 */
    @Test
    void execReturnsCommandOutput() throws Exception {
        given(service.exec(SandboxKey.of("alice", "conv-1"), "pwd", 30))
                .willReturn(new SandboxExecResponse(0, "/workspace\n", "", "/workspace\n", false));

        mvc.perform(
                        post("/api/sandboxes/exec")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"userId\":\"alice\",\"sessionId\":\"conv-1\",\"command\":\"pwd\",\"timeoutSeconds\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitCode").value(0))
                .andExpect(jsonPath("$.stdout").value("/workspace\n"));
    }

    /** 验证缺失 userId 的请求返回 400 校验错误。 */
    @Test
    void rejectsMissingUserId() throws Exception {
        mvc.perform(
                        post("/api/sandboxes/start")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"sessionId\":\"conv-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
