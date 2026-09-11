# Sandbox Service Configurable Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `agentscope-sandbox-service` 可以通过配置文件在 Docker 和 Kubernetes 沙箱后端之间切换，同时保持现有生命周期、命令执行和文件工具 API 的业务语义稳定。

**Architecture:** 新增 `SandboxProvider` 抽象作为后端边界，Docker 和 Kubernetes 分别实现 provider。`SandboxLifecycleService` 只依赖 provider，不再直接依赖 Docker 或 Kubernetes 具体类型；状态文件增加 `backend` 字段，响应结构用通用 `runtime` map 承载后端特定信息。

**Tech Stack:** Java 17、Spring Boot MVC、AgentScope Harness `Sandbox` API、AgentScope Kubernetes sandbox extension、Jackson、JUnit 5、AssertJ、Spring Boot Test。

**Spec:** `docs/superpowers/specs/2026-09-10-sandbox-service-configurable-backend-design.md`

## Global Constraints

- 默认后端必须保持 `docker`，避免破坏当前本地 demo 启动体验。
- `sandbox-service.backend` 只支持 `docker` 和 `kubernetes`。
- Controller 和文件工具 API 的路径、请求语义保持稳定。
- `SandboxLifecycleService` 不再 import Docker 或 Kubernetes 具体 client/options/state 类型。
- 同一个 `(userId, sessionId)` 的生命周期、命令执行和文件操作仍然串行。
- 状态 JSON 和快照继续保存在本地文件，不引入数据库或对象存储。
- 旧状态文件没有 `backend` 字段时按 `DOCKER` 兼容读取。
- 已有状态的 backend 和当前配置 backend 不一致时必须失败，不做跨后端恢复。
- 普通 Maven 测试不得依赖真实 Docker 或 Kubernetes 集群。
- 代码中的每个方法都需要写注释，说明用途、关键参数或重要行为。

---

## File Structure

- Modify: `agentscope-examples/agents/agentscope-sandbox-service/pom.xml`
  - 增加 `agentscope-extensions-sandbox-kubernetes` 依赖。
- Modify: `agentscope-examples/agents/agentscope-sandbox-service/src/main/resources/application.yml`
  - 增加 `sandbox-service.backend` 和 `sandbox-service.kubernetes.*` 默认配置。
- Modify: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/config/SandboxServiceProperties.java`
  - 增加 `SandboxBackendType backend` 和 `Kubernetes` nested config。
- Modify: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/config/SandboxServiceConfig.java`
  - 注册 provider、统一 ObjectMapper module、重接 `SandboxLifecycleService`。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxBackendType.java`
  - 定义后端枚举。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxProvider.java`
  - 定义生命周期服务使用的后端接口。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxRuntimeDescriptor.java`
  - 定义通用运行时描述。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/DockerSandboxProvider.java`
  - 封装现有 Docker 创建、恢复、序列化和状态描述逻辑。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/KubernetesSandboxProvider.java`
  - 接入 Kubernetes extension，封装 Kubernetes 创建、恢复、序列化和状态描述逻辑。
- Modify: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxRecord.java`
  - 增加 `backend` 字段并兼容旧 JSON。
- Modify: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/dto/SandboxStatusResponse.java`
  - 将 Docker 专属字段调整为 `backend` 和 `runtime`。
- Modify: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/error/SandboxServiceException.java`
  - 增加 `UNSUPPORTED_BACKEND` 和 `BACKEND_MISMATCH` 错误码及工厂方法。
- Modify: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxLifecycleService.java`
  - 改为依赖 `SandboxProvider`，实现 backend mismatch 校验。
- Modify: `agentscope-examples/agents/agentscope-sandbox-service/README.md`
  - 更新 Docker/Kubernetes 配置和运行说明。
- Test: `agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/config/SandboxServicePropertiesTest.java`
- Test: `agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/service/DockerSandboxProviderTest.java`
- Test: `agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/service/KubernetesSandboxProviderTest.java`
- Test: `agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/service/SandboxLifecycleServiceTest.java`
- Test: `agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/controller/SandboxControllerTest.java`

---

### Task 1: Dependencies And Config Binding

**Files:**
- Modify: `agentscope-examples/agents/agentscope-sandbox-service/pom.xml`
- Modify: `agentscope-examples/agents/agentscope-sandbox-service/src/main/resources/application.yml`
- Modify: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/config/SandboxServiceProperties.java`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxBackendType.java`
- Test: `agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/config/SandboxServicePropertiesTest.java`

**Interfaces:**
- Produces: `SandboxBackendType { DOCKER, KUBERNETES }`
- Produces: `SandboxServiceProperties#getBackend(): SandboxBackendType`
- Produces: `SandboxServiceProperties#getKubernetes(): SandboxServiceProperties.Kubernetes`
- Produces: Kubernetes config getters used later by `KubernetesSandboxProvider`

- [ ] **Step 1: Write failing config binding tests**

Update `SandboxServicePropertiesTest.java` so it verifies both default Docker binding and explicit Kubernetes binding:

```java
/** 验证默认 application.yml 会绑定 Docker 后端和 Kubernetes 默认配置。 */
@Test
void bindsDefaultBackendAndKubernetesDefaults() throws Exception {
    YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources()
            .addFirst(loader.load("test", new ClassPathResource("application.yml")).get(0));

    SandboxServiceProperties properties =
            Binder.get(environment)
                    .bind("sandbox-service", Bindable.of(SandboxServiceProperties.class))
                    .orElseThrow(
                            () -> new AssertionError("sandbox-service properties not bound"));

    assertThat(properties.getBackend()).isEqualTo(SandboxBackendType.DOCKER);
    assertThat(properties.getDocker().getImage()).isEqualTo("ubuntu:24.04");
    assertThat(properties.getKubernetes().getNamespace()).isEqualTo("default");
    assertThat(properties.getKubernetes().getWorkspaceRoot()).isEqualTo("/workspace");
    assertThat(properties.getKubernetes().getFileApiBaseDir()).isEqualTo("/workspace");
    assertThat(properties.getKubernetes().getServerPort()).isEqualTo(8888);
}

/** 验证配置文件可以切换到 Kubernetes 后端并绑定连接参数。 */
@Test
void bindsKubernetesBackendOverride() {
    MockEnvironment environment =
            new MockEnvironment()
                    .withProperty("sandbox-service.backend", "kubernetes")
                    .withProperty("sandbox-service.kubernetes.namespace", "agents")
                    .withProperty("sandbox-service.kubernetes.warm-pool-name", "agent-pool")
                    .withProperty("sandbox-service.kubernetes.api-url", "http://sandbox.local")
                    .withProperty("sandbox-service.kubernetes.gateway-name", "sandbox-gateway")
                    .withProperty("sandbox-service.kubernetes.gateway-namespace", "infra")
                    .withProperty("sandbox-service.kubernetes.gateway-scheme", "https")
                    .withProperty("sandbox-service.kubernetes.server-port", "8888")
                    .withProperty(
                            "sandbox-service.kubernetes.sandbox-ready-timeout-seconds", "90")
                    .withProperty("sandbox-service.kubernetes.request-timeout-seconds", "91")
                    .withProperty("sandbox-service.kubernetes.per-attempt-timeout-seconds", "20")
                    .withProperty("sandbox-service.kubernetes.port-forward-timeout-seconds", "11");

    SandboxServiceProperties properties =
            Binder.get(environment)
                    .bind("sandbox-service", Bindable.of(SandboxServiceProperties.class))
                    .orElseThrow(
                            () -> new AssertionError("sandbox-service properties not bound"));

    assertThat(properties.getBackend()).isEqualTo(SandboxBackendType.KUBERNETES);
    assertThat(properties.getKubernetes().getNamespace()).isEqualTo("agents");
    assertThat(properties.getKubernetes().getWarmPoolName()).isEqualTo("agent-pool");
    assertThat(properties.getKubernetes().getApiUrl()).isEqualTo("http://sandbox.local");
    assertThat(properties.getKubernetes().getGatewayName()).isEqualTo("sandbox-gateway");
    assertThat(properties.getKubernetes().getGatewayNamespace()).isEqualTo("infra");
    assertThat(properties.getKubernetes().getGatewayScheme()).isEqualTo("https");
    assertThat(properties.getKubernetes().getServerPort()).isEqualTo(8888);
    assertThat(properties.getKubernetes().getSandboxReadyTimeoutSeconds()).isEqualTo(90);
    assertThat(properties.getKubernetes().getRequestTimeoutSeconds()).isEqualTo(91);
    assertThat(properties.getKubernetes().getPerAttemptTimeoutSeconds()).isEqualTo(20);
    assertThat(properties.getKubernetes().getPortForwardTimeoutSeconds()).isEqualTo(11);
}
```

