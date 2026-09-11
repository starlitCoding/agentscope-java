# Sandbox Service 可配置沙箱后端设计

## 背景

`agentscope-examples/agents/agentscope-sandbox-service` 当前 demo 已经能通过 HTTP API 暴露沙箱生命周期、命令执行和文件工具能力。现有实现以本地 Docker 沙箱为唯一后端，核心服务 `SandboxLifecycleService` 直接依赖 `DockerSandboxClient`、`DockerSandboxClientOptions`、`DockerSandboxState` 和 `sandbox-service.docker` 配置。

后续服务需要支持通过配置文件选择不同沙箱后端。第一阶段需要支持：

- 本地 Docker 沙箱，继续使用 `agentscope-harness` 中的 Docker 实现。
- Kubernetes 沙箱，使用 `agentscope-extensions/agentscope-extensions-sandbox/agentscope-extensions-sandbox-kubernetes` 模块中的实现。

Kubernetes 模块已经提供可复用的 Harness 沙箱适配类：

- `io.agentscope.extensions.sandbox.kubernetes.KubernetesSandboxClient`
- `io.agentscope.extensions.sandbox.kubernetes.KubernetesSandboxClientOptions`
- `io.agentscope.extensions.sandbox.kubernetes.KubernetesSandboxState`
- `io.agentscope.extensions.sandbox.kubernetes.KubernetesHarnessSandboxJacksonModule`

本次改造的重点是把服务层从 Docker 具体类型中解耦，让 Docker 与 Kubernetes 通过同一套生命周期、状态、快照和文件工具 API 对外服务。

## 目标

1. 支持通过 `application.yml` 配置选择沙箱后端：
   - `sandbox-service.backend=docker`
   - `sandbox-service.backend=kubernetes`
2. Controller 和 HTTP API 保持稳定，调用方不需要因为后端变化切换接口。
3. `SandboxLifecycleService` 不再直接依赖 Docker 具体类，而是依赖统一的后端 provider。
4. Docker 后端保持现有行为：
   - 有运行态实例时直接复用。
   - 有状态 JSON 时恢复。
   - 无状态时创建。
   - `stop` 保存快照但不删除容器。
   - `close` 保存快照并释放容器。
5. Kubernetes 后端复用 extensions 模块能力：
   - 使用 `KubernetesSandboxClient#create(...)` 创建 SandboxClaim。
   - 使用 `KubernetesSandboxClient#resume(...)` 按状态恢复。
   - 使用 `KubernetesSandbox#shutdown()` 释放框架拥有的 claim。
   - 继续使用统一快照策略保存和恢复工作区。
6. 状态仓库继续保存服务包装状态和 Harness 序列化后的 `sandboxStateJson`。
7. status 响应从 Docker 专属字段调整为通用结构，兼容 Docker 容器信息和 Kubernetes claim/pod 信息。
8. 保留同一个 `(userId, sessionId)` 的串行化操作语义，避免同一沙箱并发 start/exec/stop/close。

## 非目标

1. 不改变现有文件工具 API 的业务语义。
2. 不新增 ReactAgent、HarnessAgent 或模型调用能力。
3. 不实现多副本分布式锁；当前仍按单 JVM demo 设计。
4. 不封装 Kubernetes CRD 安装、warm pool 创建或集群初始化流程。
5. 不实现运行中后端迁移。例如同一个 `userId/sessionId` 已经用 Docker 创建状态后，不支持直接切换到 Kubernetes 继续恢复同一状态。
6. 不引入数据库或对象存储；状态 JSON 与快照仍使用本地文件。

## 依赖调整

`agentscope-sandbox-service` 模块需要新增 Kubernetes extension 依赖：

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-sandbox-kubernetes</artifactId>
</dependency>
```

原有 `agentscope-harness` 依赖继续保留。Kubernetes extension 依赖 Fabric8 Kubernetes Client，因此运行 Kubernetes 后端时需要当前进程具备可用的 kubeconfig、in-cluster service account 或显式传入的 Kubernetes client/config。

## 配置设计

默认配置保持 Docker，以免破坏当前 demo 的本地启动体验。

```yaml
server:
  port: 8080

