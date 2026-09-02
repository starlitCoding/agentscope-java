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

import jakarta.validation.constraints.NotBlank;

/** 上传文件到沙箱请求，内容使用 Base64 包装。 */
public record FileUploadRequest(
        @NotBlank String userId,
        @NotBlank String sessionId,
        @NotBlank String path,
        @NotBlank String base64Content) {}
