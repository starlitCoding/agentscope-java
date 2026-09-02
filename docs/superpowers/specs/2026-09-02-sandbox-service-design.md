# 独立 Spring Boot Docker 沙箱服务设计

## 背景

当前仓库的 Docker 沙箱能力位于 `agentscope-harness` 模块，核心类包括 `DockerSandboxClient`、`Sandbox`、`WorkspaceSpec`、`LocalSnapshotSpec` 和 `SandboxBackedFilesystem`。这些能力可以不经过 `ReactAgent` / `HarnessAgent` 直接使用。

本项目要新增一个独立 Spring Boot 服务，作为“沙箱能力网关”。调用方只需要通过 HTTP API 传入 `userId` 和 `sessionId`，就能创建、启动、停止、关闭沙箱，保存和加载快照，并通过接口触发沙箱内命令执行和文件工具操作。

## 目标

1. 新增一个可独立运行的 Spring Boot 项目，建议模块路径为 `agentscope-examples/agents/agentscope-sandbox-service`。
2. 服务只复用 AgentScope Harness 的沙箱与文件系统能力，不引入模型、不创建 `ReactAgent`，也不构建 `HarnessAgent`。
3. 以 `(userId, sessionId)` 作为沙箱隔离键，同一个键可以恢复同一份沙箱状态和工作区快照。
4. 支持本地 Docker 沙箱：
   - 创建容器。
   - 启动或恢复容器。
   - 停止并保存快照。
   - 关闭并删除容器。
   - 从持久化状态恢复工作区。
5. 暴露 HTTP API：
   - 生命周期 API。
   - 命令执行 API。
   - 文件工具 API，包括读文件、写文件、编辑文件、列目录、判断存在、搜索、匹配、删除、移动、上传、下载。
6. 使用本地文件保存沙箱状态 JSON 和 tar 快照，第一版不引入数据库。
7. 提供单元测试和 Web API 测试；Docker 真容器测试作为可选集成测试，不阻塞普通构建。

## 非目标

1. 不实现 Agent 对话、模型调用、工具自动选择或 ReAct 循环。
2. 不做多副本分布式一致性，第一版按单机服务设计。
3. 不提供用户登录、鉴权、租户管理或权限审批。
4. 不支持 Kubernetes、E2B、Daytona、AgentRun 等远端沙箱后端。
5. 不实现浏览器 UI。
6. 不把沙箱内变更反向同步到宿主机项目目录。

## 项目结构

建议新增 Maven 子模块：

```text
agentscope-examples/agents/agentscope-sandbox-service/
  pom.xml
  src/main/java/io/agentscope/sandboxservice/
    SandboxServiceApplication.java
    config/
      SandboxServiceProperties.java
      SandboxServiceConfig.java
    controller/
      SandboxController.java
      SandboxFileController.java
    dto/
      SandboxKeyRequest.java
      SandboxCreateRequest.java
      SandboxStatusResponse.java
      SandboxExecRequest.java
      SandboxExecResponse.java
      FileReadResponse.java
      FileWriteRequest.java
      FileEditRequest.java
      FileMoveRequest.java
      FileUploadRequest.java
      ErrorResponse.java
    service/
      SandboxLifecycleService.java
      SandboxFileToolService.java
      SandboxStateRepository.java
      FileSandboxStateRepository.java
      SandboxKey.java
      SandboxRuntime.java
      SandboxOperationLockRegistry.java
    error/
      SandboxServiceException.java
      SandboxExceptionHandler.java
  src/main/resources/
    application.yml
  src/test/java/io/agentscope/sandboxservice/
    controller/
    service/
    repository/
```

并在 `agentscope-examples/pom.xml` 中加入：

```xml
<module>agents/agentscope-sandbox-service</module>
```

## 依赖

模块依赖保持轻量：

- `io.agentscope:agentscope-harness`
- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.boot:spring-boot-starter-actuator`
- `org.springframework.boot:spring-boot-starter-test`，测试范围

选择 `spring-boot-starter-web` 而不是 WebFlux，原因是沙箱操作主要是阻塞式 Docker CLI 调用，Servlet MVC 与当前执行模型更匹配。

## 配置

`application.yml` 默认配置：

```yaml
server:
  port: 8080

sandbox-service:
  state-dir: ${user.home}/.agentscope-sandbox-service/state
  snapshot-dir: ${user.home}/.agentscope-sandbox-service/snapshots
  docker:
    image: ubuntu:24.04
    workspace-root: /workspace
    network: none
    memory-size-bytes: 1073741824
    cpu-count: 2
  exec:
    default-timeout-seconds: 120
