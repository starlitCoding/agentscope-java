# Agent Sandbox Kubernetes 本地部署与 Java 连接说明

本文记录在本地 Kubernetes 中部署 `kubernetes-sigs/agent-sandbox`，并让 AgentScope Java 的 Kubernetes sandbox 扩展直接调用沙箱的完整流程。

## 目标

本地环境需要具备以下能力：

1. Kubernetes 已安装 agent-sandbox 的 CRD 和 controller。
2. 集群中有可用的 `SandboxTemplate` 和 `SandboxWarmPool`。
3. 集群中有 `sandbox-router-svc`，用于把本地 Java 客户端请求转发到具体 sandbox runtime。
4. Java 代码能通过 `KubernetesSandboxClient` 创建 `SandboxClaim`，等待 `Sandbox` 就绪，并执行命令或读写文件。

## 关键概念

### CRD 与资源实例

CRD 是 Kubernetes 的“资源类型定义”。安装 CRD 后，Kubernetes 才认识新的资源类型。

本项目用到 4 类 agent-sandbox CRD：

- `SandboxClaim`
- `Sandbox`
- `SandboxTemplate`
- `SandboxWarmPool`

需要注意：安装 CRD 只表示 Kubernetes 认识这些类型，不表示已经创建了具体对象。

可以这样理解：

- CRD：数据库表结构。
- `SandboxTemplate` 对象：表里的一行数据，定义沙箱 Pod 怎么运行。
- `SandboxWarmPool` 对象：表里的一行数据，定义预热池引用哪个模板、保持几个可用沙箱。
- `SandboxClaim` 对象：Java 代码创建的申请单，表示要从某个 WarmPool 领取一个沙箱。
- `Sandbox` 对象：controller 根据 Claim、WarmPool 和 Template 实际创建出来的沙箱资源。

### Template、WarmPool、Claim、Sandbox 的关系

```text
SandboxTemplate
  定义沙箱镜像、容器端口、探针、资源限制等

SandboxWarmPool
  引用 SandboxTemplate，维护预热沙箱池

SandboxClaim
  Java 客户端创建，引用 WarmPool

Sandbox
  agent-sandbox controller 实际创建和管理的沙箱资源
```

## 当前本地基础安装

已经通过以下文件安装 agent-sandbox 1.0.1 的 CRD 和 controller：

```text
docs/scripts/agent-sandbox/version1.0.1/sandbox-with-extensions.yaml
```

该文件主要提供：

- `agent-sandbox-system` namespace。
- `SandboxClaim` / `Sandbox` / `SandboxTemplate` / `SandboxWarmPool` 的 CRD。
- `agent-sandbox-controller` 的 `Deployment`、`ServiceAccount`、`ClusterRole`、`ClusterRoleBinding` 等。

安装后可验证：

```bash
kubectl get crd sandboxclaims.extensions.agents.x-k8s.io \
  sandboxes.agents.x-k8s.io \
  sandboxtemplates.extensions.agents.x-k8s.io \
  sandboxwarmpools.extensions.agents.x-k8s.io

kubectl get deploy,pod,svc,endpoints -n agent-sandbox-system
```

如果 controller 是 `1/1`，pod 是 `Running`，说明控制面已经可用。

## 部署运行时、WarmPool 和 Router

本仓库新增了一个本地验证清单：

```text
docs/scripts/agent-sandbox/version1.0.1/agentscope-official-python-runtime.yaml
```

它包含：

- `Namespace`: `agentscope-sandbox`
- `Secret`: `sandbox-router-auth`
- `SandboxTemplate`: `python-runtime-template`
- `SandboxWarmPool`: `agentscope-sandbox`
- `Service`: `sandbox-router-svc`
- `Deployment`: `sandbox-router-deployment`

应用清单：

```bash
kubectl apply -f docs/scripts/agent-sandbox/version1.0.1/agentscope-official-python-runtime.yaml
```

检查资源：

```bash
kubectl get sandboxtemplate,sandboxwarmpool -n agentscope-sandbox
kubectl get deploy,pod,svc,endpoints -n agentscope-sandbox
```

期望看到：

- `sandboxwarmpool/agentscope-sandbox`
- `sandboxtemplate/python-runtime-template`
- `service/sandbox-router-svc`
- `endpoints/sandbox-router-svc` 有地址，例如 `192.168.194.6:8080`

## 官方 Runtime 镜像说明

当前清单使用的官方 runtime 镜像是：

