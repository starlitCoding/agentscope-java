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
package io.agentscope.sandboxservice.error;

import io.agentscope.sandboxservice.service.SandboxKey;

/** 沙箱服务业务异常，携带错误码、HTTP 状态和业务键，供统一异常处理返回。 */
public class SandboxServiceException extends RuntimeException {

    /** 服务错误码，与规格中的错误码表一一对应。 */
    public enum Code {
        INVALID_REQUEST(400),
        SANDBOX_NOT_FOUND(404),
        SANDBOX_START_FAILED(500),
        SANDBOX_EXEC_FAILED(500),
        SANDBOX_EXEC_TIMEOUT(504),
        FILE_OPERATION_FAILED(500),
        STATE_STORE_FAILED(500);

        private final int httpStatus;

        Code(int httpStatus) {
            this.httpStatus = httpStatus;
        }

        /** 返回该错误码对应的默认 HTTP 状态码。 */
        public int httpStatus() {
            return httpStatus;
        }
    }

    private final Code code;
    private final String details;
    private final String userId;
    private final String sessionId;

    /** 创建带完整错误上下文的业务异常。 */
    public SandboxServiceException(
            Code code, String message, String details, String userId, String sessionId) {
        super(message);
        this.code = code;
        this.details = details;
        this.userId = userId;
        this.sessionId = sessionId;
    }

    /** 返回服务错误码。 */
    public Code code() {
        return code;
    }

    /** 返回补充错误细节。 */
    public String details() {
        return details;
    }

    /** 返回相关业务用户 ID。 */
    public String userId() {
        return userId;
    }

    /** 返回相关业务会话 ID。 */
    public String sessionId() {
        return sessionId;
    }

    /** 构造查询不存在沙箱时抛出的异常。 */
    public static SandboxServiceException notFound(SandboxKey key) {
        return new SandboxServiceException(
                Code.SANDBOX_NOT_FOUND, "Sandbox not found", null, key.userId(), key.sessionId());
    }

    /** 构造沙箱启动失败时抛出的异常，携带底层原因。 */
    public static SandboxServiceException startFailed(SandboxKey key, Throwable cause) {
        return new SandboxServiceException(
                Code.SANDBOX_START_FAILED,
                "Failed to start sandbox",
                cause == null ? null : cause.getMessage(),
                key.userId(),
                key.sessionId());
    }

    /** 构造命令执行异常时抛出的异常，携带底层原因。 */
    public static SandboxServiceException execFailed(SandboxKey key, Throwable cause) {
        return new SandboxServiceException(
                Code.SANDBOX_EXEC_FAILED,
                "Failed to execute command",
                cause == null ? null : cause.getMessage(),
                key.userId(),
                key.sessionId());
    }

    /** 构造文件工具失败时抛出的异常，携带 Harness 返回的失败原因。 */
    public static SandboxServiceException fileOperation(SandboxKey key, String error) {
        return new SandboxServiceException(
                Code.FILE_OPERATION_FAILED,
                "File operation failed",
                error,
                key.userId(),
                key.sessionId());
    }

    /** 构造参数校验失败时抛出的异常。 */
    public static SandboxServiceException invalid(SandboxKey key, String message) {
        return new SandboxServiceException(
                Code.INVALID_REQUEST, message, null, key.userId(), key.sessionId());
    }
}
