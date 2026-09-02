# Harness 写代码与沙箱生命周期流程

本文梳理 AgentScope Java 中 `HarnessAgent` 在执行“写代码”类任务时，如何从用户请求进入 agent，如何通过工具在沙箱中执行命令，如何按用户和会话做隔离，以及沙箱如何保存、销毁和在下一次聊天时恢复。

涉及的核心类：

- `io.agentscope.harness.agent.HarnessAgent`
- `io.agentscope.core.agent.RuntimeContext`
- `io.agentscope.harness.agent.middleware.SandboxLifecycleMiddleware`
- `io.agentscope.harness.agent.sandbox.SandboxManager`
- `io.agentscope.harness.agent.sandbox.SandboxIsolationKey`
- `io.agentscope.harness.agent.sandbox.SessionSandboxStateStore`
- `io.agentscope.harness.agent.sandbox.impl.docker.DockerSandbox`
- `io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshot`

## 总体流程

一个典型的 Harness 写代码流程可以概括为：

```text
用户请求
  -> 构造 RuntimeContext(userId, sessionId)
  -> HarnessAgent.call / streamEvents
  -> 注入 SandboxContext、WorkspaceManager、Permission 等运行时上下文
  -> SandboxLifecycleMiddleware.acquireForCall
  -> SandboxManager 根据 IsolationScope 解析隔离 key
  -> 从状态存储恢复旧 SandboxState，或创建新沙箱
  -> DockerSandbox.start 启动容器并恢复 /workspace
  -> Agent 调模型，模型决定调用工具
  -> 文件工具 / execute shell 工具在沙箱 /workspace 中操作
  -> 调用结束
  -> SandboxLifecycleMiddleware.releaseForCall
  -> 保存 sandbox state 和 workspace snapshot
  -> stop / shutdown 沙箱
```

这里的关键点是：用户和 session 级别的隔离不是 Docker 自动完成的，而是 Harness 根据 `RuntimeContext` 和 `IsolationScope` 算出不同的沙箱状态槽，再按状态槽保存和恢复沙箱环境。

## 用户请求进入 HarnessAgent

调用方通常会传入一条用户消息和一个运行时上下文：

```java
RuntimeContext runtimeContext =
        RuntimeContext.builder()
                .userId("alice")
                .sessionId("coding-session-1")
                .build();

agent.streamEvents(new UserMessage("请帮我修改 Java 代码并运行测试"), runtimeContext);
```

`userId` 和 `sessionId` 在普通对话里用于区分用户与会话；在沙箱模式下，它们还会被用于计算沙箱隔离 key。

如果调用方没有显式传 `sessionId`，`HarnessAgent` 会补默认 session；如果没有 `userId`，某些隔离模式会降级到 session 级别，或者跳过状态恢复。

## HarnessAgent 如何准备沙箱上下文

当 builder 配置了 sandbox filesystem，例如 Docker：

```java
HarnessAgent.builder()
        .name("coding-agent")
        .workspace(hostWorkspace)
        .filesystem(
                new DockerFilesystemSpec()
                        .image("ubuntu:24.04")
                        .workspaceRoot("/workspace")
                        .workspaceSpec(workspaceSpec)
                        .isolationScope(IsolationScope.SESSION))
        .build();
```

`DockerFilesystemSpec` 不是运行时文件系统本身，而是一份声明式配置。构建 agent 时它会生成 `SandboxContext`，里面包含：

- `SandboxClient`：负责创建和恢复具体沙箱，Docker 场景下是 `DockerSandboxClient`
- `SandboxClientOptions`：镜像、workspaceRoot、网络、端口、CPU、内存等
- `WorkspaceSpec`：工作区初始文件、目录、环境变量、挂载等
- `SandboxSnapshotSpec`：快照策略，默认可能是 no-op
- `IsolationScope`：隔离范围

如果配置了 `.workspace(hostWorkspace)`，Harness 还会把本地 workspace 中的一些根目录投影到沙箱中，例如 `AGENTS.md`、`skills`、`subagents`、`knowledge`、`.skills-cache`。这不是整个宿主机目录的全量映射，而是按配置生成 workspace projection。

## 用户和 Session 级别隔离

隔离范围由 `IsolationScope` 控制：

```java
public enum IsolationScope {
    SESSION,
    USER,
    AGENT,
    GLOBAL
}
```

不同范围的含义：

| Scope | 隔离规则 | 典型效果 |
| --- | --- | --- |
| `SESSION` | 按 `sessionId` 隔离 | 同一用户不同会话使用不同沙箱状态 |
| `USER` | 按 `userId` 隔离，缺失时 fallback 到 `sessionId` | 同一用户多个会话共享一个沙箱状态 |
| `AGENT` | 按 agent 名称隔离 | 所有用户共享该 agent 的沙箱状态 |
| `GLOBAL` | 全局共享 | 所有人共用一个沙箱状态 |

