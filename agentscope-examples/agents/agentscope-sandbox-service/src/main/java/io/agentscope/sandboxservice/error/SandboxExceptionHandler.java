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

import io.agentscope.sandboxservice.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 统一把业务异常和参数校验异常转换为标准错误响应。 */
@RestControllerAdvice
public class SandboxExceptionHandler {

    /** 处理业务异常，按错误码返回对应 HTTP 状态。 */
    @ExceptionHandler(SandboxServiceException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(SandboxServiceException e) {
        ErrorResponse body =
                new ErrorResponse(
                        e.code().name(), e.getMessage(), e.details(), e.userId(), e.sessionId());
        return ResponseEntity.status(HttpStatus.valueOf(e.code().httpStatus())).body(body);
    }

    /** 处理请求体校验失败，统一返回 400 INVALID_REQUEST。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String details =
                e.getBindingResult().getFieldErrors().stream()
                        .findFirst()
                        .map(error -> error.getField() + " " + error.getDefaultMessage())
                        .orElse("validation failed");
        ErrorResponse body =
                new ErrorResponse("INVALID_REQUEST", "Invalid request", details, null, null);
        return ResponseEntity.badRequest().body(body);
    }

    /** 处理缺少查询参数，统一返回 400 INVALID_REQUEST。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException e) {
        ErrorResponse body =
                new ErrorResponse(
                        "INVALID_REQUEST",
                        "Invalid request",
                        "missing parameter: " + e.getParameterName(),
                        null,
                        null);
        return ResponseEntity.badRequest().body(body);
    }

    /** 处理非法参数，统一返回 400 INVALID_REQUEST。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        ErrorResponse body = new ErrorResponse("INVALID_REQUEST", e.getMessage(), null, null, null);
        return ResponseEntity.badRequest().body(body);
    }

    /** 处理未知异常，返回 500 通用错误码。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e) {
        ErrorResponse body =
                new ErrorResponse(
                        "SANDBOX_EXEC_FAILED",
                        "Unexpected server error",
                        e.getMessage(),
                        null,
                        null);
        return ResponseEntity.internalServerError().body(body);
    }
}
