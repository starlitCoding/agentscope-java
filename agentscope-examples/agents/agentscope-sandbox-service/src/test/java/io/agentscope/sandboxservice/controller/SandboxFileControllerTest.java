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
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.agentscope.sandboxservice.dto.FileDownloadResponse;
import io.agentscope.sandboxservice.dto.FileExistsResponse;
import io.agentscope.sandboxservice.dto.FileGlobResponse;
import io.agentscope.sandboxservice.dto.FileGrepResponse;
import io.agentscope.sandboxservice.dto.FileListResponse;
import io.agentscope.sandboxservice.dto.FileReadResponse;
import io.agentscope.sandboxservice.service.SandboxFileToolService;
import io.agentscope.sandboxservice.service.SandboxKey;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 验证文件工具 API 的路由和参数映射。 */
@WebMvcTest(SandboxFileController.class)
class SandboxFileControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean SandboxFileToolService fileService;

    /** 验证 read API 把查询参数传给文件工具服务。 */
    @Test
    void readFileReturnsContent() throws Exception {
        given(fileService.read(SandboxKey.of("alice", "conv-1"), "/workspace/a.txt", 0, 20))
                .willReturn(new FileReadResponse("/workspace/a.txt", "hello", "utf-8"));

        mvc.perform(
                        get("/api/sandboxes/files/read")
                                .param("userId", "alice")
                                .param("sessionId", "conv-1")
                                .param("path", "/workspace/a.txt")
                                .param("offset", "0")
                                .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("hello"));
    }

    /** 验证 write API 传递请求体内容。 */
    @Test
    void writeFileDelegatesToService() throws Exception {
        mvc.perform(
                        put("/api/sandboxes/files/write")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"userId\":\"alice\",\"sessionId\":\"conv-1\",\"path\":\"/workspace/a.txt\",\"content\":\"hello\"}"))
                .andExpect(status().isOk());

        verify(fileService).write(SandboxKey.of("alice", "conv-1"), "/workspace/a.txt", "hello");
    }

    /** 验证 edit API 传递替换参数。 */
    @Test
    void editFileDelegatesToService() throws Exception {
        mvc.perform(
                        patch("/api/sandboxes/files/edit")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"userId\":\"alice\",\"sessionId\":\"conv-1\",\"path\":\"/workspace/a.txt\",\"oldString\":\"hello\",\"newString\":\"hi\",\"replaceAll\":false}"))
                .andExpect(status().isOk());

        verify(fileService)
                .edit(SandboxKey.of("alice", "conv-1"), "/workspace/a.txt", "hello", "hi", false);
    }

    /** 验证 list API 列目录。 */
    @Test
    void listFilesReturnsEntries() throws Exception {
        given(fileService.list(SandboxKey.of("alice", "conv-1"), "/workspace"))
                .willReturn(new FileListResponse(List.of()));

        mvc.perform(
                        get("/api/sandboxes/files/list")
                                .param("userId", "alice")
                                .param("sessionId", "conv-1")
                                .param("path", "/workspace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isArray());
    }

    /** 验证 exists API 返回存在性。 */
    @Test
    void existsFileReturnsFlag() throws Exception {
        given(fileService.exists(SandboxKey.of("alice", "conv-1"), "/workspace/a.txt"))
                .willReturn(new FileExistsResponse("/workspace/a.txt", true));

        mvc.perform(
                        get("/api/sandboxes/files/exists")
                                .param("userId", "alice")
                                .param("sessionId", "conv-1")
                                .param("path", "/workspace/a.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true));
    }

    /** 验证 glob API 返回匹配文件。 */
    @Test
    void globFilesReturnsMatches() throws Exception {
        given(fileService.glob(SandboxKey.of("alice", "conv-1"), "/workspace", "*.java"))
                .willReturn(new FileGlobResponse(List.of()));

        mvc.perform(
                        get("/api/sandboxes/files/glob")
                                .param("userId", "alice")
                                .param("sessionId", "conv-1")
                                .param("path", "/workspace")
                                .param("pattern", "*.java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isArray());
    }

    /** 验证 grep API 返回文本匹配。 */
    @Test
    void grepFilesReturnsMatches() throws Exception {
        given(fileService.grep(SandboxKey.of("alice", "conv-1"), "/workspace", "hello", "*.txt"))
                .willReturn(new FileGrepResponse(List.of()));

        mvc.perform(
                        get("/api/sandboxes/files/grep")
                                .param("userId", "alice")
                                .param("sessionId", "conv-1")
                                .param("path", "/workspace")
                                .param("pattern", "hello")
                                .param("glob", "*.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").isArray());
    }

    /** 验证 delete API 删除路径。 */
    @Test
    void deleteFileDelegatesToService() throws Exception {
        mvc.perform(
                        delete("/api/sandboxes/files")
                                .param("userId", "alice")
                                .param("sessionId", "conv-1")
                                .param("path", "/workspace/a.txt"))
                .andExpect(status().isOk());

        verify(fileService).delete(SandboxKey.of("alice", "conv-1"), "/workspace/a.txt");
    }

    /** 验证 move API 移动路径。 */
    @Test
    void moveFileDelegatesToService() throws Exception {
        mvc.perform(
                        post("/api/sandboxes/files/move")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"userId\":\"alice\",\"sessionId\":\"conv-1\",\"fromPath\":\"/workspace/a.txt\",\"toPath\":\"/workspace/b.txt\"}"))
                .andExpect(status().isOk());

        verify(fileService)
                .move(SandboxKey.of("alice", "conv-1"), "/workspace/a.txt", "/workspace/b.txt");
    }

    /** 验证 upload API 上传 Base64 内容。 */
    @Test
    void uploadFileDelegatesToService() throws Exception {
        mvc.perform(
                        post("/api/sandboxes/files/upload")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"userId\":\"alice\",\"sessionId\":\"conv-1\",\"path\":\"/workspace/a.bin\",\"base64Content\":\"aGVsbG8=\"}"))
                .andExpect(status().isOk());

        verify(fileService)
                .upload(SandboxKey.of("alice", "conv-1"), "/workspace/a.bin", "aGVsbG8=");
    }

    /** 验证 download API 返回 Base64 内容。 */
    @Test
    void downloadFileReturnsBase64() throws Exception {
        given(fileService.download(SandboxKey.of("alice", "conv-1"), "/workspace/a.bin"))
                .willReturn(new FileDownloadResponse("/workspace/a.bin", "aGVsbG8="));

        mvc.perform(
                        get("/api/sandboxes/files/download")
                                .param("userId", "alice")
                                .param("sessionId", "conv-1")
                                .param("path", "/workspace/a.bin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.base64Content").value("aGVsbG8="));
    }
}