```text
us-central1-docker.pkg.dev/k8s-staging-images/agent-sandbox/python-runtime-sandbox:latest-main
```

它是 agent-sandbox 官方提供的 Python runtime sandbox 镜像，已经包含沙箱 HTTP runtime 服务，能监听 `8888` 并响应 Java 客户端需要的接口。

Java 客户端依赖以下接口：

```text
POST /execute
POST /upload
GET  /download/...
GET  /list/...
GET  /exists/...
```

普通基础镜像不能直接作为 sandbox runtime 使用。例如只包含 JDK 和 Maven 的 `ubuntu:24.04-jdk17`，如果没有启动兼容这些接口的 HTTP 服务，Java 客户端仍然无法执行命令或传输文件。

因此：

- 先用官方 Python runtime 镜像跑通 agent-sandbox 链路。
- 后续如果必须在沙箱里使用 Java 17 和 Maven，需要基于官方 runtime 机制制作一个包含 JDK/Maven 的 runtime 镜像，或者找到已经实现相同 HTTP API 的 Java/Maven runtime 镜像。

## 端口关系

这里有两个端口，分别属于不同组件。

```text
Java 本地进程
  -> sandbox-router-svc:8080
  -> sandbox runtime pod:8888
```

### Router 端口 8080

`sandbox-router-svc` 暴露的是 router 服务，端口是 `8080`。

例如：

```text
endpoints/sandbox-router-svc = 192.168.194.6:8080
```

这个地址是 Kubernetes 集群内部地址，表示 Service 背后的 router pod 地址。它通常只在集群网络内部可达，不建议本地 Java 代码直接连接这个 Pod IP。

### Runtime 端口 8888

`8888` 是每个 sandbox runtime pod 内部的 HTTP 服务端口。

Java 配置里的：

```java
options.setServerPort(8888);
```

含义不是“连接 router 的端口”，而是告诉 router：

```text
请把请求转发到目标 sandbox pod 的 8888 端口
```

## Port Forward 是什么

本地 Java 进程不在 Kubernetes 集群内部，不能稳定依赖 `sandbox-router-svc` 的集群内地址。

`port-forward` 是一条临时隧道，把本机某个端口转发到 Kubernetes 里的 Pod 或 Service 端口。

手动命令示例：

```bash
kubectl -n agentscope-sandbox port-forward svc/sandbox-router-svc 8080:8080
```

含义是：

```text
本机 127.0.0.1:8080
  -> Kubernetes 中的 sandbox-router-svc:8080
```

但 AgentScope Java 当前默认不需要手动执行这条命令。它会通过 Fabric8 Kubernetes Client 自动建立类似隧道。

默认链路是：

```text
Java 本地进程
  -> 本机临时端口，例如 127.0.0.1:52731
  -> Fabric8 自动建立的 port-forward
  -> router pod:8080
  -> sandbox runtime pod:8888
```

## Java 连接方式

本地开发推荐不配置 `apiUrl`，也不配置 `gatewayName`，让框架走默认 local tunnel / port-forward。

示例配置：

```java
KubernetesSandboxClientOptions options = new KubernetesSandboxClientOptions();
options.setNamespace("agentscope-sandbox");
options.setWarmPoolName("agentscope-sandbox");
options.setWorkspaceRoot("/workspace");
options.setFileApiBaseDir("/workspace");
options.setServerPort(8888);
```

含义：

- `namespace`: `SandboxClaim`、`SandboxWarmPool`、`SandboxTemplate`、router 所在 namespace。
- `warmPoolName`: Java 创建 `SandboxClaim` 时引用的 WarmPool 名称。
- `workspaceRoot`: 框架认为沙箱内的工作区目录。
- `fileApiBaseDir`: 文件 API 使用的根目录。
- `serverPort`: sandbox runtime pod 内部端口，当前官方 runtime 是 `8888`。

创建时，框架大致做这些事：

```text
1. 创建 SandboxClaim，名称形如 as-sbx-...
2. SandboxClaim.spec.warmPoolRef.name = agentscope-sandbox
3. 等待 controller 给 SandboxClaim.status.sandbox.name 赋值
4. 等待对应 Sandbox Ready=True
5. 找到 sandbox-router-svc 背后的 router pod
6. 自动建立 port-forward 到 router pod:8080
7. 请求 router，并通过 header 告诉 router 目标 sandbox 和端口
8. router 转发到 sandbox runtime pod:8888
```

Java 请求 router 时会注入这些路由 header：