sandbox-service:
  backend: docker
  state-dir: ${user.home}/.agentscope-sandbox-service/state
  snapshot-dir: ${user.home}/.agentscope-sandbox-service/snapshots

  docker:
    image: ubuntu:24.04
    workspace-root: /workspace
    network: none
    memory-size-bytes: 1073741824
    cpu-count: 2

  kubernetes:
    namespace: default
    warm-pool-name: agentscope-sandbox
    workspace-root: /workspace
    file-api-base-dir: /workspace
    api-url:
    gateway-name:
    gateway-namespace:
    gateway-scheme: http
    server-port: 8888
    sandbox-ready-timeout-seconds: 180
    cleanup-timeout-seconds: 30
    request-timeout-seconds: 180
    per-attempt-timeout-seconds: 60
    port-forward-timeout-seconds: 30

  exec:
    default-timeout-seconds: 120
```

配置含义：

| 配置项 | 说明 |
| --- | --- |
| `sandbox-service.backend` | 当前服务使用的沙箱后端，支持 `docker`、`kubernetes` |
| `sandbox-service.docker.*` | Docker 后端创建容器时使用的参数 |
| `sandbox-service.kubernetes.namespace` | 创建 SandboxClaim 的命名空间 |
| `sandbox-service.kubernetes.warm-pool-name` | Kubernetes agent-sandbox warm pool 名称 |
| `sandbox-service.kubernetes.workspace-root` | 沙箱内工作区目录 |
| `sandbox-service.kubernetes.file-api-base-dir` | Kubernetes runtime 文件 API 基准目录；为空时使用 base64-over-exec 传输 |
| `sandbox-service.kubernetes.api-url` | 直连 runtime API 时使用 |
| `sandbox-service.kubernetes.gateway-name` | 通过 gateway 连接时使用 |
| `sandbox-service.kubernetes.gateway-namespace` | gateway 所在命名空间，未配置时使用沙箱 namespace |
| `sandbox-service.kubernetes.gateway-scheme` | gateway 协议，默认 `http` |
| `sandbox-service.kubernetes.server-port` | runtime 服务端口，默认 `8888` |
| `sandbox-service.kubernetes.*timeout*` | 创建、请求、端口转发、清理等超时配置 |

Kubernetes 连接策略沿用 `KubernetesSandboxClient#toConnectionConfig(...)`：

1. 配置了 `api-url` 时使用 direct connection。
2. 未配置 `api-url` 但配置了 `gateway-name` 时使用 gateway connection。
3. 两者都未配置时使用 local tunnel / port-forward。

## 后端类型

新增枚举：

```java
public enum SandboxBackendType {
    DOCKER,
    KUBERNETES
}
```

配置绑定时允许小写 kebab-case 文本，例如 `docker`、`kubernetes`。如果配置了未知后端，应用启动阶段应失败，并给出明确错误信息。

## Provider 抽象

新增统一后端接口：

```java
public interface SandboxProvider {

    /** 返回当前 provider 对应的沙箱后端类型。 */
    SandboxBackendType backend();

    /** 根据业务 key 和快照策略创建新的沙箱实例。 */
    Sandbox create(SandboxKey key, SandboxSnapshotSpec snapshotSpec);

    /** 从持久化状态 JSON 恢复沙箱实例。 */
    Sandbox resume(String sandboxStateJson);

    /** 把沙箱当前状态序列化为 JSON。 */
    String serializeState(Sandbox sandbox);

    /** 返回该后端默认工作区根目录。 */
    String workspaceRoot();

    /** 提取对外展示的后端运行时信息。 */
    SandboxRuntimeDescriptor describe(Sandbox sandbox);
}
```

`SandboxLifecycleService` 只依赖 `SandboxProvider`、`SandboxSnapshotSpec`、`SandboxStateRepository`、`SandboxServiceProperties` 和 `SandboxOperationLockRegistry`。服务层不再 import Docker 或 Kubernetes 的具体 client/options/state。

## 运行时描述模型

新增通用描述对象：

```java
public record SandboxRuntimeDescriptor(
        SandboxBackendType backend,
        String workspaceRoot,
        Map<String, Object> attributes) {}
```

Docker provider 返回：

```json
{
  "backend": "DOCKER",
  "workspaceRoot": "/workspace",
  "attributes": {
    "containerId": "...",
    "containerName": "agentscope-sandbox-..."
  }
}
```

Kubernetes provider 返回：

```json
{
  "backend": "KUBERNETES",
  "workspaceRoot": "/workspace",
  "attributes": {
    "namespace": "default",
    "claimName": "as-sbx-...",
    "sandboxName": "...",
    "warmPoolName": "agentscope-sandbox",
    "podName": "...",
    "podIP": "..."
  }
}
```

字段 `attributes` 是后端特定信息，不参与通用生命周期判断。

## Docker Provider

`DockerSandboxProvider` 封装现有 Docker 逻辑：

