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

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** 表示一个业务沙箱的隔离键，由 userId 和 sessionId 共同决定。 */
public record SandboxKey(String userId, String sessionId) {

    /** 创建并校验业务沙箱键。 */
    public static SandboxKey of(String userId, String sessionId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        return new SandboxKey(userId, sessionId);
    }

    /** 返回可安全用于文件路径的 userId 片段。 */
    public String safeUserSegment() {
        return encode(userId);
    }

    /** 返回可安全用于文件路径的 sessionId 片段。 */
    public String safeSessionSegment() {
        return encode(sessionId);
    }

    /** 使用 URL-safe Base64 编码路径片段，避免路径穿越。 */
    private static String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