```

配置类使用 `@ConfigurationProperties(prefix = "sandbox-service")`。所有路径在启动后转换为绝对路径并创建目录。

## 状态模型

业务侧不直接暴露 `SandboxState` 对象，而是保存 Harness 序列化后的 JSON。

状态文件路径：

```text
{state-dir}/{safeUserId}/{safeSessionId}.json
```

快照文件由 `LocalSnapshotSpec` 管理：

```text
{snapshot-dir}/{snapshotId}.tar
```

`safeUserId` 和 `safeSessionId` 需要做路径安全编码，避免调用方传入 `/`、`..`、空字符等危险路径。第一版可以使用 URL-safe Base64 编码原始 ID，文件内容中保留原始 `userId` / `sessionId`。

状态文件建议包含服务侧包装信息：

```json
{
  "userId": "alice",
  "sessionId": "conv-1",
  "status": "STOPPED",
  "sandboxStateJson": "{\"@type\":\"docker\",\"sessionId\":\"generated-sandbox-session\"}",
  "createdAt": "2026-09-02T10:00:00Z",
  "updatedAt": "2026-09-02T10:10:00Z"
}
```

`sandboxStateJson` 由 `DockerSandboxClient#serializeState(...)` 生成。恢复时使用 `DockerSandboxClient#deserializeState(...)` 和 `DockerSandboxClient#resume(...)`。

## 生命周期语义

服务层对外提供四个主要动作：

| 动作 | 内部行为 | 结果 |
|------|----------|------|
| `create` | 如果没有状态，则 `client.create(...)`；如果已有状态，则按 `start` 处理 | 返回可用沙箱 |
| `start` | 加载状态；有状态则 `resume(...)`，无状态则 `create(...)`；随后 `sandbox.start()` | 沙箱变为运行态 |
| `stop` | 对运行态沙箱调用 `sandbox.stop()`；保存状态 JSON | 快照保存，容器默认保留 |
| `close` | 先尽量 `sandbox.stop()` 保存快照，再 `sandbox.shutdown()` 删除容器；保存状态 | 快照保留，容器释放 |

设计取舍：

- `stop` 不删除容器，适合长会话复用。
- `close` 删除容器但保留快照和状态，下次 `start` 可重新创建容器并恢复 `/workspace`。
- 如果服务进程重启，内存里的 `Sandbox` 实例丢失，但状态 JSON 与快照仍在；下次 `start` 走 `resume(...)`。
- 如果状态 JSON 指向的容器不存在，Docker 后端会自动创建新容器并从快照恢复。

## 并发控制

同一个 `(userId, sessionId)` 只能同时执行一个生命周期或文件操作。

实现方式：

- `SandboxOperationLockRegistry` 维护 `ConcurrentHashMap<SandboxKey, ReentrantLock>`。
- Controller 进入 service 后先获取对应 key 的锁。
- 操作完成后释放锁。

这样可以避免并发 `exec`、`write`、`stop` 同时修改同一工作区和同一状态文件。不同用户或不同 session 可并发执行。

## 运行态缓存

`SandboxLifecycleService` 在内存中维护运行态缓存：

```text
Map<SandboxKey, SandboxRuntime>
```

`SandboxRuntime` 包含：

- `Sandbox sandbox`
- `SandboxBackedFilesystem filesystem`
- `Instant startedAt`
- `Instant lastAccessAt`

每次 `start`、`exec` 或文件工具调用都会刷新 `lastAccessAt`。

第一版不做自动闲置清理，避免后台线程影响测试稳定性。后续可以增加 `idle-ttl` 和定时清理。

## WorkspaceSpec

第一版使用最小工作区：

- root：配置中的 `workspace-root`，默认 `/workspace`
- entries：空
- environment：空

后续可以扩展为 API 支持初始化文件、环境变量、只读挂载或模板目录。当前 spec 不做这些扩展，避免接口过早复杂化。

## HTTP API

### 创建或启动沙箱

```http
POST /api/sandboxes/start
Content-Type: application/json

{
  "userId": "alice",
  "sessionId": "conv-1"
}
```

响应：

```json
{
  "userId": "alice",
  "sessionId": "conv-1",
  "status": "RUNNING",
  "running": true,
  "containerId": "abc123",
  "containerName": "agentscope-sandbox-generated-sandbox-session",
  "snapshotRestorable": true,
  "workspaceRoot": "/workspace"
}
```