```text
X-Sandbox-ID
X-Sandbox-Namespace
X-Sandbox-Port
X-Sandbox-Pod-IP
```

## Router Token 模式

当前清单按更接近生产的方式开启 router token：

```yaml
- name: ALLOW_UNAUTHENTICATED_ROUTER
  value: "false"
- name: ROUTER_AUTH_TOKEN
  valueFrom:
    secretKeyRef:
      name: sandbox-router-auth
      key: auth-token
```

Secret 当前默认值是：

```text
change-me-local-router-token
```

生产或准生产环境应替换成随机强 token。

重要限制：当前 Java 客户端代码还没有看到对 router token 的 `Authorization` header 支持。也就是说，如果保持 token 模式开启，Java 请求 router 时需要补充：

```text
Authorization: Bearer <router-token>
```

否则创建 sandbox 可能成功，但执行命令或文件操作会被 router 返回 `401 Unauthorized`。

短期本地验证有两种选择：

1. 推荐后续修代码：给 `KubernetesSandboxClientOptions` 增加 router token 配置，并在 `SandboxConnector` 构造请求时加 `Authorization` header。
2. 仅用于快速功能验证：把 `ALLOW_UNAUTHENTICATED_ROUTER` 改成 `"true"`，临时关闭 router 鉴权。

## 验证命令

检查控制面：

```bash
kubectl get deploy,pod,svc,endpoints -n agent-sandbox-system
```

检查运行时池和 router：

```bash
kubectl get sandboxtemplate,sandboxwarmpool -n agentscope-sandbox
kubectl get deploy,pod,svc,endpoints -n agentscope-sandbox
```

如果大小写不确定，也可以分别执行：

```bash
kubectl get sandboxtemplates.extensions.agents.x-k8s.io -n agentscope-sandbox
kubectl get sandboxwarmpools.extensions.agents.x-k8s.io -n agentscope-sandbox
```

查看 WarmPool 是否有 ready 副本：

```bash
kubectl get sandboxwarmpools.extensions.agents.x-k8s.io -n agentscope-sandbox
```

查看沙箱相关资源：

```bash
kubectl get sandboxclaims.extensions.agents.x-k8s.io -n agentscope-sandbox
kubectl get sandboxes.agents.x-k8s.io -n agentscope-sandbox
kubectl get pod -n agentscope-sandbox
```

查看 router 日志：

```bash
kubectl logs -n agentscope-sandbox deploy/sandbox-router-deployment
```

查看 controller 日志：

```bash
kubectl logs -n agent-sandbox-system deploy/agent-sandbox-controller
```

## 常见问题

### 为什么已经安装 CRD，还说没有 WarmPool 和 Template

安装 CRD 只是注册资源类型。还需要创建具体的 `SandboxTemplate` 和 `SandboxWarmPool` 对象。

检查 CRD：

```bash
kubectl get crd sandboxwarmpools.extensions.agents.x-k8s.io
```

检查对象：

```bash
kubectl get sandboxwarmpools.extensions.agents.x-k8s.io -A
```

前者存在表示类型已安装；后者有数据才表示已经创建 WarmPool 实例。

### 为什么不能直接连接 endpoints/sandbox-router-svc 的 IP

例如：

```text
192.168.194.6:8080
```

这是集群内部 Pod endpoint。Pod 重启后 IP 会变化，本地进程也不一定能访问该网段。推荐让框架自动 port-forward，或者手动 port-forward 到 `127.0.0.1`。

### 为什么 Java 代码写 8888，而 router 是 8080

`8080` 是 router 的端口。`8888` 是 sandbox runtime 的端口。

Java 客户端会连到 router，router 再根据 `X-Sandbox-Port: 8888` 转发到目标 sandbox pod。

### 如果想直接使用 apiUrl 怎么配置

只有在你自己暴露了 router 的稳定地址时才配置 `apiUrl`。

例如手动转发：

```bash
kubectl -n agentscope-sandbox port-forward svc/sandbox-router-svc 8080:8080
```

Java 配置：

```java
options.setApiUrl("http://127.0.0.1:8080");
options.setServerPort(8888);
```

不配置 `apiUrl` 时，框架会自动 port-forward。

## 推荐下一步

1. 先用 `agentscope-official-python-runtime.yaml` 跑通完整链路。
2. 给 Java Kubernetes sandbox 客户端补 router token 支持。
3. 如果确实需要 JDK17/Maven，在官方 runtime 基础上制作包含 Java/Maven 的 runtime 镜像，并保持 `8888` HTTP API 契约不变。