Add imports:

```java
import io.agentscope.sandboxservice.service.SandboxBackendType;
import org.springframework.mock.env.MockEnvironment;
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=SandboxServicePropertiesTest test
```

Expected: FAIL because `SandboxBackendType`, `getBackend()` and `getKubernetes()` do not exist.

- [ ] **Step 3: Add Kubernetes extension dependency**

In `agentscope-examples/agents/agentscope-sandbox-service/pom.xml`, add this dependency after `agentscope-harness`:

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-sandbox-kubernetes</artifactId>
</dependency>
```

- [ ] **Step 4: Add backend enum**

Create `SandboxBackendType.java`:

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.agentscope.sandboxservice.service;

/** 标识 sandbox-service 当前使用的沙箱后端类型。 */
public enum SandboxBackendType {
    DOCKER,
    KUBERNETES
}
```

- [ ] **Step 5: Extend application.yml**

Update `application.yml`:

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

- [ ] **Step 6: Extend properties class**

In `SandboxServiceProperties.java`, add:

```java
private SandboxBackendType backend = SandboxBackendType.DOCKER;
private Kubernetes kubernetes = new Kubernetes();

/** 返回当前启用的沙箱后端。 */
public SandboxBackendType getBackend() {
    return backend;
}

/** 设置当前启用的沙箱后端。 */
public void setBackend(SandboxBackendType backend) {
    this.backend = backend != null ? backend : SandboxBackendType.DOCKER;
}

/** 返回 Kubernetes 沙箱配置。 */
public Kubernetes getKubernetes() {
    return kubernetes;
}

/** 设置 Kubernetes 沙箱配置。 */
public void setKubernetes(Kubernetes kubernetes) {
    this.kubernetes = kubernetes != null ? kubernetes : new Kubernetes();
}
```

Add nested class:

```java
/** Kubernetes 沙箱默认参数。 */
public static class Kubernetes {
    private String namespace = "default";
    private String warmPoolName = "agentscope-sandbox";
    private String workspaceRoot = "/workspace";
    private String fileApiBaseDir = "/workspace";
    private String apiUrl;
    private String gatewayName;
    private String gatewayNamespace;
    private String gatewayScheme = "http";
    private Integer serverPort = 8888;
    private Long sandboxReadyTimeoutSeconds = 180L;
    private Long cleanupTimeoutSeconds = 30L;
    private Long requestTimeoutSeconds = 180L;
    private Long perAttemptTimeoutSeconds = 60L;
    private Long portForwardTimeoutSeconds = 30L;

    /** 返回 SandboxClaim 所在命名空间。 */
    public String getNamespace() {
        return namespace;
    }

    /** 设置 SandboxClaim 所在命名空间。 */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /** 返回 SandboxWarmPool 名称。 */
    public String getWarmPoolName() {
        return warmPoolName;
    }

    /** 设置 SandboxWarmPool 名称。 */
    public void setWarmPoolName(String warmPoolName) {
        this.warmPoolName = warmPoolName;
    }

    /** 返回沙箱内工作区根目录。 */
    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    /** 设置沙箱内工作区根目录。 */
    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    /** 返回运行时文件 API 根目录。 */
    public String getFileApiBaseDir() {
        return fileApiBaseDir;
    }

    /** 设置运行时文件 API 根目录。 */
    public void setFileApiBaseDir(String fileApiBaseDir) {
        this.fileApiBaseDir = fileApiBaseDir;
    }

    /** 返回直连运行时 API 的地址。 */
    public String getApiUrl() {
        return apiUrl;
    }

    /** 设置直连运行时 API 的地址。 */
    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    /** 返回 Gateway API 的 Gateway 名称。 */
    public String getGatewayName() {
        return gatewayName;
    }

    /** 设置 Gateway API 的 Gateway 名称。 */
    public void setGatewayName(String gatewayName) {
        this.gatewayName = gatewayName;
    }

    /** 返回 Gateway 所在命名空间。 */
    public String getGatewayNamespace() {
        return gatewayNamespace;
    }

    /** 设置 Gateway 所在命名空间。 */
    public void setGatewayNamespace(String gatewayNamespace) {
        this.gatewayNamespace = gatewayNamespace;
    }

    /** 返回 Gateway 访问协议。 */
    public String getGatewayScheme() {
        return gatewayScheme;
    }

    /** 设置 Gateway 访问协议。 */
    public void setGatewayScheme(String gatewayScheme) {
        this.gatewayScheme = gatewayScheme;
    }

    /** 返回运行时 HTTP 服务端口。 */
    public Integer getServerPort() {
        return serverPort;
    }

    /** 设置运行时 HTTP 服务端口。 */
    public void setServerPort(Integer serverPort) {
        this.serverPort = serverPort;
    }

    /** 返回等待 Sandbox ready 的超时时间。 */
    public Long getSandboxReadyTimeoutSeconds() {
        return sandboxReadyTimeoutSeconds;
    }

    /** 设置等待 Sandbox ready 的超时时间。 */
    public void setSandboxReadyTimeoutSeconds(Long sandboxReadyTimeoutSeconds) {
        this.sandboxReadyTimeoutSeconds = sandboxReadyTimeoutSeconds;
    }

    /** 返回清理 Kubernetes 沙箱资源的超时时间。 */
    public Long getCleanupTimeoutSeconds() {
        return cleanupTimeoutSeconds;
    }

    /** 设置清理 Kubernetes 沙箱资源的超时时间。 */
    public void setCleanupTimeoutSeconds(Long cleanupTimeoutSeconds) {
        this.cleanupTimeoutSeconds = cleanupTimeoutSeconds;
    }

    /** 返回运行时请求总超时时间。 */
    public Long getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    /** 设置运行时请求总超时时间。 */
    public void setRequestTimeoutSeconds(Long requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    /** 返回单次运行时请求尝试的超时时间。 */
    public Long getPerAttemptTimeoutSeconds() {
        return perAttemptTimeoutSeconds;
    }

    /** 设置单次运行时请求尝试的超时时间。 */
    public void setPerAttemptTimeoutSeconds(Long perAttemptTimeoutSeconds) {
        this.perAttemptTimeoutSeconds = perAttemptTimeoutSeconds;
    }

    /** 返回 port-forward 建立连接的超时时间。 */
    public Long getPortForwardTimeoutSeconds() {
        return portForwardTimeoutSeconds;
    }

    /** 设置 port-forward 建立连接的超时时间。 */
    public void setPortForwardTimeoutSeconds(Long portForwardTimeoutSeconds) {
        this.portForwardTimeoutSeconds = portForwardTimeoutSeconds;
    }
}
```

