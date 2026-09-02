---
title: "通过 Jar 复用本地 Docker 沙箱能力"
description: "不使用 ReactAgent，仅复用 AgentScope Harness 的 Docker 沙箱创建、执行、快照和关闭能力"
---

# 通过 Jar 复用本地 Docker 沙箱能力

## 结论

可以通过引入 `agentscope-harness` jar 的方式复用本地 Docker 沙箱能力，且不需要使用 `ReactAgent`。

当前代码里，本地 Docker 沙箱不是放在 `agentscope-extensions-sandbox` 扩展包中，而是内置在 `agentscope-harness` 模块：

- `io.agentscope.harness.agent.sandbox.Sandbox`：运行中沙箱的统一接口。
- `io.agentscope.harness.agent.sandbox.SandboxClient`：创建、恢复、删除和序列化沙箱状态的统一接口。
- `io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClient`：本地 Docker 后端客户端。
- `io.agentscope.harness.agent.sandbox.impl.docker.DockerSandbox`：通过宿主机 `docker` CLI 创建容器、执行命令、打包/恢复工作区。
- `io.agentscope.harness.agent.sandbox.snapshot.LocalSnapshotSpec`：把工作区快照保存为宿主机本地 tar 文件。

所以，另一个 Java 项目只要能拿到 `agentscope-harness` 依赖，就可以直接调用 `SandboxClient` / `Sandbox` 这组接口，独立完成：

- 沙箱创建：`DockerSandboxClient#create(...)`
- 沙箱启动或恢复：`Sandbox#start()`
- 沙箱内命令执行：`Sandbox#exec(...)`
- 工作区快照保存：`Sandbox#stop()` 或 `Sandbox#persistWorkspace()`
- 沙箱状态序列化：`DockerSandboxClient#serializeState(...)`
- 从状态恢复：`DockerSandboxClient#deserializeState(...)` + `DockerSandboxClient#resume(...)`
- 沙箱关闭并删除容器：`Sandbox#shutdown()` 或 `Sandbox#close()`

需要注意：这是一套偏底层的沙箱 API，不是一个已经包装好的独立“沙箱服务 SDK”。如果你的业务项目不想引入 Agent 运行时概念，可以自己在业务侧封装一层 `SandboxService`，把 `SandboxState` JSON 保存到数据库，把快照目录挂到固定磁盘路径。

## 推荐依赖