解析隔离 key 的逻辑在 `SandboxIsolationKey.resolve(...)`：

```text
SESSION -> value = sessionId
USER    -> value = userId；没有 userId 时 fallback 到 sessionId
AGENT   -> value = agentId
GLOBAL  -> value = "__global__"
```

随后 `SessionSandboxStateStore` 会把这个 key 编码成内部状态槽：

```text
SESSION -> sandbox/session/<sessionId>
USER    -> sandbox/user/<agentId>/<userId>
AGENT   -> sandbox/agent/<agentId>
GLOBAL  -> sandbox/global
```

所以真正隔离的是“沙箱状态槽”。只要两次调用解析出的状态槽不同，就不会恢复到同一个沙箱状态；只要状态槽相同，就会尝试恢复之前保存的沙箱状态。

## 沙箱 acquire 流程

每次 agent 调用开始前，`SandboxLifecycleMiddleware.acquireForCall(...)` 会被触发。

它做四件事：

1. 从 `RuntimeContext` 中取出 `SandboxContext`
2. 调用 `SandboxManager.acquire(...)` 获取沙箱
3. 调用 `sandbox.start()` 启动沙箱并准备工作目录
4. 把 live sandbox 注入 `SandboxBackedFilesystem`，后续工具调用就会打到这个沙箱

`SandboxManager.acquire(...)` 的优先级是：

```text
Priority 1: 如果调用方直接传入 externalSandbox，直接使用它
Priority 2: 如果调用方传入 externalSandboxState，直接 resume 这个 state
Priority 3: 根据隔离 key 从状态存储读取历史 SandboxState，读到就 resume
Priority 4: 没有历史状态，则创建新沙箱
```

也就是说，通常的 Harness 托管场景走 Priority 3 或 Priority 4：

- 老会话继续聊：命中历史 `SandboxState`，恢复
- 新用户或新 session：没有历史状态，新建

## Docker 沙箱启动流程

Docker 场景下，`DockerSandbox.start()` 会先确保容器存在并运行：

```text
如果 containerId 存在且正在运行
  -> 复用容器

如果 containerId 存在但已停止
  -> docker start <containerId>

如果 containerId 不存在、被删除或无法 inspect
  -> 重新 docker run 创建容器
  -> workspaceRootReady = false
```

创建容器时会根据 `DockerSandboxState` 和 `WorkspaceSpec` 拼出类似命令：

```bash
docker run -d \
  --name agentscope-sandbox-<sandboxSessionId> \
  -e KEY=VALUE \
  --network=<network> \
  -v <hostPath>:<containerPath>:rw \
  ubuntu:24.04 \
  sh -c 'while :; do sleep 3600; done'
```

容器启动后，才进入工作目录恢复流程。

## 工作目录初始化与恢复

`AbstractBaseSandbox.start()` 里有四个分支：

```text
A. workspaceRootReady=true，且 /workspace 仍存在
   -> 不恢复快照，只重新应用 ephemeral entries

B. workspaceRootReady=true，但 /workspace 丢失
   -> 从 snapshot 恢复 /workspace，再应用 ephemeral entries

C. workspaceRootReady=false，且 snapshot 可恢复
   -> 创建 /workspace，从 snapshot 恢复，再应用完整 WorkspaceSpec

D. workspaceRootReady=false，且没有可恢复 snapshot
   -> 创建 /workspace，直接应用完整 WorkspaceSpec
```

当旧容器被删除时，Docker 启动阶段会把 `workspaceRootReady` 设为 `false`，所以后续一般进入 C 或 D：

- 有 snapshot：重建容器后恢复 `/workspace`
- 没 snapshot：只能重新按 `WorkspaceSpec` 初始化

Docker 恢复工作目录的真实动作等价于：

```bash
docker exec <newContainerId> mkdir -p /workspace
docker exec -i <newContainerId> tar -xf - -C /workspace < snapshot.tar
```

## 工具调用如何进入沙箱

HarnessAgent 写代码时，模型不会直接操作宿主机文件系统，而是通过 Harness 暴露的工具执行动作。

典型工具包括：

- 文件读取
- 文件写入
- 目录查看
- shell 命令执行

当配置的是 sandbox filesystem 时，这些文件操作会走 `SandboxBackedFilesystem`，最终落到当前调用注入的 live sandbox。

shell 命令执行在 Docker 场景下最终等价于：

```bash
docker exec -w /workspace <containerId> sh -c '<command>'
```

比如模型要求运行：

```bash
mvn test
```

实际执行路径就是：

```bash
docker exec -w /workspace <containerId> sh -c 'mvn test'
```

所以 agent 在沙箱里 clone 仓库、写 Java 文件、运行 Maven，本质都是围绕 `/workspace` 做文件操作和命令执行。

## 调用结束时什么时候停掉沙箱