Add import:

```java
import io.agentscope.sandboxservice.service.SandboxBackendType;
```

- [ ] **Step 7: Run test to verify it passes**

Run:

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=SandboxServicePropertiesTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add agentscope-examples/agents/agentscope-sandbox-service/pom.xml \
  agentscope-examples/agents/agentscope-sandbox-service/src/main/resources/application.yml \
  agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/config/SandboxServiceProperties.java \
  agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxBackendType.java \
  agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/config/SandboxServicePropertiesTest.java
git commit -m "feat: add sandbox backend configuration"
```

---

### Task 2: Provider Interfaces And Status DTO Shape

**Files:**
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxProvider.java`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxRuntimeDescriptor.java`
- Modify: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/dto/SandboxStatusResponse.java`
- Test: `agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/service/SandboxRuntimeDescriptorTest.java`

**Interfaces:**
- Consumes: `SandboxBackendType`
- Produces: `SandboxProvider`
- Produces: `SandboxRuntimeDescriptor`
- Produces: `SandboxStatusResponse(..., SandboxBackendType backend, ..., Map<String, Object> runtime, ...)`

- [ ] **Step 1: Write failing runtime descriptor test**

Create `SandboxRuntimeDescriptorTest.java`:

```java
package io.agentscope.sandboxservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SandboxRuntimeDescriptorTest {

    /** 验证运行时描述对象能承载后端类型、工作区和后端特定属性。 */
    @Test
    void describesBackendSpecificRuntimeAttributes() {
        SandboxRuntimeDescriptor descriptor =
                new SandboxRuntimeDescriptor(
                        SandboxBackendType.KUBERNETES,
                        "/workspace",
                        Map.of("namespace", "agents", "podName", "sandbox-0"));

        assertThat(descriptor.backend()).isEqualTo(SandboxBackendType.KUBERNETES);
        assertThat(descriptor.workspaceRoot()).isEqualTo("/workspace");
        assertThat(descriptor.attributes())
                .containsEntry("namespace", "agents")
                .containsEntry("podName", "sandbox-0");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=SandboxRuntimeDescriptorTest test
```

Expected: FAIL because `SandboxRuntimeDescriptor` does not exist.

- [ ] **Step 3: Create provider interface**

Create `SandboxProvider.java`:

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.agentscope.sandboxservice.service;

import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;

/** 隔离具体沙箱后端差异，为生命周期服务提供统一创建、恢复和状态序列化能力。 */
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

- [ ] **Step 4: Create runtime descriptor record**

Create `SandboxRuntimeDescriptor.java`:

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.agentscope.sandboxservice.service;

import java.util.Map;