- 持有 `DockerSandboxClient`。
- 从 `SandboxServiceProperties.Docker` 构造 `DockerSandboxClientOptions`。
- 创建 `WorkspaceSpec` 并设置 `root`。
- 使用 `DockerSandboxClient#create(...)` 创建沙箱。
- 使用 `DockerSandboxClient#deserializeState(...)` 和 `resume(...)` 恢复沙箱。
- 使用 `DockerSandboxClient#serializeState(...)` 保存状态。
- `describe(...)` 从 `DockerSandboxState` 提取 `containerId`、`containerName` 和 `workspaceRoot`。

Docker 后端继续使用当前快照语义：`stop()` 持久化工作区快照，`close()` 先 `stop()` 再 `shutdown()`。

## Kubernetes Provider

`KubernetesSandboxProvider` 封装 Kubernetes extension：

- 持有 `KubernetesSandboxClient`。
- 从 `SandboxServiceProperties.Kubernetes` 构造 `KubernetesSandboxClientOptions`。
- 创建 `WorkspaceSpec` 并设置 `root`。
- 使用 `KubernetesSandboxClient#create(...)` 创建 Kubernetes 沙箱。
- 使用 `KubernetesSandboxClient#deserializeState(...)` 和 `resume(...)` 恢复沙箱。
- 使用 `KubernetesSandboxClient#serializeState(...)` 保存状态。
- `describe(...)` 从 `KubernetesSandboxState` 提取 `namespace`、`claimName`、`sandboxName`、`warmPoolName`、`podName`、`podIP` 和 `workspaceRoot`。

Kubernetes 后端的 `close()` 继续调用通用 `sandbox.close()`。底层 `KubernetesSandbox` 负责根据 `claimOwned` 释放它拥有的 SandboxClaim。

## Jackson 序列化

状态仓库保存的 `sandboxStateJson` 可能来自 Docker 或 Kubernetes。ObjectMapper 必须同时注册 Harness 和 Kubernetes 沙箱类型：

```java
new ObjectMapper()
    .findAndRegisterModules()
    .registerModule(new HarnessSandboxJacksonModule())
    .registerModule(new KubernetesHarnessSandboxJacksonModule());
```

要求：

1. `FileSandboxStateRepository` 使用的 ObjectMapper 能读写服务包装记录。
2. Docker provider 使用的 client 能读写 `DockerSandboxState`。
3. Kubernetes provider 使用的 client 能读写 `KubernetesSandboxState`。
4. 如果状态文件中的 `sandboxStateJson` 后端类型和当前配置的 `backend` 不一致，`start` 应失败并返回明确错误，避免错误地用 Docker provider 读取 Kubernetes state，或反过来读取。

## 状态模型调整

建议扩展 `SandboxRecord`，新增 `backend` 字段：

```json
{
  "userId": "alice",
  "sessionId": "conv-1",
  "backend": "DOCKER",
  "status": "RUNNING",
  "sandboxStateJson": "{\"@type\":\"docker\", ...}",
  "createdAt": "2026-09-10T10:00:00Z",
  "updatedAt": "2026-09-10T10:05:00Z"
}
```

兼容策略：

- 读取旧状态文件时，如果 `backend` 为空，按 `DOCKER` 处理，因为当前 demo 只存在 Docker 状态。
- 保存新状态时必须写入当前 provider 的 `backend`。
- 当旧 Docker 状态在新版本启动后被再次保存，应补齐 `backend=DOCKER`。

## 生命周期语义

生命周期流程保持不变，但创建、恢复和状态序列化委托给 provider。

### start

1. 获取 `(userId, sessionId)` 的本地互斥锁。
2. 如果内存运行态存在且 `sandbox.isRunning()` 为 true，直接返回状态。
3. 从状态仓库加载 `SandboxRecord`。
4. 如果记录存在，校验 `record.backend` 与当前配置后端一致。
5. 有 `sandboxStateJson` 时调用 `provider.resume(...)`。
6. 无状态时调用 `provider.create(...)`。
7. 调用 `sandbox.start()`。
8. 写入 `RUNNING` 状态。

### stop

1. 获取互斥锁。
2. 如果内存运行态存在且运行中，调用 `sandbox.stop()`。
3. 使用 `provider.serializeState(...)` 写入 `STOPPED` 状态。
4. 不删除 Docker 容器或 Kubernetes claim。

### close

1. 获取互斥锁。
2. 优先使用内存运行态。
3. 如果内存中没有运行态但状态存在，调用 `provider.resume(...)`。
4. 调用 `sandbox.close()`，由后端完成保存快照和资源释放。
5. 移除内存运行态。
6. 写入 `CLOSED` 状态。

