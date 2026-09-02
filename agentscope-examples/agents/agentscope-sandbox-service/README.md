# AgentScope Sandbox Service

独立 Spring Boot Docker 沙箱服务。调用方只需通过 HTTP API 传入 `userId` 和 `sessionId`，即可创建、启动、停止、关闭 Docker 沙箱，保存和加载快照，并触发沙箱内命令执行和文件工具操作。

本服务**不**使用 `ReactAgent` / `HarnessAgent`，也不引入模型，只复用 AgentScope Harness 的 Docker 沙箱与文件系统能力。

## 功能

- 以 `(userId, sessionId)` 作为沙箱隔离键，同一个键可恢复同一份沙箱状态和工作区快照。
- 支持本地 Docker 沙箱生命周期：创建、启动/恢复、停止并保存快照、关闭并删除容器。
- 命令执行 API：在沙箱 `/workspace` 中执行任意 shell 命令。
- 文件工具 API：读、写、编辑、列目录、exists、glob、grep、删除、移动、上传、下载。
- 使用本地文件保存状态 JSON 与 tar 快照，第一版不引入数据库。

## 启动

需要本机可访问 Docker（`docker` CLI 在 `PATH` 中），并预先拉取默认镜像：

```bash
docker pull ubuntu:24.04
```

启动服务：

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am spring-boot:run
```

服务默认监听 `8080` 端口。

## 配置

所有配置前缀为 `sandbox-service`，可通过命令行参数或环境变量覆盖：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `sandbox-service.state-dir` | `~/.agentscope-sandbox-service/state` | 本地状态 JSON 根目录 |
| `sandbox-service.snapshot-dir` | `~/.agentscope-sandbox-service/snapshots` | 本地快照 tar 根目录 |
| `sandbox-service.docker.image` | `ubuntu:24.04` | Docker 镜像 |
| `sandbox-service.docker.workspace-root` | `/workspace` | 容器内工作区根目录 |
| `sandbox-service.docker.network` | `none` | Docker 网络模式（默认断网） |
| `sandbox-service.docker.memory-size-bytes` | `1073741824` | 容器内存限制（字节） |
| `sandbox-service.docker.cpu-count` | `2` | 容器 CPU 限制 |
| `sandbox-service.exec.default-timeout-seconds` | `120` | 默认命令超时（秒） |
| `server.port` | `8080` | 服务端口 |

示例：临时指定状态目录和镜像：

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am spring-boot:run \
  -Dspring-boot.run.arguments="--sandbox-service.state-dir=/tmp/sb-state --sandbox-service.docker.image=ubuntu:22.04"
```

## API 示例

### 创建或启动沙箱

```bash
curl -X POST http://localhost:8080/api/sandboxes/start \
  -H "Content-Type: application/json" \
  -d '{"userId":"alice","sessionId":"conv-1"}'
```

`POST /api/sandboxes` 是 `start` 的别名。响应中 `status` 为 `RUNNING`，并包含 `containerId`、`containerName`、`workspaceRoot` 等运行信息。

### 执行命令

```bash
curl -X POST http://localhost:8080/api/sandboxes/exec \
  -H "Content-Type: application/json" \
  -d '{"userId":"alice","sessionId":"conv-1","command":"pwd && ls -la","timeoutSeconds":30}'
```

命令执行前如果沙箱未运行会自动启动；命令退出码非 0 仍返回 HTTP 200，并在响应体里保留 `exitCode`、`stdout`、`stderr`。

### 写文件

```bash
curl -X PUT http://localhost:8080/api/sandboxes/files/write \
  -H "Content-Type: application/json" \
  -d '{"userId":"alice","sessionId":"conv-1","path":"/workspace/hello.txt","content":"hello\n"}'
```

### 读文件

```bash
curl "http://localhost:8080/api/sandboxes/files/read?userId=alice&sessionId=conv-1&path=/workspace/hello.txt&offset=0&limit=200"
```

其他文件 API：

- 编辑：`PATCH /api/sandboxes/files/edit`
- 列目录：`GET /api/sandboxes/files/list`
- 判断存在：`GET /api/sandboxes/files/exists`
- glob：`GET /api/sandboxes/files/glob`
- grep：`GET /api/sandboxes/files/grep`
- 删除：`DELETE /api/sandboxes/files`
- 移动：`POST /api/sandboxes/files/move`
- 上传：`POST /api/sandboxes/files/upload`（`base64Content` 字段）
- 下载：`GET /api/sandboxes/files/download`（响应为 JSON 包装的 `base64Content`）

### 停止并保存快照

```bash
curl -X POST http://localhost:8080/api/sandboxes/stop \
  -H "Content-Type: application/json" \
  -d '{"userId":"alice","sessionId":"conv-1"}'
```

`stop` 保存快照但保留容器，适合长会话复用。

### 关闭并删除容器

```bash
curl -X DELETE http://localhost:8080/api/sandboxes \
  -H "Content-Type: application/json" \
  -d '{"userId":"alice","sessionId":"conv-1"}'
```

`close` 先保存快照再释放容器；状态 JSON 与快照 tar 保留，下次 `start` 自动从快照恢复工作区。

### 查询状态

```bash
curl "http://localhost:8080/api/sandboxes/status?userId=alice&sessionId=conv-1"
```

## 安全说明

- 默认 Docker 网络为 `none`，容器默认断网。
- 默认设置 CPU 与内存限制。
- API 不支持传入任意 `additionalRunArgs`，也不支持挂载宿主机目录，Docker socket 不会挂进容器。
- 文件路径必须是绝对路径，且限制在配置的 `workspaceRoot` 之下。
- `userId` / `sessionId` 仅作为业务键，状态文件路径使用 URL-safe Base64 编码，避免路径穿越。
- 第一版接口未内置鉴权，**生产环境必须放在受控内网或前置鉴权网关之后**。

## 测试

普通单元测试和 Web API 测试不依赖 Docker：

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am test
```

Docker 真容器集成测试默认关闭，可通过系统属性显式开启（需要本机 Docker 且可拉取 `ubuntu:24.04`）：

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am \
  -Dsandbox.integration.docker=true \
  -Dtest=DockerSandboxServiceIntegrationTest test
```

## 状态存储

- 状态文件：`{state-dir}/{safeUserId}/{safeSessionId}.json`，保存服务侧包装信息（状态、时间戳）与 Harness 序列化后的 `sandboxStateJson`。
- 快照文件：`{snapshot-dir}/{snapshotId}.tar`，由 Harness `LocalSnapshotSpec` 管理。
- 即使服务进程重启，状态 JSON 与快照仍在；下次 `start` 会走 `resume` 恢复沙箱。
