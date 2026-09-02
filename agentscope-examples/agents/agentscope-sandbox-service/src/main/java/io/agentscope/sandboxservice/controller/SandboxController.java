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

import io.agentscope.sandboxservice.dto.SandboxCreateRequest;
import io.agentscope.sandboxservice.dto.SandboxExecRequest;
import io.agentscope.sandboxservice.dto.SandboxExecResponse;
import io.agentscope.sandboxservice.dto.SandboxKeyRequest;
import io.agentscope.sandboxservice.dto.SandboxStatusResponse;
import io.agentscope.sandboxservice.error.SandboxServiceException;
import io.agentscope.sandboxservice.service.SandboxKey;
import io.agentscope.sandboxservice.service.SandboxLifecycleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 沙箱生命周期与命令执行 REST API。 */
@RestController
@RequestMapping("/api/sandboxes")
public class SandboxController {

    private final SandboxLifecycleService lifecycleService;

    /** 创建控制器，依赖生命周期服务。 */
    public SandboxController(SandboxLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    /** 创建或恢复沙箱，作为 start 的别名语义。 */
    @PostMapping
    public SandboxStatusResponse create(@Valid @RequestBody SandboxCreateRequest request) {
        return lifecycleService.create(SandboxKey.of(request.userId(), request.sessionId()));
    }

    /** 启动沙箱，无状态时新建，有状态时恢复。 */
    @PostMapping("/start")
    public SandboxStatusResponse start(@Valid @RequestBody SandboxKeyRequest request) {
        return lifecycleService.start(SandboxKey.of(request.userId(), request.sessionId()));
    }

    /** 停止并保存快照，不释放容器。 */
    @PostMapping("/stop")
    public SandboxStatusResponse stop(@Valid @RequestBody SandboxKeyRequest request) {
        return lifecycleService.stop(SandboxKey.of(request.userId(), request.sessionId()));
    }

    /** 关闭并释放容器，保留状态和快照。 */
    @DeleteMapping
    public SandboxStatusResponse close(@Valid @RequestBody SandboxKeyRequest request) {
        return lifecycleService.close(SandboxKey.of(request.userId(), request.sessionId()));
    }

    /** 查询沙箱状态，无状态时返回 404。 */
    @GetMapping("/status")
    public ResponseEntity<SandboxStatusResponse> status(
            @RequestParam String userId, @RequestParam String sessionId) {
        SandboxKey key = SandboxKey.of(userId, sessionId);
        return lifecycleService
                .status(key)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> SandboxServiceException.notFound(key));
    }

    /** 在沙箱内执行命令并返回输出。 */
    @PostMapping("/exec")
    public SandboxExecResponse exec(@Valid @RequestBody SandboxExecRequest request) {
        return lifecycleService.exec(
                SandboxKey.of(request.userId(), request.sessionId()),
                request.command(),
                request.timeoutSeconds());
    }
}