### exec 和文件工具

`exec`、读文件、写文件、编辑文件、列目录等工具继续调用 `requireRunning(...)`，确保沙箱已经运行。后端差异由 `Sandbox` 和文件系统适配层承担，Controller 不感知后端类型。

## HTTP 响应调整

`SandboxStatusResponse` 从 Docker 专属结构：

```java
String containerId;
String containerName;
```

调整为通用结构：

```java
SandboxBackendType backend;
Map<String, Object> runtime;
```

建议响应示例：

```json
{
  "userId": "alice",
  "sessionId": "conv-1",
  "backend": "KUBERNETES",
  "status": "RUNNING",
  "running": true,
  "snapshotRestorable": true,
  "workspaceRoot": "/workspace",
  "runtime": {
    "namespace": "default",
    "claimName": "as-sbx-123",
    "sandboxName": "sandbox-abc",
    "podName": "sandbox-abc-0",
    "podIP": "10.0.1.23"
  },
  "createdAt": "2026-09-10T10:00:00Z",
  "updatedAt": "2026-09-10T10:05:00Z"
}
```

为了减少破坏面，可以不保留顶层 `containerId` / `containerName`。如果需要短期兼容旧调用方，可临时保留这两个字段，仅 Docker 后端填充，Kubernetes 后端返回 null；但长期建议调用方统一读取 `runtime`。

## Spring Bean 装配

`SandboxServiceConfig` 负责根据配置创建唯一的 `SandboxProvider`。

推荐装配方式：

- 始终创建 `LocalSnapshotSpec`、`SandboxStateRepository`、`SandboxOperationLockRegistry`。
- 创建 `DockerSandboxProvider` 和 `KubernetesSandboxProvider` 所需的 client/options 工厂方法。
- `sandboxProvider(...)` 方法读取 `properties.getBackend()`，返回对应 provider。
- 未知 backend 直接抛出 `IllegalStateException`，让应用启动失败。

示意：

```java
@Bean
public SandboxProvider sandboxProvider(
        SandboxServiceProperties properties,
        ObjectMapper objectMapper) {
    return switch (properties.getBackend()) {
        case DOCKER -> new DockerSandboxProvider(properties, objectMapper);
        case KUBERNETES -> new KubernetesSandboxProvider(properties, objectMapper);
    };
}
```

`SandboxLifecycleService` 构造器改为接收 `SandboxProvider`：

```java
public SandboxLifecycleService(
        SandboxProvider provider,
        SandboxSnapshotSpec snapshotSpec,
        SandboxStateRepository repository,
        SandboxServiceProperties properties,
        SandboxOperationLockRegistry lockRegistry)
```

## 后端切换限制

同一个 `(userId, sessionId)` 的状态不能跨后端恢复。

示例：

1. 服务以 `backend=docker` 启动，创建 `userA/session1`。
2. 状态文件保存 `backend=DOCKER`。
3. 服务改为 `backend=kubernetes` 后再次 start `userA/session1`。
4. 服务应返回错误，提示该 session 已绑定 Docker 后端，不能用 Kubernetes 后端恢复。

如果调用方确实要切换后端，需要先调用 close，然后删除或归档旧状态，再创建新的 session，或者使用新的 `sessionId`。

## 错误处理

新增或复用错误码：

| 错误码 | 场景 |
| --- | --- |
| `UNSUPPORTED_BACKEND` | 配置了不支持的 backend |
| `BACKEND_MISMATCH` | 状态文件后端与当前配置后端不一致 |
| `SANDBOX_START_FAILED` | Docker 容器或 Kubernetes SandboxClaim 创建/恢复失败 |
| `SANDBOX_EXEC_FAILED` | 命令执行失败 |
| `SANDBOX_EXEC_TIMEOUT` | 命令执行超时 |
| `SANDBOX_NOT_FOUND` | 查询或停止不存在的沙箱 |

Kubernetes 连接错误、warm pool 不存在、SandboxClaim 未 ready 等异常统一包装为 `SANDBOX_START_FAILED`，并在 `detail` 中保留底层错误信息。

## 测试策略

### 单元测试

1. `SandboxServicePropertiesTest`
   - 验证默认配置绑定为 `backend=DOCKER`。
   - 验证 Kubernetes 配置字段能正确绑定。

2. `DockerSandboxProviderTest`
   - 验证 Docker options 从配置生成。
   - 验证 `describe(...)` 能返回 container 信息。