每次 agent 调用结束后，`SandboxLifecycleMiddleware.releaseForCall(...)` 会执行清理逻辑：

```text
persistState
  -> 保存 SandboxState JSON

release
  -> sandbox.stop()
  -> sandbox.shutdown()

clear filesystemProxy
```

`sandbox.stop()` 做的是工作区快照保存：

```text
如果 snapshot 开启持久化
  -> tar /workspace
  -> 保存 tar 快照
workspaceRootReady = true
```

Docker 保存快照的真实动作等价于：

```bash
docker exec <containerId> tar -cf - -C /workspace . > snapshot.tar
```

`sandbox.shutdown()` 对 Docker 自管理容器会执行：

```bash
docker stop --time=<timeout> <containerId>
docker rm --force <containerId>
```

因此在默认 Harness 自管理 Docker 沙箱下，容器通常是“每次调用结束后保存状态并删除容器”。下一次恢复依靠的是 `SandboxState` 和 snapshot，而不是依靠旧容器一直存在。

有一个例外：如果使用的是调用方传入的 `externalSandbox`，Harness 认为它是用户管理的沙箱，不会 stop、snapshot 或 shutdown。生命周期由外部调用方自己负责。

## 快照保存原理

快照接口是 `SandboxSnapshot`：

```java
void persist(InputStream workspaceArchive);
InputStream restore();
boolean isRestorable();
```

Docker 沙箱停止时，会调用 `doPersistWorkspace()`，它在容器里执行：

```bash
docker exec <containerId> tar -cf - -C /workspace .
```

这个 tar 流会交给具体 snapshot 实现：

- `NoopSandboxSnapshot`：不保存，直接丢弃
- `LocalSandboxSnapshot`：保存成本地 `{basePath}/{id}.tar`
- `RemoteSandboxSnapshot`：上传到远端存储，例如 S3、OSS、GCS 这类后端

需要注意：`DockerFilesystemSpec` 默认使用 no-op snapshot。也就是说，如果没有显式配置 local 或 remote snapshot，容器被销毁后，`/workspace` 的运行时修改不能靠 snapshot 恢复。

## 下一次聊天如何恢复之前的沙箱环境

下一次用户继续聊天时，只要满足以下条件，就会尝试恢复之前的沙箱：

1. 使用同一个 agent
2. `IsolationScope` 相同
3. `RuntimeContext` 解析出来的隔离 key 相同
4. `SessionSandboxStateStore` 中还能读到之前保存的 `SandboxState`
5. snapshot 可恢复，或者旧容器还存在且 `/workspace` 还在

恢复流程是：

```text
用户继续发消息
  -> RuntimeContext(userId, sessionId)
  -> SandboxIsolationKey.resolve(...)
  -> SessionSandboxStateStore.load(...)
  -> DockerSandboxClient.deserializeState(...)
  -> DockerSandboxClient.resume(...)
  -> DockerSandbox.start()
```

如果旧容器仍在：

```text
docker inspect 旧 containerId 成功
  -> 如果 running，直接用
  -> 如果 stopped，docker start
  -> /workspace 还在则继续使用
```

如果旧容器已经被删除：

```text
docker inspect 旧 containerId 失败
  -> 使用原 state 中的 image/network/env/ports 等配置 docker run 新容器
  -> 如果 snapshot 可恢复，把 snapshot tar 解到 /workspace
  -> 如果 snapshot 不可恢复，重新应用 WorkspaceSpec
  -> 新 containerId 写回 DockerSandboxState
```

这就是“下一次聊天进入之前沙箱”的真实含义：并不一定是进入同一个 Docker 容器，而是进入同一个隔离状态槽对应的工作环境。容器可以换，但 `/workspace` 可以通过 snapshot 恢复。

## 默认行为与风险点

默认行为需要特别注意：

- `IsolationScope` 默认偏向 `USER`，没有 `userId` 时可能 fallback 到 `SESSION`
- Docker 容器如果是 Harness 自管理，调用结束后会 shutdown 并删除
- Docker 默认 snapshot 是 no-op，不会保存 `/workspace` tar
- 如果没有配置可持久化 snapshot，容器删除后，运行过程中生成的文件无法恢复
- 如果使用 `USER` 隔离，同一个用户多个 session 可能共享一个沙箱状态
- 如果使用 `AGENT` 或 `GLOBAL`，多个用户可能争用同一个沙箱状态，需要考虑并发保护

## 一句话总结

Harness 写代码的沙箱流程可以理解为：

```text
RuntimeContext 决定隔离槽
SandboxManager 决定恢复还是新建
DockerSandbox 提供容器执行环境
WorkspaceSpec 初始化工作目录
SandboxSnapshot 保存和恢复 /workspace
工具调用通过 SandboxBackedFilesystem 和 docker exec 落到沙箱
调用结束后保存状态并销毁自管理容器
下一次同一隔离槽再用 SandboxState + snapshot 还原环境
```