如果使用 Maven，推荐引入 BOM 后再引入 `agentscope-harness`。

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.agentscope</groupId>
            <artifactId>agentscope-bom</artifactId>
            <version>${agentscope.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.agentscope</groupId>
        <artifactId>agentscope-harness</artifactId>
    </dependency>
</dependencies>
```

如果当前版本还没有发布到 Maven 仓库，可以先在本地构建并安装：

```bash
mvn -pl agentscope-harness -am install
```

本地 Docker 方案的运行前提：

- 宿主机已安装 Docker。
- Java 进程所在用户可以执行 `docker` 命令。
- 运行镜像内必须有 `sh`、`tar`、`base64`、`mkdir`、`rm` 等基础命令。
- 如果要使用文件系统封装里的精确编辑能力，镜像内还需要 `python3`。

## 最小生命周期方案

下面代码不创建 `HarnessAgent`，不使用 `ReactAgent`，只直接使用沙箱接口。

```java
import io.agentscope.harness.agent.sandbox.ExecResult;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxState;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClient;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClientOptions;
import io.agentscope.harness.agent.sandbox.layout.FileEntry;
import io.agentscope.harness.agent.sandbox.snapshot.LocalSnapshotSpec;
import java.nio.file.Path;

public class DockerSandboxDemo {

    /**
     * 创建一个本地 Docker 沙箱，执行命令，保存快照，并返回可持久化的状态 JSON。
     */
    public String createRunAndStop() throws Exception {
        DockerSandboxClient client = new DockerSandboxClient();

        WorkspaceSpec workspaceSpec = new WorkspaceSpec();
        workspaceSpec.setRoot("/workspace");
        workspaceSpec.getEntries().put("README.md", new FileEntry("# sandbox demo\n"));

        DockerSandboxClientOptions options = new DockerSandboxClientOptions()
                .image("ubuntu:24.04")
                .workspaceRoot("/workspace")
                .memorySizeBytes(1024L * 1024L * 1024L)
                .cpuCount(2L)
                .network("none");

        LocalSnapshotSpec snapshotSpec = new LocalSnapshotSpec(
                Path.of("/data/agentscope/sandbox-snapshots"));

        Sandbox sandbox = client.create(workspaceSpec, snapshotSpec, options);
        try {
            sandbox.start();

            ExecResult result = sandbox.exec(null, "echo hello > hello.txt && ls -la", 30);
            System.out.println(result.combinedOutput());

            sandbox.stop();
            return client.serializeState(sandbox.getState());
        } catch (Exception e) {
            sandbox.close();
            throw e;
        }
    }

    /**
     * 从之前保存的状态 JSON 恢复沙箱，继续使用同一个工作区。
     */
    public void resumeAndShutdown(String savedStateJson) throws Exception {
        DockerSandboxClient client = new DockerSandboxClient();

        SandboxState state = client.deserializeState(savedStateJson);
        Sandbox sandbox = client.resume(state);
        try {
            sandbox.start();
            ExecResult result = sandbox.exec(null, "cat hello.txt", 30);
            System.out.println(result.stdout());
            sandbox.stop();
        } finally {
            sandbox.shutdown();
        }
    }
}
```

这段代码对应的真实行为：

1. `create(...)` 只创建 Java 侧沙箱对象和初始状态，不立刻创建容器。
2. `start()` 会执行 `docker run -d ...` 创建并启动容器，然后初始化 `/workspace`。
3. `exec(...)` 会在容器内执行 `docker exec -w /workspace <containerId> sh -c <command>`。
4. `stop()` 会把 `/workspace` 打成 tar，保存到 `LocalSnapshotSpec` 指定目录下，但不会删除容器。
5. `serializeState(...)` 会把容器 ID、镜像、工作区路径、快照 ID 等信息保存成 JSON。
6. `resume(...)` 会按状态恢复沙箱对象；`start()` 时如果容器还在就复用，如果容器已停止就重启，如果容器被删除就新建容器并从快照恢复。
7. `shutdown()` 会停止并删除框架自管的 Docker 容器。

## 生命周期语义

`Sandbox` 的几个方法要区分清楚：

| 方法 | 作用 | 是否保存快照 | 是否删除容器 |
|------|------|--------------|--------------|
| `start()` | 创建、启动或恢复沙箱工作区 | 否 | 否 |
| `exec(...)` | 在沙箱工作区内执行 shell 命令 | 否 | 否 |
| `stop()` | 保存当前工作区快照，标记本次使用结束 | 是 | 否 |
| `shutdown()` | 停止并删除自管 Docker 容器 | 否 | 是 |
| `close()` | 先 `stop()`，再 `shutdown()` | 是 | 是 |

如果你希望容器保留在本机，方便下次直接复用，调用 `stop()` 后持久化状态 JSON 即可。如果你希望每次任务结束都释放 Docker 资源，调用 `close()` 或 `stop()` 后再调用 `shutdown()`。

推荐业务侧采用下面的策略：

- 短任务、资源敏感：任务结束后 `stop()` + 保存状态 JSON + `shutdown()`。
- 长会话、本机开发：每次请求结束只 `stop()` + 保存状态 JSON，定时清理长时间不用的容器。
- 异常处理：业务执行失败时仍尽量调用 `stop()` 保存现场；如果保存失败，再调用 `shutdown()` 释放资源。

## 快照保存方案

本地 Docker 方案可以先使用 `LocalSnapshotSpec`：

```java
LocalSnapshotSpec snapshotSpec = new LocalSnapshotSpec(
        "/data/agentscope/sandbox-snapshots");
```

保存后，每个沙箱会生成一个 `{snapshotId}.tar` 文件。`snapshotId` 默认使用 `DockerSandboxClient#create(...)` 生成的 sessionId。

需要同时持久化两类数据：

| 数据 | 保存位置 | 用途 |
|------|----------|------|
| `SandboxState` JSON | 你的业务数据库或状态表 | 下次知道该恢复哪个容器、哪个镜像、哪个快照 |
| 工作区 tar 快照 | `LocalSnapshotSpec` 指定目录 | 容器不存在时恢复 `/workspace` 内容 |

只保存 tar 不够，因为恢复时还需要 `SandboxState` 里的镜像、workspaceRoot、快照 ID 等元数据。只保存 JSON 也不够，因为容器被删除后没有 tar 就只能冷启动。

## 沙箱内部能力

直接使用 `Sandbox#exec(...)` 时，你获得的是最基础也最通用的能力：在 `/workspace` 内执行 shell 命令。

```java
ExecResult result = sandbox.exec(null, "pwd && ls -la && python3 --version", 60);
```

如果你还希望获得 AgentScope 文件系统层封装好的能力，比如读文件、写文件、列目录、搜索、移动、删除、上传下载，可以使用 `SandboxBackedFilesystem`，但需要手动注入当前沙箱：

```java
import io.agentscope.harness.agent.filesystem.sandbox.SandboxBackedFilesystem;
import io.agentscope.harness.agent.filesystem.model.ReadResult;
import io.agentscope.harness.agent.filesystem.model.WriteResult;

public class SandboxFileOperations {

    /**
     * 在指定沙箱上执行文件读写操作，不依赖 ReactAgent。
     */
    public void writeAndRead(Sandbox sandbox) {
        SandboxBackedFilesystem fs = new SandboxBackedFilesystem();
        fs.setSandbox(sandbox);

        WriteResult write = fs.write(null, "/workspace/app.txt", "hello\n");
        if (!write.isSuccess()) {
            throw new IllegalStateException(write.error());
        }

        ReadResult read = fs.read(null, "/workspace/app.txt", 0, 100);
        if (!read.isSuccess()) {
            throw new IllegalStateException(read.error());
        }

        System.out.println(read.fileData().content());
    }
}
```

这部分能力本质上仍然通过 `sandbox.exec(...)` 调用容器内的 Unix 命令实现，所以镜像必须满足运行时约束。

## 建议封装成业务服务

另一个项目里建议封装一层自己的 `SandboxService`，不要把 `DockerSandboxClient` 直接散落在业务代码里。

```java
public interface BusinessSandboxService {

    /**
     * 创建一个新的业务沙箱，初始化工作区，并返回业务侧沙箱 ID。
     */
    String createSandbox(String ownerId) throws Exception;

    /**
     * 在已有业务沙箱中执行命令，并返回命令输出。
     */
    String execute(String sandboxId, String command, int timeoutSeconds) throws Exception;

    /**
     * 保存工作区快照和沙箱状态，但不删除底层容器。
     */
    void saveSnapshot(String sandboxId) throws Exception;

    /**
     * 关闭业务沙箱，释放底层 Docker 容器。
     */
    void closeSandbox(String sandboxId) throws Exception;
}
```

业务表可以按下面方式设计：

| 字段 | 含义 |
|------|------|
| `sandbox_id` | 业务侧 ID |
| `owner_id` | 用户、租户或任务 ID |
| `state_json` | `SandboxClient#serializeState(...)` 结果 |
| `snapshot_base_path` | 本地快照目录 |
| `status` | `RUNNING`、`STOPPED`、`CLOSED` |
| `created_at` / `updated_at` | 创建和更新时间 |

服务实现时，每次执行命令的流程建议固定为：

1. 根据 `sandbox_id` 读取 `state_json`。
2. `deserializeState(...)`。
3. `resume(...)`。
4. `start()`。
5. `exec(...)`。
6. `stop()`。
7. `serializeState(...)` 并更新数据库。

如果要立刻释放资源，则在第 7 步后调用 `shutdown()`。

## Docker 镜像建议

建议先使用 Debian / Ubuntu 系基础镜像，例如：

```dockerfile
FROM ubuntu:24.04

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        bash \
        coreutils \
        findutils \
        git \
        grep \
        maven \
        openjdk-17-jdk \
        sed \
        tar \
        python3 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
```

不建议第一版使用 Alpine 或 distroless 镜像，因为文件系统封装依赖 GNU 风格的 `stat -c`、`grep --include`、`base64 -d` 等命令行为。

### 在 M1 Mac 上构建和使用镜像

如果你是 Apple Silicon，也就是 M1 / M2 / M3 芯片的 Mac，本机 Docker 默认更适合运行 `linux/arm64` 镜像。建议先按下面方式构建一个专门给 Harness 沙箱使用的镜像。

先新建一个目录保存 Dockerfile：

```bash
mkdir -p ~/agentscope-sandbox-image
cd ~/agentscope-sandbox-image
```

把上面的 Dockerfile 内容保存为当前目录下的 `Dockerfile`，然后构建镜像：

```bash
docker buildx build \
  --platform linux/arm64 \
  -t agentscope-sandbox-ubuntu:24.04-jdk17 \
  --load \
  .
```

这里几个参数的含义：

| 参数 | 含义 |
|------|------|
| `--platform linux/arm64` | 构建适合 M1 Mac 本机运行的 ARM64 镜像 |
| `-t agentscope-sandbox-ubuntu:24.04-jdk17` | 给镜像起一个本地名称，后续 Java 代码里直接引用这个名称 |
| `--load` | 把 buildx 构建结果加载到本机 Docker 镜像列表中 |
| `.` | 使用当前目录下的 Dockerfile 构建 |

构建完成后，先确认镜像已经在本机：

```bash
docker images agentscope-sandbox-ubuntu
```

再启动一个临时容器验证关键命令是否齐全：

```bash
docker run --rm \
  --platform linux/arm64 \
  -w /workspace \
  agentscope-sandbox-ubuntu:24.04-jdk17 \
  sh -lc 'java -version && mvn -version && git --version && python3 --version && tar --version'
```

如果这些命令都能正常输出版本号，就可以把 Java 代码里的镜像从 `ubuntu:24.04` 换成你刚构建的镜像：

```java
DockerSandboxClientOptions options = new DockerSandboxClientOptions()
        .image("agentscope-sandbox-ubuntu:24.04-jdk17")
        .workspaceRoot("/workspace")
        .memorySizeBytes(1024L * 1024L * 1024L)
        .cpuCount(2L)
        .network("none");
```

如果沙箱内需要 `git clone` 远程仓库，不能使用 `.network("none")`，需要改成 Docker 可用的网络，例如：

```java
DockerSandboxClientOptions options = new DockerSandboxClientOptions()
        .image("agentscope-sandbox-ubuntu:24.04-jdk17")
        .workspaceRoot("/workspace")
        .memorySizeBytes(1024L * 1024L * 1024L)
        .cpuCount(2L)
        .network("bridge");
```

也可以只在初始化阶段打开网络拉代码，后续执行不需要网络的任务时再使用无网络配置。是否能动态切换取决于业务侧如何创建和管理沙箱，最简单的方式是为“联网初始化”和“离线执行”使用不同的沙箱生命周期。

如果你的镜像未来要在 x86_64 Linux 服务器上跑，不要继续使用 `linux/arm64`，应构建 amd64 镜像：

```bash
docker buildx build \
  --platform linux/amd64 \
  -t agentscope-sandbox-ubuntu:24.04-jdk17-amd64 \
  --load \
  .
```

M1 Mac 也能通过模拟运行 `linux/amd64` 镜像，但速度会明显慢一些。团队本地开发建议使用 `linux/arm64`，生产环境按服务器 CPU 架构构建对应镜像。

## 安全边界

本地 Docker 沙箱比直接在宿主机执行命令安全，但它不是强安全边界。接入业务系统时至少要做这些限制：

- 默认使用 `.network("none")`，除非业务明确需要访问网络。
- 设置内存和 CPU 限制，避免单个沙箱拖垮宿主机。
- 不要把 Docker socket 挂进沙箱。
- 谨慎使用 `BindMountEntry` 或额外 `-v` 参数，挂载宿主目录等于把该目录暴露给沙箱内命令。
- 对用户传入的命令做业务级白名单或审批，尤其是多人系统。
- 定期清理长时间未使用的容器和快照 tar。

## 当前可行性判断

| 能力 | 是否可通过 jar 直接复用 | 说明 |
|------|--------------------------|------|
| 创建本地 Docker 沙箱 | 可以 | 使用 `DockerSandboxClient#create(...)` + `Sandbox#start()` |
| 执行沙箱内命令 | 可以 | 使用 `Sandbox#exec(...)` |
| 保存工作区快照 | 可以 | 使用 `LocalSnapshotSpec` + `Sandbox#stop()`，或直接 `persistWorkspace()` |
| 从快照恢复 | 可以 | 保存 `SandboxState` JSON 后，使用 `deserializeState(...)` + `resume(...)` + `start()` |
| 关闭并删除沙箱 | 可以 | 使用 `Sandbox#shutdown()` 或 `Sandbox#close()` |
| 文件读写、搜索、上传下载 | 可以 | 直接 `exec` 自己实现，或手动使用 `SandboxBackedFilesystem` |
| 不使用 ReactAgent | 可以 | 直接使用 `SandboxClient` / `Sandbox`，不要创建 `HarnessAgent` 即可 |
| 作为独立沙箱 SDK | 部分具备 | 底层能力已具备，但建议业务侧再封装状态表、资源清理和异常策略 |

第一版建议采用“直接调用 `DockerSandboxClient` + 本地 `LocalSnapshotSpec` + 业务库保存 `SandboxState` JSON”的方案。这样接入成本最低，也不会把 ReactAgent、模型、工具调用链带进你的另一个项目。