/** 描述一个运行中沙箱的通用信息和后端特定属性。 */
public record SandboxRuntimeDescriptor(
        SandboxBackendType backend, String workspaceRoot, Map<String, Object> attributes) {

    /** 创建描述对象时复制属性表，避免调用方修改内部状态。 */
    public SandboxRuntimeDescriptor {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
```

- [ ] **Step 5: Update status response shape**

Replace `SandboxStatusResponse.java` with:

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.agentscope.sandboxservice.dto;

import io.agentscope.sandboxservice.service.SandboxBackendType;
import io.agentscope.sandboxservice.service.SandboxLifecycleStatus;
import java.time.Instant;
import java.util.Map;

/** 返回沙箱生命周期状态和当前后端的运行时信息。 */
public record SandboxStatusResponse(
        String userId,
        String sessionId,
        SandboxBackendType backend,
        SandboxLifecycleStatus status,
        boolean running,
        boolean snapshotRestorable,
        String workspaceRoot,
        Map<String, Object> runtime,
        Instant createdAt,
        Instant updatedAt) {

    /** 创建响应时复制运行时属性，避免响应对象被外部修改。 */
    public SandboxStatusResponse {
        runtime = runtime == null ? Map.of() : Map.copyOf(runtime);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run:

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=SandboxRuntimeDescriptorTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxProvider.java \
  agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxRuntimeDescriptor.java \
  agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/dto/SandboxStatusResponse.java \
  agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/service/SandboxRuntimeDescriptorTest.java
git commit -m "feat: define sandbox provider boundary"
```

---

### Task 3: Docker Sandbox Provider

**Files:**
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/DockerSandboxProvider.java`
- Test: `agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/service/DockerSandboxProviderTest.java`

**Interfaces:**
- Consumes: `SandboxProvider`
- Consumes: `SandboxRuntimeDescriptor`
- Consumes: `SandboxServiceProperties#getDocker()`
- Produces: Docker-backed implementation used by config in Task 5

- [ ] **Step 1: Write failing Docker provider tests**

Create `DockerSandboxProviderTest.java`:

```java
package io.agentscope.sandboxservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClient;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClientOptions;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxState;
import io.agentscope.harness.agent.sandbox.snapshot.NoopSnapshotSpec;
import io.agentscope.sandboxservice.config.SandboxServiceProperties;
import org.junit.jupiter.api.Test;

class DockerSandboxProviderTest {

    /** 验证 Docker provider 会用配置创建 Docker options 和工作区根目录。 */
    @Test
    void createsSandboxWithConfiguredDockerOptions() {
        SandboxServiceProperties properties = new SandboxServiceProperties();
        properties.getDocker().setImage("ubuntu:22.04");
        properties.getDocker().setWorkspaceRoot("/work");
        properties.getDocker().setNetwork("none");
        properties.getDocker().setMemorySizeBytes(512L);
        properties.getDocker().setCpuCount(1L);
        CapturingDockerSandboxClient client = new CapturingDockerSandboxClient();
        DockerSandboxProvider provider = new DockerSandboxProvider(properties, client);

        provider.create(SandboxKey.of("alice", "conv-1"), new NoopSnapshotSpec());

        assertThat(provider.backend()).isEqualTo(SandboxBackendType.DOCKER);
        assertThat(provider.workspaceRoot()).isEqualTo("/work");
        assertThat(client.options.getImage()).isEqualTo("ubuntu:22.04");
        assertThat(client.options.getWorkspaceRoot()).isEqualTo("/work");
        assertThat(client.options.getNetwork()).isEqualTo("none");
        assertThat(client.options.getMemorySizeBytes()).isEqualTo(512L);
        assertThat(client.options.getCpuCount()).isEqualTo(1L);
        assertThat(client.workspaceSpec.getRoot()).isEqualTo("/work");
    }

    /** 验证 Docker provider 能从 DockerSandboxState 提取容器运行信息。 */
    @Test
    void describesDockerRuntimeState() {
        SandboxServiceProperties properties = new SandboxServiceProperties();
        DockerSandboxProvider provider =
                new DockerSandboxProvider(properties, new CapturingDockerSandboxClient());
        DockerSandboxState state = new DockerSandboxState();
        state.setContainerId("cid-1");
        state.setContainerName("agentscope-sandbox-1");
        state.setWorkspaceRoot("/workspace");

        SandboxRuntimeDescriptor descriptor = provider.describe(new StateOnlySandbox(state));

        assertThat(descriptor.backend()).isEqualTo(SandboxBackendType.DOCKER);
        assertThat(descriptor.workspaceRoot()).isEqualTo("/workspace");
        assertThat(descriptor.attributes())
                .containsEntry("containerId", "cid-1")
                .containsEntry("containerName", "agentscope-sandbox-1");
    }

    static class CapturingDockerSandboxClient extends DockerSandboxClient {
        WorkspaceSpec workspaceSpec;
        DockerSandboxClientOptions options;

        /** 记录创建参数并返回最小沙箱对象，避免测试依赖真实 Docker。 */
        @Override
        public Sandbox create(
                WorkspaceSpec workspaceSpec,
                io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec snapshotSpec,
                DockerSandboxClientOptions options) {
            this.workspaceSpec = workspaceSpec;
            this.options = options;
            return new StateOnlySandbox(new DockerSandboxState());
        }
    }

    record StateOnlySandbox(DockerSandboxState state) implements Sandbox {
        /** 测试沙箱启动为空操作。 */
        @Override
        public void start() {}

        /** 测试沙箱停止为空操作。 */
        @Override
        public void stop() {}

        /** 测试沙箱关闭为空操作。 */
        @Override
        public void close() {}

        /** 测试沙箱始终视为未运行。 */
        @Override
        public boolean isRunning() {
            return false;
        }

        /** 返回测试状态对象。 */
        @Override
        public DockerSandboxState getState() {
            return state;
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=DockerSandboxProviderTest test
```

Expected: FAIL because `DockerSandboxProvider` does not exist.

- [ ] **Step 3: Implement Docker provider**

Create `DockerSandboxProvider.java`:

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.agentscope.sandboxservice.service;

import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxState;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClient;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClientOptions;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxState;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import io.agentscope.sandboxservice.config.SandboxServiceProperties;
import java.util.LinkedHashMap;
import java.util.Map;

/** Docker 沙箱后端适配器，封装 Docker client、options 和状态描述逻辑。 */
public class DockerSandboxProvider implements SandboxProvider {

    private final SandboxServiceProperties properties;
    private final DockerSandboxClient client;

    /** 创建 Docker provider，并注入配置和 Docker 沙箱客户端。 */
    public DockerSandboxProvider(SandboxServiceProperties properties, DockerSandboxClient client) {
        this.properties = properties;
        this.client = client;
    }

    /** 返回 Docker 后端类型。 */
    @Override
    public SandboxBackendType backend() {
        return SandboxBackendType.DOCKER;
    }

    /** 使用当前 Docker 配置创建新沙箱。 */
    @Override
    public Sandbox create(SandboxKey key, SandboxSnapshotSpec snapshotSpec) {
        WorkspaceSpec workspaceSpec = new WorkspaceSpec();
        workspaceSpec.setRoot(workspaceRoot());
        return client.create(workspaceSpec, snapshotSpec, dockerOptions());
    }

    /** 从 Docker 状态 JSON 恢复沙箱。 */
    @Override
    public Sandbox resume(String sandboxStateJson) {
        return client.resume(client.deserializeState(sandboxStateJson));
    }

    /** 使用 Docker client 序列化沙箱状态。 */
    @Override
    public String serializeState(Sandbox sandbox) {
        return client.serializeState(sandbox.getState());
    }

    /** 返回 Docker 容器内工作区根目录。 */
    @Override
    public String workspaceRoot() {
        return properties.getDocker().getWorkspaceRoot();
    }

    /** 从 Docker 状态提取容器 ID、容器名和工作区根目录。 */
    @Override
    public SandboxRuntimeDescriptor describe(Sandbox sandbox) {
        String root = workspaceRoot();
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (sandbox != null) {
            SandboxState state = sandbox.getState();
            if (state instanceof DockerSandboxState dockerState) {
                if (dockerState.getWorkspaceRoot() != null) {
                    root = dockerState.getWorkspaceRoot();
                }
                putIfPresent(attributes, "containerId", dockerState.getContainerId());
                putIfPresent(attributes, "containerName", dockerState.getContainerName());
            }
        }
        return new SandboxRuntimeDescriptor(backend(), root, attributes);
    }

    /** 根据配置组装 Docker 创建参数。 */
    private DockerSandboxClientOptions dockerOptions() {
        SandboxServiceProperties.Docker docker = properties.getDocker();
        DockerSandboxClientOptions options = new DockerSandboxClientOptions();
        options.setImage(docker.getImage());
        options.setWorkspaceRoot(docker.getWorkspaceRoot());
        options.setNetwork(docker.getNetwork());
        options.setMemorySizeBytes(docker.getMemorySizeBytes());
        options.setCpuCount(docker.getCpuCount());
        return options;
    }

    /** 在值非空时写入运行时属性。 */
    private static void putIfPresent(Map<String, Object> attributes, String key, Object value) {
        if (value != null) {
            attributes.put(key, value);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=DockerSandboxProviderTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/DockerSandboxProvider.java \
  agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/service/DockerSandboxProviderTest.java
git commit -m "feat: add docker sandbox provider"
```

---

### Task 4: Kubernetes Sandbox Provider

**Files:**
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/KubernetesSandboxProvider.java`
- Test: `agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/service/KubernetesSandboxProviderTest.java`

**Interfaces:**
- Consumes: `SandboxProvider`
- Consumes: `SandboxServiceProperties#getKubernetes()`
- Produces: Kubernetes-backed implementation used by config in Task 5

- [ ] **Step 1: Write failing Kubernetes provider tests**

Create `KubernetesSandboxProviderTest.java`:

```java
package io.agentscope.sandboxservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.extensions.sandbox.kubernetes.KubernetesSandboxClient;
import io.agentscope.extensions.sandbox.kubernetes.KubernetesSandboxClientOptions;
import io.agentscope.extensions.sandbox.kubernetes.KubernetesSandboxState;
import io.agentscope.extensions.sandbox.kubernetes.client.config.DirectConnectionConfig;
import io.agentscope.extensions.sandbox.kubernetes.client.config.GatewayConnectionConfig;
import io.agentscope.extensions.sandbox.kubernetes.client.config.LocalTunnelConnectionConfig;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.snapshot.NoopSnapshotSpec;
import io.agentscope.sandboxservice.config.SandboxServiceProperties;
import org.junit.jupiter.api.Test;

class KubernetesSandboxProviderTest {

    /** 验证 Kubernetes provider 会用配置创建 Kubernetes options 和工作区根目录。 */
    @Test
    void createsSandboxWithConfiguredKubernetesOptions() {
        SandboxServiceProperties properties = new SandboxServiceProperties();
        properties.getKubernetes().setNamespace("agents");
        properties.getKubernetes().setWarmPoolName("agent-pool");
        properties.getKubernetes().setWorkspaceRoot("/work");
        properties.getKubernetes().setFileApiBaseDir("/work");
        properties.getKubernetes().setServerPort(9999);
        CapturingKubernetesSandboxClient client = new CapturingKubernetesSandboxClient();
        KubernetesSandboxProvider provider = new KubernetesSandboxProvider(properties, client);

        provider.create(SandboxKey.of("alice", "conv-1"), new NoopSnapshotSpec());

        assertThat(provider.backend()).isEqualTo(SandboxBackendType.KUBERNETES);
        assertThat(provider.workspaceRoot()).isEqualTo("/work");
        assertThat(client.options.getNamespace()).isEqualTo("agents");
        assertThat(client.options.getWarmPoolName()).isEqualTo("agent-pool");
        assertThat(client.options.getWorkspaceRoot()).isEqualTo("/work");
        assertThat(client.options.getFileApiBaseDir()).isEqualTo("/work");
        assertThat(client.options.getServerPort()).isEqualTo(9999);
        assertThat(client.workspaceSpec.getRoot()).isEqualTo("/work");
    }

    /** 验证 apiUrl 优先生成为 direct connection 配置。 */
    @Test
    void directConnectionWinsWhenApiUrlConfigured() {
        KubernetesSandboxClientOptions options = new KubernetesSandboxClientOptions();
        options.setApiUrl("http://sandbox-runtime:8888");

        assertThat(KubernetesSandboxClient.toConnectionConfig(options))
                .isInstanceOf(DirectConnectionConfig.class);
    }

    /** 验证未配置 apiUrl 但配置 gatewayName 时使用 gateway connection。 */
    @Test
    void gatewayConnectionUsedWhenGatewayNameConfigured() {
        KubernetesSandboxClientOptions options = new KubernetesSandboxClientOptions();
        options.setNamespace("agents");
        options.setGatewayName("sandbox-gateway");

        assertThat(KubernetesSandboxClient.toConnectionConfig(options))
                .isInstanceOf(GatewayConnectionConfig.class);
    }

    /** 验证未配置 apiUrl 和 gatewayName 时使用本地 port-forward 模式。 */
    @Test
    void localTunnelConnectionUsedByDefault() {
        KubernetesSandboxClientOptions options = new KubernetesSandboxClientOptions();
        options.setNamespace("agents");

        assertThat(KubernetesSandboxClient.toConnectionConfig(options))
                .isInstanceOf(LocalTunnelConnectionConfig.class);
    }

    /** 验证 Kubernetes provider 能从 KubernetesSandboxState 提取运行信息。 */
    @Test
    void describesKubernetesRuntimeState() {
        SandboxServiceProperties properties = new SandboxServiceProperties();
        KubernetesSandboxProvider provider =
                new KubernetesSandboxProvider(properties, new CapturingKubernetesSandboxClient());
        KubernetesSandboxState state = new KubernetesSandboxState();
        state.setNamespace("agents");
        state.setClaimName("as-sbx-1");
        state.setSandboxName("sandbox-1");
        state.setWarmPoolName("agent-pool");
        state.setPodName("sandbox-1-pod");
        state.setPodIP("10.0.0.1");
        state.setWorkspaceRoot("/workspace");

        SandboxRuntimeDescriptor descriptor = provider.describe(new StateOnlySandbox(state));

        assertThat(descriptor.backend()).isEqualTo(SandboxBackendType.KUBERNETES);
        assertThat(descriptor.workspaceRoot()).isEqualTo("/workspace");
        assertThat(descriptor.attributes())
                .containsEntry("namespace", "agents")
                .containsEntry("claimName", "as-sbx-1")
                .containsEntry("sandboxName", "sandbox-1")
                .containsEntry("warmPoolName", "agent-pool")
                .containsEntry("podName", "sandbox-1-pod")
                .containsEntry("podIP", "10.0.0.1");
    }

    static class CapturingKubernetesSandboxClient extends KubernetesSandboxClient {
        WorkspaceSpec workspaceSpec;
        KubernetesSandboxClientOptions options;

        /** 记录创建参数并返回最小沙箱对象，避免测试依赖真实 Kubernetes。 */
        @Override
        public Sandbox create(
                WorkspaceSpec workspaceSpec,
                io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec snapshotSpec,
                KubernetesSandboxClientOptions options) {
            this.workspaceSpec = workspaceSpec;
            this.options = options;
            return new StateOnlySandbox(new KubernetesSandboxState());
        }
    }

    record StateOnlySandbox(KubernetesSandboxState state) implements Sandbox {
        /** 测试沙箱启动为空操作。 */
        @Override
        public void start() {}

        /** 测试沙箱停止为空操作。 */
        @Override
        public void stop() {}

        /** 测试沙箱关闭为空操作。 */
        @Override
        public void close() {}

        /** 测试沙箱始终视为未运行。 */
        @Override
        public boolean isRunning() {
            return false;
        }

        /** 返回测试状态对象。 */
        @Override
        public KubernetesSandboxState getState() {
            return state;
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=KubernetesSandboxProviderTest test
```

Expected: FAIL because `KubernetesSandboxProvider` does not exist.

- [ ] **Step 3: Implement Kubernetes provider**

Create `KubernetesSandboxProvider.java`:

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.agentscope.sandboxservice.service;

import io.agentscope.extensions.sandbox.kubernetes.KubernetesSandboxClient;
import io.agentscope.extensions.sandbox.kubernetes.KubernetesSandboxClientOptions;
import io.agentscope.extensions.sandbox.kubernetes.KubernetesSandboxState;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxState;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import io.agentscope.sandboxservice.config.SandboxServiceProperties;
import java.util.LinkedHashMap;
import java.util.Map;

/** Kubernetes 沙箱后端适配器，封装 agent-sandbox claim 创建、恢复和状态描述逻辑。 */
public class KubernetesSandboxProvider implements SandboxProvider {

    private final SandboxServiceProperties properties;
    private final KubernetesSandboxClient client;

    /** 创建 Kubernetes provider，并注入配置和 Kubernetes 沙箱客户端。 */
    public KubernetesSandboxProvider(
            SandboxServiceProperties properties, KubernetesSandboxClient client) {
        this.properties = properties;
        this.client = client;
    }

    /** 返回 Kubernetes 后端类型。 */
    @Override
    public SandboxBackendType backend() {
        return SandboxBackendType.KUBERNETES;
    }

    /** 使用当前 Kubernetes 配置创建新沙箱。 */
    @Override
    public Sandbox create(SandboxKey key, SandboxSnapshotSpec snapshotSpec) {
        WorkspaceSpec workspaceSpec = new WorkspaceSpec();
        workspaceSpec.setRoot(workspaceRoot());
        return client.create(workspaceSpec, snapshotSpec, kubernetesOptions());
    }

    /** 从 Kubernetes 状态 JSON 恢复沙箱。 */
    @Override
    public Sandbox resume(String sandboxStateJson) {
        return client.resume(client.deserializeState(sandboxStateJson));
    }

    /** 使用 Kubernetes client 序列化沙箱状态。 */
    @Override
    public String serializeState(Sandbox sandbox) {
        return client.serializeState(sandbox.getState());
    }

    /** 返回 Kubernetes 沙箱内工作区根目录。 */
    @Override
    public String workspaceRoot() {
        return properties.getKubernetes().getWorkspaceRoot();
    }

    /** 从 Kubernetes 状态提取 claim、sandbox、pod 和工作区根目录。 */
    @Override
    public SandboxRuntimeDescriptor describe(Sandbox sandbox) {
        String root = workspaceRoot();
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (sandbox != null) {
            SandboxState state = sandbox.getState();
            if (state instanceof KubernetesSandboxState k8sState) {
                if (k8sState.getWorkspaceRoot() != null) {
                    root = k8sState.getWorkspaceRoot();
                }
                putIfPresent(attributes, "namespace", k8sState.getNamespace());
                putIfPresent(attributes, "claimName", k8sState.getClaimName());
                putIfPresent(attributes, "sandboxName", k8sState.getSandboxName());
                putIfPresent(attributes, "warmPoolName", k8sState.getWarmPoolName());
                putIfPresent(attributes, "podName", k8sState.getPodName());
                putIfPresent(attributes, "podIP", k8sState.getPodIP());
            }
        }
        return new SandboxRuntimeDescriptor(backend(), root, attributes);
    }

    /** 根据配置组装 Kubernetes 创建参数。 */
    private KubernetesSandboxClientOptions kubernetesOptions() {
        SandboxServiceProperties.Kubernetes kubernetes = properties.getKubernetes();
        KubernetesSandboxClientOptions options = new KubernetesSandboxClientOptions();
        options.setNamespace(kubernetes.getNamespace());
        options.setWarmPoolName(kubernetes.getWarmPoolName());
        options.setWorkspaceRoot(kubernetes.getWorkspaceRoot());
        options.setFileApiBaseDir(kubernetes.getFileApiBaseDir());
        options.setApiUrl(kubernetes.getApiUrl());
        options.setGatewayName(kubernetes.getGatewayName());
        options.setGatewayNamespace(kubernetes.getGatewayNamespace());
        options.setGatewayScheme(kubernetes.getGatewayScheme());
        if (kubernetes.getServerPort() != null) {
            options.setServerPort(kubernetes.getServerPort());
        }
        if (kubernetes.getSandboxReadyTimeoutSeconds() != null) {
            options.setSandboxReadyTimeoutSeconds(kubernetes.getSandboxReadyTimeoutSeconds());
        }
        if (kubernetes.getCleanupTimeoutSeconds() != null) {
            options.setCleanupTimeoutSeconds(kubernetes.getCleanupTimeoutSeconds());
        }
        if (kubernetes.getRequestTimeoutSeconds() != null) {
            options.setRequestTimeoutSeconds(kubernetes.getRequestTimeoutSeconds());
        }
        if (kubernetes.getPerAttemptTimeoutSeconds() != null) {
            options.setPerAttemptTimeoutSeconds(kubernetes.getPerAttemptTimeoutSeconds());
        }
        if (kubernetes.getPortForwardTimeoutSeconds() != null) {
            options.setPortForwardTimeoutSeconds(kubernetes.getPortForwardTimeoutSeconds());
        }
        return options;
    }

    /** 在值非空时写入运行时属性。 */
    private static void putIfPresent(Map<String, Object> attributes, String key, Object value) {
        if (value != null) {
            attributes.put(key, value);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=KubernetesSandboxProviderTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/KubernetesSandboxProvider.java \
  agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/service/KubernetesSandboxProviderTest.java
git commit -m "feat: add kubernetes sandbox provider"
```

---

### Task 5: State Backend Compatibility And Lifecycle Refactor

**Files:**
- Modify: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxRecord.java`
- Modify: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/error/SandboxServiceException.java`
- Modify: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxLifecycleService.java`
- Modify: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/config/SandboxServiceConfig.java`
- Test: `agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/service/SandboxLifecycleServiceTest.java`
- Test: `agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/service/FileSandboxStateRepositoryTest.java`

**Interfaces:**
- Consumes: `SandboxProvider#create(...)`
- Consumes: `SandboxProvider#resume(...)`
- Consumes: `SandboxProvider#serializeState(...)`
- Consumes: `SandboxProvider#describe(...)`
- Produces: backend-aware `SandboxRecord`
- Produces: Docker/Kubernetes-independent `SandboxLifecycleService`

- [ ] **Step 1: Write failing lifecycle tests using fake provider**

Replace fake Docker client usage in `SandboxLifecycleServiceTest.java` with this fake provider setup:

```java
private SandboxLifecycleService newService(
        InMemoryRepository repository, FakeSandboxProvider provider) {
    SandboxServiceProperties properties = new SandboxServiceProperties();
    return new SandboxLifecycleService(
            provider,
            new NoopSnapshotSpec(),
            repository,
            properties,
            new SandboxOperationLockRegistry());
}

static class FakeSandboxProvider implements SandboxProvider {
    int createCalls;
    int resumeCalls;
    FakeSandbox latestSandbox;
    SandboxBackendType backend = SandboxBackendType.DOCKER;

    /** 返回测试用后端类型。 */
    @Override
    public SandboxBackendType backend() {
        return backend;
    }

    /** 创建测试沙箱并记录调用次数。 */
    @Override
    public Sandbox create(SandboxKey key, SandboxSnapshotSpec snapshotSpec) {
        createCalls++;
        latestSandbox = new FakeSandbox();
        return latestSandbox;
    }

    /** 恢复测试沙箱并记录调用次数。 */
    @Override
    public Sandbox resume(String sandboxStateJson) {
        resumeCalls++;
        latestSandbox = new FakeSandbox();
        return latestSandbox;
    }

    /** 返回固定状态 JSON，便于断言仓库写入。 */
    @Override
    public String serializeState(Sandbox sandbox) {
        return "state-json";
    }

    /** 返回测试工作区根目录。 */
    @Override
    public String workspaceRoot() {
        return "/workspace";
    }

    /** 返回测试运行时描述。 */
    @Override
    public SandboxRuntimeDescriptor describe(Sandbox sandbox) {
        return new SandboxRuntimeDescriptor(
                backend, "/workspace", Map.of("runtimeId", "fake-runtime"));
    }
}
```

Add test for backend persistence:

```java
/** 验证保存状态时会写入当前 provider 后端类型。 */
@Test
void persistsCurrentBackendInRecord() {
    InMemoryRepository repository = new InMemoryRepository();
    FakeSandboxProvider provider = new FakeSandboxProvider();
    SandboxLifecycleService service = newService(repository, provider);

    service.start(SandboxKey.of("alice", "conv-1"));

    assertThat(repository.record.orElseThrow().backend()).isEqualTo(SandboxBackendType.DOCKER);
}
```

Add test for backend mismatch:

```java
/** 验证已有状态后端和当前 provider 不一致时拒绝恢复。 */
@Test
void rejectsBackendMismatchWhenStartingExistingRecord() {
    InMemoryRepository repository = new InMemoryRepository();
    repository.record =
            Optional.of(
                    new SandboxRecord(
                            "alice",
                            "conv-1",
                            SandboxBackendType.DOCKER,
                            SandboxLifecycleStatus.STOPPED,
                            "state-json",
                            Instant.now(),
                            Instant.now()));
    FakeSandboxProvider provider = new FakeSandboxProvider();
    provider.backend = SandboxBackendType.KUBERNETES;
    SandboxLifecycleService service = newService(repository, provider);

    assertThatThrownBy(() -> service.start(SandboxKey.of("alice", "conv-1")))
            .isInstanceOf(SandboxServiceException.class)
            .hasMessageContaining("backend mismatch");
}
```

Add imports:

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Map;
```

- [ ] **Step 2: Write failing repository compatibility test**

In `FileSandboxStateRepositoryTest.java`, add:

```java
/** 验证旧状态 JSON 没有 backend 字段时按 Docker 后端兼容读取。 */
@Test
void readsLegacyRecordWithoutBackendAsDocker() throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    Path stateDir = tempDir.resolve("state");
    FileSandboxStateRepository repository = new FileSandboxStateRepository(stateDir, mapper);
    SandboxKey key = SandboxKey.of("alice", "conv-legacy");
    Path userDir =
            stateDir.resolve(
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString("alice".getBytes(StandardCharsets.UTF_8)));
    Files.createDirectories(userDir);
    Path stateFile =
            userDir.resolve(
                    Base64.getUrlEncoder()
                                    .withoutPadding()
                                    .encodeToString("conv-legacy".getBytes(StandardCharsets.UTF_8))
                            + ".json");
    Files.writeString(
            stateFile,
            """
            {
              "userId": "alice",
              "sessionId": "conv-legacy",
              "status": "STOPPED",
              "sandboxStateJson": "state-json",
              "createdAt": "2026-09-10T10:00:00Z",
              "updatedAt": "2026-09-10T10:05:00Z"
            }
            """);

    SandboxRecord record = repository.find(key).orElseThrow();

    assertThat(record.backend()).isEqualTo(SandboxBackendType.DOCKER);
}
```

Add imports:

```java
import java.nio.charset.StandardCharsets;
import java.util.Base64;
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=SandboxLifecycleServiceTest,FileSandboxStateRepositoryTest test
```

Expected: FAIL because `SandboxRecord` does not have `backend`, `SandboxLifecycleService` constructor still expects Docker client, and backend mismatch handling does not exist.

- [ ] **Step 4: Update SandboxRecord**

Replace `SandboxRecord.java` with a record that has a compact constructor:

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.agentscope.sandboxservice.service;

import java.time.Instant;

/** 服务侧持久化状态，包装业务 key、后端类型、生命周期状态和 Harness 沙箱状态 JSON。 */
public record SandboxRecord(
        String userId,
        String sessionId,
        SandboxBackendType backend,
        SandboxLifecycleStatus status,
        String sandboxStateJson,
        Instant createdAt,
        Instant updatedAt) {

    /** 兼容旧 JSON 缺少 backend 字段的 Docker 状态文件。 */
    public SandboxRecord {
        backend = backend != null ? backend : SandboxBackendType.DOCKER;
    }
}
```

Update all `new SandboxRecord(...)` call sites to include `SandboxBackendType.DOCKER` where tests construct legacy current-shape records, and later use `provider.backend()` in service production code.

- [ ] **Step 5: Extend service exception**

Add enum values in `SandboxServiceException.Code`:

```java
UNSUPPORTED_BACKEND,
BACKEND_MISMATCH,
```

Add factory method:

```java
/** 创建后端不匹配异常，阻止同一 session 跨后端恢复。 */
public static SandboxServiceException backendMismatch(
        SandboxKey key, SandboxBackendType stored, SandboxBackendType configured) {
    return new SandboxServiceException(
            Code.BACKEND_MISMATCH,
            "Sandbox backend mismatch",
            "Session "
                    + key.userId()
                    + "/"
                    + key.sessionId()
                    + " was created with "
                    + stored
                    + " but current backend is "
                    + configured,
            key.userId(),
            key.sessionId());
}
```

- [ ] **Step 6: Refactor lifecycle service constructor and fields**

In `SandboxLifecycleService.java`, replace Docker fields:

```java
private final SandboxClient<DockerSandboxClientOptions> client;
```

with:

```java
private final SandboxProvider provider;
```

Replace constructor:

```java
/** 创建生命周期服务，依赖沙箱 provider、快照策略、状态仓库和锁注册表。 */
public SandboxLifecycleService(
        SandboxProvider provider,
        SandboxSnapshotSpec snapshotSpec,
        SandboxStateRepository repository,
        SandboxServiceProperties properties,
        SandboxOperationLockRegistry lockRegistry) {
    this.provider = provider;
    this.snapshotSpec = snapshotSpec;
    this.repository = repository;
    this.properties = properties;
    this.lockRegistry = lockRegistry;
}
```

Remove imports for:

```java
io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClient;
io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClientOptions;
io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxState;
```

- [ ] **Step 7: Refactor start/resume/create**

In `doStart(...)`, replace:

```java
sandbox = client.resume(client.deserializeState(record.get().sandboxStateJson()));
```

with:

```java
SandboxRecord stored = record.get();
assertBackendMatches(key, stored);
sandbox = provider.resume(stored.sandboxStateJson());
```

Replace:

```java
sandbox = createSandbox(key);
```

with:

```java
sandbox = provider.create(key, snapshotSpec);
```

Add helper:

```java
/** 校验持久化状态的后端类型与当前配置一致，避免跨后端错误恢复。 */
private void assertBackendMatches(SandboxKey key, SandboxRecord record) {
    if (record != null && record.backend() != provider.backend()) {
        throw SandboxServiceException.backendMismatch(key, record.backend(), provider.backend());
    }
}
```

Delete the old `createSandbox(...)` method after all call sites are replaced.

- [ ] **Step 8: Refactor close resume path**

In `doClose(...)`, replace the record resume path:

```java
sandbox = client.resume(client.deserializeState(record.sandboxStateJson()));
```

with:

```java
assertBackendMatches(key, record);
sandbox = provider.resume(record.sandboxStateJson());
```

When saving `CLOSED` without a sandbox, preserve `record.backend()`:

```java
new SandboxRecord(
        record.userId(),
        record.sessionId(),
        record.backend(),
        SandboxLifecycleStatus.CLOSED,
        record.sandboxStateJson(),
        record.createdAt(),
        Instant.now())
```

- [ ] **Step 9: Refactor state serialization and status response**

Replace `toRecord(...)` implementation with:

```java
/** 把沙箱当前状态序列化后包装为状态记录。 */
private SandboxRecord toRecord(
        SandboxKey key, SandboxLifecycleStatus status, Sandbox sandbox, Instant createdAt) {
    String stateJson = provider.serializeState(sandbox);
    return new SandboxRecord(
            key.userId(),
            key.sessionId(),
            provider.backend(),
            status,
            stateJson,
            createdAt,
            Instant.now());
}
```

Replace `toStatus(...)` implementation:

```java
/** 组装状态响应，尽量从运行态沙箱读取通用后端信息和快照可恢复性。 */
private SandboxStatusResponse toStatus(
        SandboxKey key, SandboxRuntime runtime, SandboxRecord record) {
    boolean running = runtime != null && runtime.sandbox().isRunning();
    SandboxRuntimeDescriptor descriptor =
            runtime != null
                    ? provider.describe(runtime.sandbox())
                    : new SandboxRuntimeDescriptor(
                            record != null ? record.backend() : provider.backend(),
                            provider.workspaceRoot(),
                            Map.of());
    boolean snapshotRestorable = false;
    if (runtime != null) {
        SandboxSnapshot snapshot = runtime.sandbox().getState().getSnapshot();
        if (snapshot != null) {
            try {
                snapshotRestorable = snapshot.isRestorable();
            } catch (Exception e) {
                snapshotRestorable = false;
            }
        }
    }
    SandboxLifecycleStatus status =
            record != null
                    ? record.status()
                    : (running
                            ? SandboxLifecycleStatus.RUNNING
                            : SandboxLifecycleStatus.STOPPED);
    return new SandboxStatusResponse(
            key.userId(),
            key.sessionId(),
            descriptor.backend(),
            status,
            running,
            snapshotRestorable,
            descriptor.workspaceRoot(),
            descriptor.attributes(),
            record != null ? record.createdAt() : null,
            record != null ? record.updatedAt() : null);
}
```

Add import:

```java
import java.util.Map;
```

- [ ] **Step 10: Update Spring config**

In `SandboxServiceConfig.java`, register providers:

```java
/** 创建 Docker 沙箱 provider。 */
@Bean
public DockerSandboxProvider dockerSandboxProvider(
        SandboxServiceProperties properties, DockerSandboxClient dockerSandboxClient) {
    return new DockerSandboxProvider(properties, dockerSandboxClient);
}

/** 创建 Kubernetes 沙箱客户端。 */
@Bean
public KubernetesSandboxClient kubernetesSandboxClient(ObjectMapper objectMapper) {
    return new KubernetesSandboxClient(null, sandboxObjectMapper(objectMapper));
}

/** 创建 Kubernetes 沙箱 provider。 */
@Bean
public KubernetesSandboxProvider kubernetesSandboxProvider(
        SandboxServiceProperties properties, KubernetesSandboxClient kubernetesSandboxClient) {
    return new KubernetesSandboxProvider(properties, kubernetesSandboxClient);
}

/** 根据配置选择当前服务使用的沙箱 provider。 */
@Bean
public SandboxProvider sandboxProvider(
        SandboxServiceProperties properties,
        DockerSandboxProvider dockerSandboxProvider,
        KubernetesSandboxProvider kubernetesSandboxProvider) {
    return switch (properties.getBackend()) {
        case DOCKER -> dockerSandboxProvider;
        case KUBERNETES -> kubernetesSandboxProvider;
    };
}

/** 创建能识别 Harness 与 Kubernetes 沙箱状态类型的 ObjectMapper。 */
private static ObjectMapper sandboxObjectMapper(ObjectMapper objectMapper) {
    return objectMapper.copy()
            .findAndRegisterModules()
            .registerModule(new HarnessSandboxJacksonModule())
            .registerModule(new KubernetesHarnessSandboxJacksonModule());
}
```

Update `sandboxLifecycleService(...)` signature to receive `SandboxProvider sandboxProvider`, and pass it into `new SandboxLifecycleService(...)`.

Add imports:

```java
import io.agentscope.extensions.sandbox.kubernetes.KubernetesHarnessSandboxJacksonModule;
import io.agentscope.extensions.sandbox.kubernetes.KubernetesSandboxClient;
import io.agentscope.harness.agent.sandbox.json.HarnessSandboxJacksonModule;
import io.agentscope.sandboxservice.service.DockerSandboxProvider;
import io.agentscope.sandboxservice.service.KubernetesSandboxProvider;
import io.agentscope.sandboxservice.service.SandboxProvider;
```

- [ ] **Step 11: Run lifecycle and repository tests**

Run:

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=SandboxLifecycleServiceTest,FileSandboxStateRepositoryTest test
```

Expected: PASS.

- [ ] **Step 12: Commit**

```bash
git add agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxRecord.java \
  agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/error/SandboxServiceException.java \
  agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxLifecycleService.java \
  agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/config/SandboxServiceConfig.java \
  agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/service/SandboxLifecycleServiceTest.java \
  agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/service/FileSandboxStateRepositoryTest.java
git commit -m "refactor: route sandbox lifecycle through provider"
```

---

### Task 6: Controller Tests, README, And Final Verification

**Files:**
- Modify: `agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/controller/SandboxControllerTest.java`
- Modify: `agentscope-examples/agents/agentscope-sandbox-service/README.md`
- Modify if compilation requires response accessor updates: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/controller/SandboxController.java`

**Interfaces:**
- Consumes: new `SandboxStatusResponse` constructor and accessors
- Produces: documented Docker/Kubernetes configuration and verified API response shape

- [ ] **Step 1: Update controller test expectations**

In `SandboxControllerTest.java`, replace expectations for `containerId` and `containerName` with `backend` and `runtime`. Use this expected JSON fragment in status/start tests:

```java
.andExpect(jsonPath("$.backend").value("DOCKER"))
.andExpect(jsonPath("$.workspaceRoot").value("/workspace"))
.andExpect(jsonPath("$.runtime.containerId").value("cid-1"))
.andExpect(jsonPath("$.runtime.containerName").value("agentscope-sandbox-1"));
```

If the test builds a response object directly, construct it as:

```java
new SandboxStatusResponse(
        "alice",
        "conv-1",
        SandboxBackendType.DOCKER,
        SandboxLifecycleStatus.RUNNING,
        true,
        true,
        "/workspace",
        Map.of("containerId", "cid-1", "containerName", "agentscope-sandbox-1"),
        Instant.parse("2026-09-10T10:00:00Z"),
        Instant.parse("2026-09-10T10:05:00Z"))
```

Add imports:

```java
import io.agentscope.sandboxservice.service.SandboxBackendType;
import java.util.Map;
```

- [ ] **Step 2: Run controller test to verify failures are fixed**

Run:

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=SandboxControllerTest test
```

Expected: PASS.

- [ ] **Step 3: Update README configuration section**

In `README.md`, replace the config table with entries for:

```text
sandbox-service.backend
sandbox-service.state-dir
sandbox-service.snapshot-dir
sandbox-service.docker.image
sandbox-service.docker.workspace-root
sandbox-service.docker.network
sandbox-service.docker.memory-size-bytes
sandbox-service.docker.cpu-count
sandbox-service.kubernetes.namespace
sandbox-service.kubernetes.warm-pool-name
sandbox-service.kubernetes.workspace-root
sandbox-service.kubernetes.file-api-base-dir
sandbox-service.kubernetes.api-url
sandbox-service.kubernetes.gateway-name
sandbox-service.kubernetes.gateway-namespace
sandbox-service.kubernetes.gateway-scheme
sandbox-service.kubernetes.server-port
sandbox-service.exec.default-timeout-seconds
```

Add a Kubernetes example:

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am spring-boot:run \
  -Dspring-boot.run.arguments="--sandbox-service.backend=kubernetes --sandbox-service.kubernetes.namespace=agents --sandbox-service.kubernetes.warm-pool-name=agent-pool"
```

Document that Kubernetes requires the agent-sandbox controller, CRDs, `SandboxTemplate`, `SandboxWarmPool`, and a runtime image exposing port `8888` with `/execute`, `/upload`, `/download`, `/list`, and `/exists`.

- [ ] **Step 4: Run all sandbox-service tests**

Run:

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am test
```

Expected: PASS.

- [ ] **Step 5: Verify lifecycle service no longer imports backend types**

Run:

```bash
rg -n "DockerSandbox|KubernetesSandbox|DockerSandboxClient|KubernetesSandboxClient|DockerSandboxState|KubernetesSandboxState" agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxLifecycleService.java
```

Expected: no matches.

- [ ] **Step 6: Verify formatting and whitespace**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 7: Commit**

```bash
git add agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/controller/SandboxControllerTest.java \
  agentscope-examples/agents/agentscope-sandbox-service/README.md \
  agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/controller/SandboxController.java
git commit -m "docs: document configurable sandbox backends"
```

---

## Final Verification

After all tasks are complete, run:

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am test
git diff --check
rg -n "DockerSandbox|KubernetesSandbox|DockerSandboxClient|KubernetesSandboxClient|DockerSandboxState|KubernetesSandboxState" agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxLifecycleService.java
```

Expected results:

- Maven tests pass.
- `git diff --check` prints no whitespace errors.
- The `rg` command prints no backend-specific imports or references from `SandboxLifecycleService.java`.

## Implementation Notes

- Before executing this plan, inspect `git status --short`; the current workspace may contain user edits, especially in `SandboxLifecycleService.java`.
- Preserve user edits when refactoring touched files. If a touched file contains unrelated user changes, integrate around them instead of reverting them.
- If adding `agentscope-extensions-sandbox-kubernetes` introduces dependency ordering issues, first verify that `agentscope-extensions/agentscope-extensions-sandbox/pom.xml` already lists the Kubernetes module, then build with `-am`.
- Keep Kubernetes integration tests opt-in. Do not make ordinary `mvn test` require kubeconfig, Docker daemon, CRDs, WarmPool, or network access.