`POST /api/sandboxes` 可作为 `start` 的别名，方便调用方理解“创建沙箱”。

### 停止并保存快照

```http
POST /api/sandboxes/stop
Content-Type: application/json

{
  "userId": "alice",
  "sessionId": "conv-1"
}
```

行为：

- 如果沙箱正在运行，调用 `sandbox.stop()`。
- 保存最新 `SandboxState` JSON。
- 如果沙箱未在内存中但已有状态，返回当前状态，不强行启动。

### 关闭并删除容器

```http
DELETE /api/sandboxes
Content-Type: application/json

{
  "userId": "alice",
  "sessionId": "conv-1"
}
```

行为：

- 如果沙箱正在运行，先调用 `stop()`，再调用 `shutdown()`。
- 如果沙箱不在内存中，则加载状态并尝试 `resume(...)` 后 `shutdown()`。
- 保留状态 JSON 和快照 tar，用于以后恢复。

### 查询状态

```http
GET /api/sandboxes/status?userId=alice&sessionId=conv-1
```

响应包含：

- 是否有状态文件。
- 当前内存中是否运行。
- Docker 容器 ID 和容器名。
- workspaceRoot。
- 快照是否可恢复。
- 创建和更新时间。

### 执行命令

```http
POST /api/sandboxes/exec
Content-Type: application/json

{
  "userId": "alice",
  "sessionId": "conv-1",
  "command": "pwd && ls -la",
  "timeoutSeconds": 30
}
```

响应：

```json
{
  "exitCode": 0,
  "stdout": "README.md\n",
  "stderr": "",
  "output": "README.md\n",
  "truncated": false
}
```

命令执行前，如果沙箱未运行，服务会自动 `start`。命令执行后不自动 `stop`，调用方可以显式调用 `stop` 保存快照。

### 文件工具 API

所有文件 API 都以 `userId` 和 `sessionId` 定位沙箱；如果沙箱未运行，自动启动或恢复。

读文件：

```http
GET /api/sandboxes/files/read?userId=alice&sessionId=conv-1&path=/workspace/README.md&offset=0&limit=200
```

写文件：

```http
PUT /api/sandboxes/files/write
Content-Type: application/json

{
  "userId": "alice",
  "sessionId": "conv-1",
  "path": "/workspace/hello.txt",
  "content": "hello\n"
}
```

编辑文件：

```http
PATCH /api/sandboxes/files/edit
Content-Type: application/json

{
  "userId": "alice",
  "sessionId": "conv-1",
  "path": "/workspace/hello.txt",
  "oldString": "hello",
  "newString": "hi",
  "replaceAll": false
}
```

列目录：

```http
GET /api/sandboxes/files/list?userId=alice&sessionId=conv-1&path=/workspace
```

判断存在：

```http
GET /api/sandboxes/files/exists?userId=alice&sessionId=conv-1&path=/workspace/hello.txt
```

glob：

```http
GET /api/sandboxes/files/glob?userId=alice&sessionId=conv-1&path=/workspace&pattern=*.java
```

grep：

```http
GET /api/sandboxes/files/grep?userId=alice&sessionId=conv-1&path=/workspace&pattern=hello&glob=*.txt
```

删除：

```http
DELETE /api/sandboxes/files?userId=alice&sessionId=conv-1&path=/workspace/hello.txt
```

移动：

```http
POST /api/sandboxes/files/move
Content-Type: application/json

{
  "userId": "alice",
  "sessionId": "conv-1",
  "fromPath": "/workspace/a.txt",
  "toPath": "/workspace/b.txt"
}
```

上传：

```http
POST /api/sandboxes/files/upload
Content-Type: application/json

{
  "userId": "alice",
  "sessionId": "conv-1",
  "path": "/workspace/data.bin",
  "base64Content": "aGVsbG8K"
}
```

下载：

```http
GET /api/sandboxes/files/download?userId=alice&sessionId=conv-1&path=/workspace/data.bin
```

下载响应使用 JSON 包装：

```json
{
  "path": "/workspace/data.bin",
  "base64Content": "aGVsbG8K"
}
```

## 文件工具实现

文件工具不重新实现 shell 细节，直接复用 `SandboxBackedFilesystem`：

- `read(...)`
- `write(...)`
- `edit(...)`
- `ls(...)`
- `exists(...)`
- `glob(...)`
- `grep(...)`
- `delete(...)`
- `move(...)`
- `uploadFiles(...)`
- `downloadFiles(...)`