3. `KubernetesSandboxProviderTest`
   - 验证 Kubernetes options 从配置生成。
   - 验证 direct/gateway/local tunnel 配置选择符合 `KubernetesSandboxClient#toConnectionConfig(...)`。
   - 验证 `describe(...)` 能返回 namespace、claim、sandbox、pod 信息。

4. `SandboxLifecycleServiceTest`
   - 使用 fake provider 测试 start/stop/close/exec 通用流程。
   - 验证服务层不再依赖 Docker 具体类型。
   - 验证旧状态缺少 backend 时按 Docker 兼容。
   - 验证 backend mismatch 时失败。

5. `SandboxStatusResponseTest`
   - 验证 Docker runtime attributes。
   - 验证 Kubernetes runtime attributes。

### 集成测试

Docker 集成测试继续保留为可选测试。

Kubernetes 集成测试默认不在普通构建中运行，建议通过 profile 或环境变量启用：

```text
-Dit.kubernetes=true
```

集成测试前提：

- 当前环境能访问 Kubernetes API。
- 目标 namespace 存在。
- agent-sandbox CRD 和 warm pool 已部署。
- 测试账号具备创建、读取、删除 SandboxClaim 以及访问 runtime 的权限。

## 迁移步骤

1. 增加 Kubernetes extension Maven 依赖。
2. 扩展 `SandboxServiceProperties`：
   - 新增 `backend`。
   - 新增 `Kubernetes` nested config。
3. 新增 `SandboxBackendType`、`SandboxProvider`、`SandboxRuntimeDescriptor`。
4. 新增 `DockerSandboxProvider`，迁移现有 Docker create/resume/serialize/describe 逻辑。
5. 新增 `KubernetesSandboxProvider`，接入 `KubernetesSandboxClient`。
6. 修改 `SandboxLifecycleService`：
   - 构造器依赖 `SandboxProvider`。
   - `createSandbox(...)` 改为 `provider.create(...)`。
   - `client.deserializeState(...)` 改为 `provider.resume(...)`。
   - `client.serializeState(...)` 改为 `provider.serializeState(...)`。
   - `toStatus(...)` 改为使用 `provider.describe(...)`。
7. 修改 `SandboxRecord`，增加 `backend` 字段并兼容旧记录。
8. 修改 `SandboxStatusResponse`，增加 `backend` 和 `runtime`，移除或兼容 Docker 顶层字段。
9. 修改 `SandboxServiceConfig`，根据配置选择 provider。
10. 更新 `application.yml` 和 README。
11. 补充单元测试和可选 Kubernetes 集成测试说明。

## 验收标准

1. 默认配置不变时，服务使用 Docker 后端，原有 Docker 生命周期和文件工具测试通过。
2. 配置 `sandbox-service.backend=kubernetes` 时，Spring 能创建 Kubernetes provider。
3. `SandboxLifecycleService` 源码不再 import `DockerSandboxClientOptions`、`DockerSandboxClient`、`DockerSandboxState`、`KubernetesSandboxClientOptions`、`KubernetesSandboxClient` 或 `KubernetesSandboxState`。
4. 状态文件包含 `backend` 字段；旧 Docker 状态文件没有该字段时仍可恢复。
5. 同一个 `(userId, sessionId)` 的操作仍然串行。
6. `status` 响应包含通用 `backend`、`workspaceRoot`、`runtime` 字段。
7. Docker 后端能返回 container runtime 信息。
8. Kubernetes 后端能返回 namespace、claim、sandbox、pod runtime 信息。
9. 后端配置与已有状态不一致时，返回明确错误，不做隐式跨后端恢复。
10. 普通 Maven 测试不依赖真实 Docker 或 Kubernetes 集群；真实后端测试通过显式 profile 或环境变量启用。

## 风险与注意事项

1. Kubernetes 后端依赖集群侧 CRD、warm pool 和网络连接，demo 只能保证 Java 侧配置和调用链正确，不能替代集群安装检查。
2. 本地文件状态仓库不适合多副本部署。后续如果 sandbox-service 多实例部署，需要把状态仓库和锁迁移到 Redis、数据库或其他共享基础设施。
3. `backend` 是服务级配置，不是每次请求传入。这样可以保持 API 简单，但同一个服务实例不能同时为部分用户使用 Docker、部分用户使用 Kubernetes。若后续需要按请求选择后端，需要把 `backend` 加入状态 key 或请求参数，并重新设计隔离和状态迁移规则。
4. Kubernetes `fileApiBaseDir` 为空时会退化到 base64-over-exec，适合兼容但不适合大文件传输。
5. `runtime` 是后端特定字段，调用方不应依赖其字段在不同后端间一致。