每个运行态沙箱创建一个对应的 `SandboxBackedFilesystem`，并通过 `fs.setSandbox(sandbox)` 注入当前沙箱。

## 错误处理

统一错误响应：

```json
{
  "code": "SANDBOX_START_FAILED",
  "message": "Failed to start sandbox",
  "details": "docker run failed because the Docker daemon is not reachable",
  "userId": "alice",
  "sessionId": "conv-1"
}
```

建议错误码：

| 错误码 | HTTP 状态 | 说明 |
|--------|-----------|------|
| `INVALID_REQUEST` | 400 | 缺少 userId、sessionId、path、command 等 |
| `SANDBOX_NOT_FOUND` | 404 | 查询状态时没有对应状态 |
| `SANDBOX_START_FAILED` | 500 | Docker 容器启动失败 |
| `SANDBOX_EXEC_FAILED` | 500 | 命令执行异常 |
| `SANDBOX_EXEC_TIMEOUT` | 504 | 命令超时 |
| `FILE_OPERATION_FAILED` | 500 | 文件工具返回失败 |
| `STATE_STORE_FAILED` | 500 | 状态文件读写失败 |

命令本身 exit code 非 0 不一定映射为 HTTP 500。`/exec` 应返回 200，并在响应体里保留 `exitCode`、`stdout`、`stderr`，这样调用方能区分“命令正常失败”和“服务异常”。

## 安全约束

第一版默认配置应偏保守：

- Docker network 默认 `none`。
- 默认设置 CPU 和内存限制。
- 不支持通过 API 传入任意 `additionalRunArgs`。
- 不支持通过 API 挂载宿主机目录。
- 不把 Docker socket 挂进容器。
- 文件路径必须是绝对路径，且建议限制在配置的 `workspaceRoot` 下。
- `userId` 和 `sessionId` 仅作为业务键，不能直接拼接为文件系统路径。
- 接口暂不内置鉴权，但文档和 README 需要明确：生产环境必须放在受控内网或前置鉴权网关之后。

## 测试策略

普通测试不依赖 Docker。

单元测试：

- `FileSandboxStateRepositoryTest`
  - 能保存和加载状态。
  - userId / sessionId 被安全编码。
  - 状态文件不存在时返回空。
- `SandboxLifecycleServiceTest`
  - 无状态时创建新沙箱。
  - 有状态时恢复沙箱。
  - `stop` 会保存状态。
  - `close` 会调用 shutdown。
  - 同一 key 操作串行。
- `SandboxFileToolServiceTest`
  - 文件工具请求会确保沙箱已启动。
  - 文件工具失败时转换为统一异常。

Web API 测试：

- `SandboxControllerTest`
  - start / stop / close / status / exec 的请求与响应结构正确。
- `SandboxFileControllerTest`
  - read / write / edit / list / exists / glob / grep / delete / move / upload / download 的路由和参数校验正确。

可选集成测试：

- 使用 JUnit tag `docker` 或系统属性 `sandbox.integration.docker=true` 开启。
- 真实拉起 `ubuntu:24.04` 容器。
- 验证 `exec` 创建文件、`stop` 保存快照、`shutdown` 删除容器、再次 `start` 从快照恢复文件。

## 验收标准

1. 可以通过 Maven 构建新增模块。
2. 启动 Spring Boot 服务后，调用 `POST /api/sandboxes/start` 能创建或恢复 Docker 沙箱。
3. 调用 `/api/sandboxes/exec` 能在沙箱 `/workspace` 中执行命令并返回输出。
4. 调用文件 API 能完成读、写、编辑、列目录、exists、glob、grep、删除、移动、上传、下载。
5. 调用 `stop` 后，状态 JSON 和本地快照 tar 被保存。
6. 调用 `close` 后，容器被释放；再次 `start` 能从状态和快照恢复工作区。
7. 不需要配置模型，不需要启动或调用 `ReactAgent` / `HarnessAgent`。
8. 普通测试不依赖 Docker，默认构建稳定。

## 后续扩展

后续可以按需要增加：

- 数据库存储 `SandboxState`。
- Redis / OSS 快照，支持多副本部署。
- 自动闲置清理。
- API 鉴权和审计日志。
- Workspace 初始化模板。
- 按租户配置镜像、资源限制和网络策略。
- OpenAPI 文档导出。
