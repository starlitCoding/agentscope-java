# Sandbox Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增一个独立 Spring Boot 服务，通过 HTTP API 按 `userId` 和 `sessionId` 管理本地 Docker 沙箱生命周期、快照恢复、命令执行和文件工具操作。

**Architecture:** 新模块 `agentscope-examples/agents/agentscope-sandbox-service` 只依赖 `agentscope-harness` 与 Spring Boot Web。控制器接收 REST 请求，服务层按 `(userId, sessionId)` 串行化操作，状态仓库把 `SandboxState` JSON 保存到本地文件，运行态缓存持有 `Sandbox` 与 `SandboxBackedFilesystem`。Docker 容器和快照能力全部复用 Harness 的 `DockerSandboxClient`、`Sandbox`、`LocalSnapshotSpec`。

**Tech Stack:** Java 17, Maven, Spring Boot 4.0.4, Spring MVC, Jakarta Validation, JUnit 5, Mockito, Spring Boot Test, AgentScope Harness Docker sandbox.

**Spec:** `docs/superpowers/specs/2026-09-02-sandbox-service-design.md`

## Global Constraints

- 新模块路径必须是 `agentscope-examples/agents/agentscope-sandbox-service`。
- 服务只复用 AgentScope Harness 的沙箱与文件系统能力，不引入模型、不创建 `ReactAgent`，也不构建 `HarnessAgent`。
- 沙箱隔离键必须是 `(userId, sessionId)`。
- 第一版只支持本地 Docker 沙箱。
- 第一版使用本地文件保存状态 JSON 和 tar 快照，不引入数据库。
- 默认 `state-dir` 是 `${user.home}/.agentscope-sandbox-service/state`。
- 默认 `snapshot-dir` 是 `${user.home}/.agentscope-sandbox-service/snapshots`。
- 默认 Docker 镜像是 `ubuntu:24.04`。
- 默认 `workspace-root` 是 `/workspace`。
- 默认 Docker network 是 `none`。
- 默认内存限制是 `1073741824` 字节。
- 默认 CPU 限制是 `2`。
- 默认命令超时时间是 `120` 秒。
- 普通测试不依赖 Docker。
- Docker 真容器测试必须可选开启，不能阻塞默认构建。
- 生产代码中的每个方法都要写注释，说明用途、关键参数或重要行为。
- 不修改已有用户改动；提交时只提交当前任务涉及的文件。

---

## File Structure

新增和修改文件如下：

- Modify: `agentscope-examples/pom.xml`
  - 注册 `agents/agentscope-sandbox-service` 子模块。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/pom.xml`
  - 定义 Spring Boot 独立 jar 模块及依赖。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/resources/application.yml`
  - 提供默认服务端口、状态目录、快照目录、Docker 配置和命令超时。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/SandboxServiceApplication.java`
  - Spring Boot 入口。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/config/SandboxServiceProperties.java`
  - `sandbox-service` 配置模型。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/config/SandboxServiceConfig.java`
  - 创建 `DockerSandboxClient`、`LocalSnapshotSpec`、`ObjectMapper` 等 Bean。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxKey.java`
  - 表示 `(userId, sessionId)`，并提供校验和路径安全编码。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxRecord.java`
  - 状态文件包装对象，保存业务键、状态、sandbox state JSON、时间戳。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxLifecycleStatus.java`
  - `RUNNING`、`STOPPED`、`CLOSED` 状态枚举。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxStateRepository.java`
  - 状态仓库接口。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/FileSandboxStateRepository.java`
  - 本地 JSON 文件状态仓库实现。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxRuntime.java`
  - 内存运行态对象，持有 `Sandbox`、`SandboxBackedFilesystem` 和访问时间。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxOperationLockRegistry.java`
  - 按 `SandboxKey` 提供串行化锁。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxLifecycleService.java`
  - 生命周期和命令执行核心服务。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxFileToolService.java`
  - 文件工具服务，依赖 `SandboxFilesystemOperations` 完成 Harness 文件工具调用。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/dto/*.java`
  - REST 请求和响应 DTO。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/controller/SandboxController.java`
  - 生命周期、状态和 exec API。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/controller/SandboxFileController.java`
  - 文件工具 API。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/error/SandboxServiceException.java`
  - 业务异常与错误码。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/error/SandboxExceptionHandler.java`
  - 统一错误响应。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/README.md`
  - 中文使用说明、API 示例和安全提示。
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/**`
  - 单元测试、MVC API 测试和可选 Docker 集成测试。

---

### Task 1: Maven Module And Configuration Shell

**Files:**
- Modify: `agentscope-examples/pom.xml`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/pom.xml`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/SandboxServiceApplication.java`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/config/SandboxServiceProperties.java`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/config/SandboxServiceConfig.java`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/resources/application.yml`
- Test: `agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/config/SandboxServicePropertiesTest.java`

**Interfaces:**
- Consumes: existing Maven parent `agentscope-examples`, existing artifact `io.agentscope:agentscope-harness`.
- Produces:
  - `SandboxServiceApplication`
  - `SandboxServiceProperties`
  - `DockerSandboxClient dockerSandboxClient()`
  - `LocalSnapshotSpec localSnapshotSpec(SandboxServiceProperties properties)`

- [ ] **Step 1: Write the failing configuration binding test**

Create `SandboxServicePropertiesTest.java`:

```java
package io.agentscope.sandboxservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class SandboxServicePropertiesTest {

    /** 验证默认 application.yml 能绑定到配置对象，避免启动后 Docker 参数为空。 */
    @Test
    void bindsDefaultSandboxServiceProperties() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        environment.getPropertySources()
                .addFirst(loader.load("test", new ClassPathResource("application.yml")).get(0));

        SandboxServiceProperties properties = Binder.get(environment)
                .bind("sandbox-service", Bindable.of(SandboxServiceProperties.class))
                .orElseThrow();

        assertThat(properties.getDocker().getImage()).isEqualTo("ubuntu:24.04");
        assertThat(properties.getDocker().getWorkspaceRoot()).isEqualTo("/workspace");
        assertThat(properties.getDocker().getNetwork()).isEqualTo("none");
        assertThat(properties.getDocker().getMemorySizeBytes()).isEqualTo(1073741824L);
        assertThat(properties.getDocker().getCpuCount()).isEqualTo(2L);
        assertThat(properties.getExec().getDefaultTimeoutSeconds()).isEqualTo(120);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=SandboxServicePropertiesTest test`

Expected: FAIL because the module, configuration class, or test target does not exist yet.

- [ ] **Step 3: Create the Maven module**

Add this module entry to `agentscope-examples/pom.xml` inside `<modules>`:

```xml
<module>agents/agentscope-sandbox-service</module>
```

Create `agentscope-examples/agents/agentscope-sandbox-service/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.agentscope</groupId>
        <artifactId>agentscope-examples</artifactId>
        <version>${revision}</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <groupId>io.agentscope.examples</groupId>
    <artifactId>agentscope-sandbox-service</artifactId>
    <packaging>jar</packaging>
    <name>AgentScope Java - Sandbox Service</name>
    <description>Standalone Spring Boot service exposing Docker sandbox lifecycle and file tool APIs.</description>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring.boot.version}</version>
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
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring.boot.version}</version>
                <configuration>
                    <mainClass>io.agentscope.sandboxservice.SandboxServiceApplication</mainClass>
                    <skip>true</skip>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: Add application and configuration classes**

Create `SandboxServiceApplication.java`:

```java
package io.agentscope.sandboxservice;

import io.agentscope.sandboxservice.config.SandboxServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** 启动独立沙箱服务，不创建模型、ReactAgent 或 HarnessAgent。 */
@SpringBootApplication
@EnableConfigurationProperties(SandboxServiceProperties.class)
public class SandboxServiceApplication {

    /** 启动 Spring Boot HTTP 服务。 */
    public static void main(String[] args) {
        SpringApplication.run(SandboxServiceApplication.class, args);
    }
}
```

Create `SandboxServiceProperties.java` with nested `Docker` and `Exec` classes. Include getters/setters and method comments:

```java
package io.agentscope.sandboxservice.config;

import java.nio.file.Path;

/** 绑定 sandbox-service 配置，集中管理状态目录、快照目录和 Docker 默认参数。 */
public class SandboxServiceProperties {

    private Path stateDir = Path.of(System.getProperty("user.home"), ".agentscope-sandbox-service", "state");
    private Path snapshotDir = Path.of(System.getProperty("user.home"), ".agentscope-sandbox-service", "snapshots");
    private Docker docker = new Docker();
    private Exec exec = new Exec();

    /** 返回本地状态 JSON 根目录。 */
    public Path getStateDir() {
        return stateDir;
    }

    /** 设置本地状态 JSON 根目录。 */
    public void setStateDir(Path stateDir) {
        this.stateDir = stateDir;
    }

    /** 返回本地快照 tar 根目录。 */
    public Path getSnapshotDir() {
        return snapshotDir;
    }

    /** 设置本地快照 tar 根目录。 */
    public void setSnapshotDir(Path snapshotDir) {
        this.snapshotDir = snapshotDir;
    }

    /** 返回 Docker 运行配置。 */
    public Docker getDocker() {
        return docker;
    }

    /** 设置 Docker 运行配置。 */
    public void setDocker(Docker docker) {
        this.docker = docker;
    }

    /** 返回命令执行配置。 */
    public Exec getExec() {
        return exec;
    }

    /** 设置命令执行配置。 */
    public void setExec(Exec exec) {
        this.exec = exec;
    }

    /** Docker 沙箱默认参数。 */
    public static class Docker {
        private String image = "ubuntu:24.04";
        private String workspaceRoot = "/workspace";
        private String network = "none";
        private Long memorySizeBytes = 1073741824L;
        private Long cpuCount = 2L;

        /** 返回 Docker 镜像名。 */
        public String getImage() {
            return image;
        }

        /** 设置 Docker 镜像名。 */
        public void setImage(String image) {
            this.image = image;
        }

        /** 返回容器内工作区根目录。 */
        public String getWorkspaceRoot() {
            return workspaceRoot;
        }

        /** 设置容器内工作区根目录。 */
        public void setWorkspaceRoot(String workspaceRoot) {
            this.workspaceRoot = workspaceRoot;
        }

        /** 返回 Docker 网络模式或网络名。 */
        public String getNetwork() {
            return network;
        }

        /** 设置 Docker 网络模式或网络名。 */
        public void setNetwork(String network) {
            this.network = network;
        }

        /** 返回容器内存限制字节数。 */
        public Long getMemorySizeBytes() {
            return memorySizeBytes;
        }

        /** 设置容器内存限制字节数。 */
        public void setMemorySizeBytes(Long memorySizeBytes) {
            this.memorySizeBytes = memorySizeBytes;
        }

        /** 返回容器 CPU 限制。 */
        public Long getCpuCount() {
            return cpuCount;
        }

        /** 设置容器 CPU 限制。 */
        public void setCpuCount(Long cpuCount) {
            this.cpuCount = cpuCount;
        }
    }

    /** 命令执行默认参数。 */
    public static class Exec {
        private Integer defaultTimeoutSeconds = 120;

        /** 返回默认命令超时时间。 */
        public Integer getDefaultTimeoutSeconds() {
            return defaultTimeoutSeconds;
        }

        /** 设置默认命令超时时间。 */
        public void setDefaultTimeoutSeconds(Integer defaultTimeoutSeconds) {
            this.defaultTimeoutSeconds = defaultTimeoutSeconds;
        }
    }
}
```

Create `SandboxServiceConfig.java`:

```java
package io.agentscope.sandboxservice.config;

import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClient;
import io.agentscope.harness.agent.sandbox.snapshot.LocalSnapshotSpec;
import java.io.IOException;
import java.nio.file.Files;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 创建沙箱服务需要的基础 Bean，并确保本地状态目录存在。 */
@Configuration
public class SandboxServiceConfig {

    /** 创建 Docker 沙箱客户端。 */
    @Bean
    public DockerSandboxClient dockerSandboxClient() {
        return new DockerSandboxClient();
    }

    /** 创建本地快照策略，并确保快照目录存在。 */
    @Bean
    public LocalSnapshotSpec localSnapshotSpec(SandboxServiceProperties properties) throws IOException {
        Files.createDirectories(properties.getSnapshotDir().toAbsolutePath().normalize());
        return new LocalSnapshotSpec(properties.getSnapshotDir().toAbsolutePath().normalize());
    }
}
```

Create `application.yml` exactly from the spec defaults.

- [ ] **Step 5: Run the module configuration test**

Run: `mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=SandboxServicePropertiesTest test`

Expected: PASS.

- [ ] **Step 6: Commit Task 1**

Run:

```bash
git add agentscope-examples/pom.xml agentscope-examples/agents/agentscope-sandbox-service
git commit -m "feat: add sandbox service module shell"
```

---

### Task 2: State Repository And Key Encoding

**Files:**
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxKey.java`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxLifecycleStatus.java`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxRecord.java`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxStateRepository.java`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/FileSandboxStateRepository.java`
- Test: `agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/service/FileSandboxStateRepositoryTest.java`

**Interfaces:**
- Consumes: `SandboxServiceProperties#getStateDir()`.
- Produces:
  - `record SandboxKey(String userId, String sessionId)`
  - `enum SandboxLifecycleStatus { RUNNING, STOPPED, CLOSED }`
  - `record SandboxRecord(String userId, String sessionId, SandboxLifecycleStatus status, String sandboxStateJson, Instant createdAt, Instant updatedAt)`
  - `interface SandboxStateRepository`
  - `class FileSandboxStateRepository implements SandboxStateRepository`

- [ ] **Step 1: Write failing repository tests**

Create `FileSandboxStateRepositoryTest.java`:

```java
package io.agentscope.sandboxservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSandboxStateRepositoryTest {

    @TempDir Path tempDir;

    /** 验证状态能保存到本地文件并按原始业务键加载回来。 */
    @Test
    void savesAndLoadsRecordBySandboxKey() {
        FileSandboxStateRepository repository = new FileSandboxStateRepository(tempDir, new ObjectMapper().findAndRegisterModules());
        SandboxKey key = SandboxKey.of("alice", "conv-1");
        SandboxRecord record = new SandboxRecord("alice", "conv-1", SandboxLifecycleStatus.STOPPED, "{\"type\":\"docker\"}", Instant.parse("2026-09-02T10:00:00Z"), Instant.parse("2026-09-02T10:01:00Z"));

        repository.save(key, record);

        assertThat(repository.find(key)).contains(record);
    }

    /** 验证 userId 和 sessionId 不会被直接拼成路径，避免路径穿越。 */
    @Test
    void encodesUnsafeUserAndSessionSegments() throws Exception {
        FileSandboxStateRepository repository = new FileSandboxStateRepository(tempDir, new ObjectMapper().findAndRegisterModules());
        SandboxKey key = SandboxKey.of("../alice", "conv/1");
        SandboxRecord record = new SandboxRecord("../alice", "conv/1", SandboxLifecycleStatus.RUNNING, "{\"type\":\"docker\"}", Instant.parse("2026-09-02T10:00:00Z"), Instant.parse("2026-09-02T10:01:00Z"));

        repository.save(key, record);

        assertThat(repository.find(key)).contains(record);
        assertThat(Files.exists(tempDir.resolve("../alice"))).isFalse();
    }

    /** 验证缺失状态文件时返回空，便于生命周期服务创建新沙箱。 */
    @Test
    void returnsEmptyWhenRecordDoesNotExist() {
        FileSandboxStateRepository repository = new FileSandboxStateRepository(tempDir, new ObjectMapper().findAndRegisterModules());

        assertThat(repository.find(SandboxKey.of("bob", "conv-2"))).isEmpty();
    }

    /** 验证空业务键会被拒绝，避免生成不可定位的状态文件。 */
    @Test
    void rejectsBlankSandboxKeyValues() {
        assertThatThrownBy(() -> SandboxKey.of(" ", "conv-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SandboxKey.of("alice", "")).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=FileSandboxStateRepositoryTest test`

Expected: FAIL because state repository classes are missing.

- [ ] **Step 3: Implement key, record, enum, and repository**

Implement `SandboxKey`:

```java
package io.agentscope.sandboxservice.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** 表示一个业务沙箱的隔离键，由 userId 和 sessionId 共同决定。 */
public record SandboxKey(String userId, String sessionId) {

    /** 创建并校验业务沙箱键。 */
    public static SandboxKey of(String userId, String sessionId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        return new SandboxKey(userId, sessionId);
    }

    /** 返回可安全用于文件路径的 userId 片段。 */
    public String safeUserSegment() {
        return encode(userId);
    }

    /** 返回可安全用于文件路径的 sessionId 片段。 */
    public String safeSessionSegment() {
        return encode(sessionId);
    }

    /** 使用 URL-safe Base64 编码路径片段，避免路径穿越。 */
    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
```

Implement `SandboxLifecycleStatus`:

```java
package io.agentscope.sandboxservice.service;

/** 服务侧记录的沙箱生命周期状态。 */
public enum SandboxLifecycleStatus {
    RUNNING,
    STOPPED,
    CLOSED
}
```

Implement `SandboxRecord`:

```java
package io.agentscope.sandboxservice.service;

import java.time.Instant;

/** 本地状态文件中的业务包装对象，内部保存 Harness 原生 SandboxState JSON。 */
public record SandboxRecord(
        String userId,
        String sessionId,
        SandboxLifecycleStatus status,
        String sandboxStateJson,
        Instant createdAt,
        Instant updatedAt) {}
```

Implement `SandboxStateRepository`:

```java
package io.agentscope.sandboxservice.service;

import java.util.Optional;

/** 负责按业务沙箱键保存和加载沙箱状态。 */
public interface SandboxStateRepository {

    /** 按业务键查找沙箱状态。 */
    Optional<SandboxRecord> find(SandboxKey key);

    /** 保存沙箱状态。 */
    SandboxRecord save(SandboxKey key, SandboxRecord record);
}
```

Implement `FileSandboxStateRepository` using atomic write:

```java
package io.agentscope.sandboxservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/** 使用本地 JSON 文件保存沙箱状态。 */
public class FileSandboxStateRepository implements SandboxStateRepository {

    private final Path stateDir;
    private final ObjectMapper objectMapper;

    /** 创建文件状态仓库，并确保状态根目录存在。 */
    public FileSandboxStateRepository(Path stateDir, ObjectMapper objectMapper) {
        this.stateDir = stateDir.toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
        try {
            Files.createDirectories(this.stateDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create sandbox state dir: " + this.stateDir, e);
        }
    }

    /** 按业务键读取状态文件，不存在时返回空。 */
    @Override
    public Optional<SandboxRecord> find(SandboxKey key) {
        Path path = statePath(key);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(path.toFile(), SandboxRecord.class));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read sandbox state file: " + path, e);
        }
    }

    /** 原子写入状态文件，避免进程中断留下半个 JSON。 */
    @Override
    public SandboxRecord save(SandboxKey key, SandboxRecord record) {
        Path path = statePath(key);
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writeValue(tmp.toFile(), record);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return record;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write sandbox state file: " + path, e);
        }
    }

    /** 计算业务键对应的状态文件路径。 */
    private Path statePath(SandboxKey key) {
        return stateDir.resolve(key.safeUserSegment()).resolve(key.safeSessionSegment() + ".json").normalize();
    }
}
```

- [ ] **Step 4: Wire repository bean**

Add to `SandboxServiceConfig.java`:

```java
/** 创建本地状态仓库，并确保状态目录存在。 */
@Bean
public SandboxStateRepository sandboxStateRepository(
        SandboxServiceProperties properties, ObjectMapper objectMapper) {
    return new FileSandboxStateRepository(properties.getStateDir(), objectMapper.findAndRegisterModules());
}
```

- [ ] **Step 5: Run repository tests**

Run: `mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=FileSandboxStateRepositoryTest test`

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

Run:

```bash
git add agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/config/SandboxServiceConfig.java agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/service
git commit -m "feat: persist sandbox service state"
```

---

### Task 3: Lifecycle Service Without Real Docker In Unit Tests

**Files:**
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxRuntime.java`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxOperationLockRegistry.java`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxLifecycleService.java`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxFilesystemOperations.java`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/HarnessSandboxFilesystemOperations.java`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/dto/SandboxStatusResponse.java`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/dto/SandboxExecResponse.java`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/error/SandboxServiceException.java`
- Test: `agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/service/SandboxLifecycleServiceTest.java`

**Interfaces:**
- Consumes:
  - `SandboxKey`
  - `SandboxStateRepository`
  - `DockerSandboxClient`
  - `LocalSnapshotSpec`
  - `SandboxServiceProperties`
- Produces:
  - `SandboxStatusResponse start(SandboxKey key)`
  - `SandboxStatusResponse create(SandboxKey key)`
  - `SandboxStatusResponse stop(SandboxKey key)`
  - `SandboxStatusResponse close(SandboxKey key)`
  - `Optional<SandboxStatusResponse> status(SandboxKey key)`
  - `SandboxExecResponse exec(SandboxKey key, String command, Integer timeoutSeconds)`
  - `SandboxRuntime requireRunning(SandboxKey key)`

- [ ] **Step 1: Write failing lifecycle tests with test doubles**

Create `SandboxLifecycleServiceTest.java`. Use fake `Sandbox` and fake `SandboxClient` classes inside the test file to avoid Docker:

```java
package io.agentscope.sandboxservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.sandbox.ExecResult;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxState;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClient;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClientOptions;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxState;
import io.agentscope.harness.agent.sandbox.snapshot.NoopSnapshotSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import io.agentscope.sandboxservice.dto.SandboxExecResponse;
import io.agentscope.sandboxservice.dto.SandboxStatusResponse;
import io.agentscope.sandboxservice.config.SandboxServiceProperties;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SandboxLifecycleServiceTest {

    /** 验证无历史状态时会创建新沙箱、启动并保存 RUNNING 状态。 */
    @Test
    void startsNewSandboxWhenNoRecordExists() {
        InMemoryRepository repository = new InMemoryRepository();
        FakeDockerSandboxClient client = new FakeDockerSandboxClient();
        SandboxLifecycleService service = newService(repository, client);

        SandboxStatusResponse response = service.start(SandboxKey.of("alice", "conv-1"));

        assertThat(response.status()).isEqualTo(SandboxLifecycleStatus.RUNNING);
        assertThat(client.createCalls).isEqualTo(1);
        assertThat(client.latestSandbox.started).isTrue();
        assertThat(repository.record).isPresent();
        assertThat(repository.record.orElseThrow().status()).isEqualTo(SandboxLifecycleStatus.RUNNING);
    }

    /** 验证有历史状态时会 resume 而不是 create。 */
    @Test
    void resumesSandboxWhenRecordExists() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.record = Optional.of(new SandboxRecord("alice", "conv-1", SandboxLifecycleStatus.STOPPED, "state-json", Instant.now(), Instant.now()));
        FakeDockerSandboxClient client = new FakeDockerSandboxClient();
        SandboxLifecycleService service = newService(repository, client);

        service.start(SandboxKey.of("alice", "conv-1"));

        assertThat(client.resumeCalls).isEqualTo(1);
        assertThat(client.createCalls).isZero();
    }

    /** 验证 stop 会保存快照语义对应的状态并保持容器不 shutdown。 */
    @Test
    void stopPersistsStateWithoutShutdown() {
        InMemoryRepository repository = new InMemoryRepository();
        FakeDockerSandboxClient client = new FakeDockerSandboxClient();
        SandboxLifecycleService service = newService(repository, client);
        SandboxKey key = SandboxKey.of("alice", "conv-1");

        service.start(key);
        service.stop(key);

        assertThat(client.latestSandbox.stopped).isTrue();
        assertThat(client.latestSandbox.shutdown).isFalse();
        assertThat(repository.record.orElseThrow().status()).isEqualTo(SandboxLifecycleStatus.STOPPED);
    }

    /** 验证 close 会先保存状态再释放容器。 */
    @Test
    void closeStopsAndShutdownsSandbox() {
        InMemoryRepository repository = new InMemoryRepository();
        FakeDockerSandboxClient client = new FakeDockerSandboxClient();
        SandboxLifecycleService service = newService(repository, client);
        SandboxKey key = SandboxKey.of("alice", "conv-1");

        service.start(key);
        service.close(key);

        assertThat(client.latestSandbox.stopped).isTrue();
        assertThat(client.latestSandbox.shutdown).isTrue();
        assertThat(repository.record.orElseThrow().status()).isEqualTo(SandboxLifecycleStatus.CLOSED);
    }

    /** 验证 exec 会复用运行态沙箱并返回命令输出。 */
    @Test
    void execRunsCommandInRunningSandbox() {
        InMemoryRepository repository = new InMemoryRepository();
        FakeDockerSandboxClient client = new FakeDockerSandboxClient();
        SandboxLifecycleService service = newService(repository, client);
        SandboxKey key = SandboxKey.of("alice", "conv-1");
        service.start(key);

        SandboxExecResponse response = service.exec(key, "echo hi", 30);

        assertThat(response.exitCode()).isZero();
        assertThat(response.stdout()).isEqualTo("echo hi");
    }

    private SandboxLifecycleService newService(InMemoryRepository repository, FakeDockerSandboxClient client) {
        SandboxServiceProperties properties = new SandboxServiceProperties();
        return new SandboxLifecycleService(client, new NoopSnapshotSpec(), repository, properties, new SandboxOperationLockRegistry());
    }

    static class InMemoryRepository implements SandboxStateRepository {
        Optional<SandboxRecord> record = Optional.empty();

        public Optional<SandboxRecord> find(SandboxKey key) {
            return record;
        }

        public SandboxRecord save(SandboxKey key, SandboxRecord record) {
            this.record = Optional.of(record);
            return record;
        }
    }

    static class FakeDockerSandboxClient extends DockerSandboxClient {
        int createCalls;
        int resumeCalls;
        FakeSandbox latestSandbox;

        public Sandbox create(WorkspaceSpec workspaceSpec, SandboxSnapshotSpec snapshotSpec, DockerSandboxClientOptions options) {
            createCalls++;
            latestSandbox = new FakeSandbox();
            return latestSandbox;
        }

        public Sandbox resume(SandboxState state) {
            resumeCalls++;
            latestSandbox = new FakeSandbox();
            return latestSandbox;
        }

        public void delete(Sandbox sandbox) {}

        public String serializeState(SandboxState state) {
            return "state-json";
        }

        public SandboxState deserializeState(String json) {
            DockerSandboxState state = new DockerSandboxState();
            state.setSessionId("resumed-session");
            state.setWorkspaceSpec(new WorkspaceSpec());
            state.setWorkspaceRoot("/workspace");
            state.setImage("ubuntu:24.04");
            return state;
        }
    }

    static class FakeSandbox implements Sandbox {
        boolean started;
        boolean stopped;
        boolean shutdown;
        DockerSandboxState state = new DockerSandboxState();

        public void start() {
            started = true;
        }

        public void stop() {
            stopped = true;
        }

        public void shutdown() {
            shutdown = true;
        }

        public void close() throws Exception {
            stop();
            shutdown();
        }

        public boolean isRunning() {
            return started && !stopped;
        }

        public SandboxState getState() {
            return state;
        }

        public ExecResult exec(RuntimeContext runtimeContext, String command, Integer timeoutSeconds) {
            return new ExecResult(0, command, "", false);
        }

        public InputStream persistWorkspace() {
            return new ByteArrayInputStream(new byte[0]);
        }

        public void hydrateWorkspace(InputStream archive) {}
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=SandboxLifecycleServiceTest test`

Expected: FAIL because lifecycle classes and response DTOs are missing.

- [ ] **Step 3: Implement lifecycle DTOs and runtime classes**

Implement `SandboxRuntime`:

```java
package io.agentscope.sandboxservice.service;

import io.agentscope.harness.agent.filesystem.sandbox.SandboxBackedFilesystem;
import io.agentscope.harness.agent.sandbox.Sandbox;
import java.time.Instant;

/** 内存中的运行态沙箱，绑定 Harness Sandbox 与文件系统代理。 */
public class SandboxRuntime {

    private final Sandbox sandbox;
    private final SandboxBackedFilesystem filesystem;
    private final Instant startedAt;
    private Instant lastAccessAt;

    /** 创建运行态对象并把文件系统代理绑定到当前沙箱。 */
    public SandboxRuntime(Sandbox sandbox) {
        this.sandbox = sandbox;
        this.filesystem = new SandboxBackedFilesystem();
        this.filesystem.setSandbox(sandbox);
        this.startedAt = Instant.now();
        this.lastAccessAt = this.startedAt;
    }

    /** 返回当前 Harness 沙箱。 */
    public Sandbox sandbox() {
        return sandbox;
    }

    /** 返回绑定当前沙箱的文件系统工具代理。 */
    public SandboxBackedFilesystem filesystem() {
        touch();
        return filesystem;
    }

    /** 返回启动时间。 */
    public Instant startedAt() {
        return startedAt;
    }

    /** 返回最后访问时间。 */
    public Instant lastAccessAt() {
        return lastAccessAt;
    }

    /** 刷新最后访问时间。 */
    public void touch() {
        this.lastAccessAt = Instant.now();
    }
}
```

Implement `SandboxOperationLockRegistry`:

```java
package io.agentscope.sandboxservice.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** 按业务沙箱键串行化操作，防止同一工作区并发写状态或快照。 */
public class SandboxOperationLockRegistry {

    private final ConcurrentHashMap<SandboxKey, ReentrantLock> locks = new ConcurrentHashMap<>();

    /** 在对应沙箱键的互斥锁内执行操作并返回结果。 */
    public <T> T withLock(SandboxKey key, Supplier<T> operation) {
        ReentrantLock lock = locks.computeIfAbsent(key, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return operation.get();
        } finally {
            lock.unlock();
        }
    }
}
```

Implement `SandboxStatusResponse` and `SandboxExecResponse` as records:

```java
package io.agentscope.sandboxservice.dto;

import io.agentscope.sandboxservice.service.SandboxLifecycleStatus;
import java.time.Instant;

/** 返回沙箱生命周期状态和底层 Docker 运行信息。 */
public record SandboxStatusResponse(
        String userId,
        String sessionId,
        SandboxLifecycleStatus status,
        boolean running,
        String containerId,
        String containerName,
        boolean snapshotRestorable,
        String workspaceRoot,
        Instant createdAt,
        Instant updatedAt) {}
```

```java
package io.agentscope.sandboxservice.dto;

/** 返回沙箱内命令执行结果。 */
public record SandboxExecResponse(
        int exitCode, String stdout, String stderr, String output, boolean truncated) {}
```

- [ ] **Step 4: Implement lifecycle service**

Implement `SandboxLifecycleService` with these public methods and comments:

```java
public SandboxStatusResponse create(SandboxKey key)
public SandboxStatusResponse start(SandboxKey key)
public SandboxStatusResponse stop(SandboxKey key)
public SandboxStatusResponse close(SandboxKey key)
public Optional<SandboxStatusResponse> status(SandboxKey key)
public SandboxExecResponse exec(SandboxKey key, String command, Integer timeoutSeconds)
public SandboxRuntime requireRunning(SandboxKey key)
```

Implementation details:

- Use `Map<SandboxKey, SandboxRuntime> runtimes = new ConcurrentHashMap<>()`.
- Build `WorkspaceSpec` with root from `properties.getDocker().getWorkspaceRoot()`.
- Build `DockerSandboxClientOptions` with image, workspaceRoot, network, memory, cpu from properties.
- If `repository.find(key)` returns a record with `sandboxStateJson`, call `client.deserializeState(json)` and `client.resume(state)`.
- If no record exists, call `client.create(workspaceSpec, snapshotSpec, options)`.
- After `sandbox.start()`, store `new SandboxRuntime(sandbox)` in the runtime map and save `RUNNING`.
- `stop` only affects in-memory runtime. If no runtime exists but a record exists, return status from record.
- `close` removes runtime from the map. If runtime does not exist but record exists, resume the sandbox and call `shutdown()`.
- Use `Instant.now()` for `createdAt` only when no existing record exists; preserve existing `createdAt` on later saves.
- Convert `ExecResult` to `SandboxExecResponse`.

- [ ] **Step 5: Run lifecycle tests**

Run: `mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=SandboxLifecycleServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit Task 3**

Run:

```bash
git add agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/dto agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/error agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/service
git commit -m "feat: manage sandbox lifecycle"
```

---

### Task 4: File Tool Service

**Files:**
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxFilesystemOperations.java`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/HarnessSandboxFilesystemOperations.java`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxFileToolService.java`
- Create: DTOs:
  - `FileReadResponse.java`
  - `FileWriteRequest.java`
  - `FileEditRequest.java`
  - `FileMoveRequest.java`
  - `FileUploadRequest.java`
  - `FileDownloadResponse.java`
  - `FileExistsResponse.java`
  - `FileListResponse.java`
  - `FileGlobResponse.java`
  - `FileGrepResponse.java`
- Test: `agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/service/SandboxFileToolServiceTest.java`

**Interfaces:**
- Consumes:
  - `SandboxLifecycleService#requireRunning(SandboxKey key)`
  - `SandboxFilesystemOperations`
- Produces:
  - `interface SandboxFilesystemOperations`
  - `class HarnessSandboxFilesystemOperations implements SandboxFilesystemOperations`
  - `FileReadResponse read(SandboxKey key, String path, int offset, int limit)`
  - `void write(SandboxKey key, String path, String content)`
  - `void edit(SandboxKey key, String path, String oldString, String newString, boolean replaceAll)`
  - `FileListResponse list(SandboxKey key, String path)`
  - `FileExistsResponse exists(SandboxKey key, String path)`
  - `FileGlobResponse glob(SandboxKey key, String path, String pattern)`
  - `FileGrepResponse grep(SandboxKey key, String path, String pattern, String glob)`
  - `void delete(SandboxKey key, String path)`
  - `void move(SandboxKey key, String fromPath, String toPath)`
  - `void upload(SandboxKey key, String path, String base64Content)`
  - `FileDownloadResponse download(SandboxKey key, String path)`

- [ ] **Step 1: Write failing file service tests**

Create `SandboxFileToolServiceTest.java` with a fake `SandboxFilesystemOperations`. This keeps service tests focused on request validation, base64 handling, error conversion, and response mapping; `HarnessSandboxFilesystemOperations` is covered by integration tests.

```java
package io.agentscope.sandboxservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.agentscope.harness.agent.filesystem.model.EditResult;
import io.agentscope.harness.agent.filesystem.model.FileData;
import io.agentscope.harness.agent.filesystem.model.FileDownloadResponse;
import io.agentscope.harness.agent.filesystem.model.FileUploadResponse;
import io.agentscope.harness.agent.filesystem.model.GlobResult;
import io.agentscope.harness.agent.filesystem.model.GrepResult;
import io.agentscope.harness.agent.filesystem.model.LsResult;
import io.agentscope.harness.agent.filesystem.model.ReadResult;
import io.agentscope.harness.agent.filesystem.model.WriteResult;
import io.agentscope.sandboxservice.config.SandboxServiceProperties;
import io.agentscope.sandboxservice.dto.FileReadResponse;
import io.agentscope.sandboxservice.error.SandboxServiceException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SandboxFileToolServiceTest {

/** 验证 write 后 read 可以通过文件工具服务读回内容。 */
@Test
void writeThenReadUsesRunningSandboxFilesystem() {
    SandboxFileToolService service = serviceWithFakeRuntime();
    SandboxKey key = SandboxKey.of("alice", "conv-1");

    service.write(key, "/workspace/a.txt", "hello\n");
    FileReadResponse response = service.read(key, "/workspace/a.txt", 0, 20);

    assertThat(response.content()).isEqualTo("hello\n");
    assertThat(response.encoding()).isEqualTo("utf-8");
}

/** 验证 upload 和 download 使用 base64 包装二进制内容。 */
@Test
void uploadThenDownloadReturnsBase64Content() {
    SandboxFileToolService service = serviceWithFakeRuntime();
    SandboxKey key = SandboxKey.of("alice", "conv-1");

    service.upload(key, "/workspace/data.bin", "aGVsbG8K");
    FileDownloadResponse response = service.download(key, "/workspace/data.bin");

    assertThat(response.base64Content()).isEqualTo("aGVsbG8K");
}

/** 验证不存在的文件读取失败会转成 FILE_OPERATION_FAILED。 */
@Test
void readFailureThrowsFileOperationError() {
    SandboxFileToolService service = serviceWithFakeRuntime();

    assertThatThrownBy(() -> service.read(SandboxKey.of("alice", "conv-1"), "/workspace/missing.txt", 0, 20))
            .isInstanceOf(SandboxServiceException.class)
            .hasMessageContaining("File operation failed");
}

private SandboxFileToolService serviceWithFakeRuntime() {
    SandboxServiceProperties properties = new SandboxServiceProperties();
    return new SandboxFileToolService(new FakeFilesystemOperations(), properties);
}

static class FakeFilesystemOperations implements SandboxFilesystemOperations {
    final Map<String, byte[]> files = new HashMap<>();

    public ReadResult read(SandboxKey key, String path, int offset, int limit) {
        byte[] content = files.get(path);
        if (content == null) {
            return ReadResult.fail("file_not_found");
        }
        return ReadResult.success(new FileData(new String(content, StandardCharsets.UTF_8), "utf-8"));
    }

    public WriteResult write(SandboxKey key, String path, String content) {
        files.put(path, content.getBytes(StandardCharsets.UTF_8));
        return WriteResult.ok(path);
    }

    public EditResult edit(SandboxKey key, String path, String oldString, String newString, boolean replaceAll) {
        return EditResult.ok(path, 1);
    }

    public LsResult list(SandboxKey key, String path) {
        return LsResult.success(List.of());
    }

    public boolean exists(SandboxKey key, String path) {
        return files.containsKey(path);
    }

    public GlobResult glob(SandboxKey key, String path, String pattern) {
        return GlobResult.success(List.of());
    }

    public GrepResult grep(SandboxKey key, String path, String pattern, String glob) {
        return GrepResult.success(List.of());
    }

    public WriteResult delete(SandboxKey key, String path) {
        files.remove(path);
        return WriteResult.ok(path);
    }

    public WriteResult move(SandboxKey key, String fromPath, String toPath) {
        files.put(toPath, files.remove(fromPath));
        return WriteResult.ok(toPath);
    }

    public List<FileUploadResponse> upload(SandboxKey key, String path, byte[] content) {
        files.put(path, content);
        return List.of(FileUploadResponse.success(path));
    }

    public List<FileDownloadResponse> download(SandboxKey key, String path) {
        byte[] content = files.get(path);
        if (content == null) {
            return List.of(FileDownloadResponse.fail(path, "file_not_found"));
        }
        return List.of(FileDownloadResponse.success(path, content));
    }
}
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=SandboxFileToolServiceTest test`

Expected: FAIL because file tool service and DTOs are missing.

- [ ] **Step 3: Implement file DTOs**

Use Java records with comments. Required records:

```java
public record FileReadResponse(String path, String content, String encoding) {}
public record FileDownloadResponse(String path, String base64Content) {}
public record FileExistsResponse(String path, boolean exists) {}
public record FileListResponse(List<FileInfo> entries) {}
public record FileGlobResponse(List<FileInfo> entries) {}
public record FileGrepResponse(List<GrepMatch> matches) {}
public record FileWriteRequest(String userId, String sessionId, String path, String content) {}
public record FileEditRequest(String userId, String sessionId, String path, String oldString, String newString, boolean replaceAll) {}
public record FileMoveRequest(String userId, String sessionId, String fromPath, String toPath) {}
public record FileUploadRequest(String userId, String sessionId, String path, String base64Content) {}
```

Import `io.agentscope.harness.agent.filesystem.model.FileInfo` and `GrepMatch` for list/glob/grep responses.

- [ ] **Step 4: Implement filesystem operations adapter**

Create `SandboxFilesystemOperations.java`:

```java
package io.agentscope.sandboxservice.service;

import io.agentscope.harness.agent.filesystem.model.EditResult;
import io.agentscope.harness.agent.filesystem.model.FileDownloadResponse;
import io.agentscope.harness.agent.filesystem.model.FileUploadResponse;
import io.agentscope.harness.agent.filesystem.model.GlobResult;
import io.agentscope.harness.agent.filesystem.model.GrepResult;
import io.agentscope.harness.agent.filesystem.model.LsResult;
import io.agentscope.harness.agent.filesystem.model.ReadResult;
import io.agentscope.harness.agent.filesystem.model.WriteResult;
import java.util.List;

/** 隔离文件工具服务和 Harness 文件系统代理，便于单元测试替换底层实现。 */
public interface SandboxFilesystemOperations {

    /** 读取沙箱内文件内容。 */
    ReadResult read(SandboxKey key, String path, int offset, int limit);

    /** 写入沙箱内新文件。 */
    WriteResult write(SandboxKey key, String path, String content);

    /** 编辑沙箱内已有文本文件。 */
    EditResult edit(SandboxKey key, String path, String oldString, String newString, boolean replaceAll);

    /** 列出沙箱内目录内容。 */
    LsResult list(SandboxKey key, String path);

    /** 判断沙箱内路径是否存在。 */
    boolean exists(SandboxKey key, String path);

    /** 按文件名模式查找沙箱内文件。 */
    GlobResult glob(SandboxKey key, String path, String pattern);

    /** 在沙箱内按文本内容搜索文件。 */
    GrepResult grep(SandboxKey key, String path, String pattern, String glob);

    /** 删除沙箱内路径。 */
    WriteResult delete(SandboxKey key, String path);

    /** 移动沙箱内路径。 */
    WriteResult move(SandboxKey key, String fromPath, String toPath);

    /** 上传单个文件到沙箱。 */
    List<FileUploadResponse> upload(SandboxKey key, String path, byte[] content);

    /** 从沙箱下载单个文件。 */
    List<FileDownloadResponse> download(SandboxKey key, String path);
}
```

Create `HarnessSandboxFilesystemOperations.java`:

```java
package io.agentscope.sandboxservice.service;

import io.agentscope.harness.agent.filesystem.sandbox.SandboxBackedFilesystem;
import io.agentscope.harness.agent.filesystem.model.EditResult;
import io.agentscope.harness.agent.filesystem.model.FileDownloadResponse;
import io.agentscope.harness.agent.filesystem.model.FileUploadResponse;
import io.agentscope.harness.agent.filesystem.model.GlobResult;
import io.agentscope.harness.agent.filesystem.model.GrepResult;
import io.agentscope.harness.agent.filesystem.model.LsResult;
import io.agentscope.harness.agent.filesystem.model.ReadResult;
import io.agentscope.harness.agent.filesystem.model.WriteResult;
import java.util.List;
import java.util.Map;

/** 把文件工具调用委托给当前运行沙箱绑定的 Harness 文件系统代理。 */
public class HarnessSandboxFilesystemOperations implements SandboxFilesystemOperations {

    private final SandboxLifecycleService lifecycleService;

    /** 创建 Harness 文件系统适配器。 */
    public HarnessSandboxFilesystemOperations(SandboxLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    /** 返回当前业务键对应的运行态文件系统代理。 */
    private SandboxBackedFilesystem filesystem(SandboxKey key) {
        return lifecycleService.requireRunning(key).filesystem();
    }

    public ReadResult read(SandboxKey key, String path, int offset, int limit) {
        return filesystem(key).read(null, path, offset, limit);
    }

    public WriteResult write(SandboxKey key, String path, String content) {
        return filesystem(key).write(null, path, content);
    }

    public EditResult edit(SandboxKey key, String path, String oldString, String newString, boolean replaceAll) {
        return filesystem(key).edit(null, path, oldString, newString, replaceAll);
    }

    public LsResult list(SandboxKey key, String path) {
        return filesystem(key).ls(null, path);
    }

    public boolean exists(SandboxKey key, String path) {
        return filesystem(key).exists(null, path);
    }

    public GlobResult glob(SandboxKey key, String path, String pattern) {
        return filesystem(key).glob(null, pattern, path);
    }

    public GrepResult grep(SandboxKey key, String path, String pattern, String glob) {
        return filesystem(key).grep(null, pattern, path, glob);
    }

    public WriteResult delete(SandboxKey key, String path) {
        return filesystem(key).delete(null, path);
    }

    public WriteResult move(SandboxKey key, String fromPath, String toPath) {
        return filesystem(key).move(null, fromPath, toPath);
    }

    public List<FileUploadResponse> upload(SandboxKey key, String path, byte[] content) {
        return filesystem(key).uploadFiles(null, List.of(Map.entry(path, content)));
    }

    public List<FileDownloadResponse> download(SandboxKey key, String path) {
        return filesystem(key).downloadFiles(null, List.of(path));
    }
}
```

Register `HarnessSandboxFilesystemOperations` as a bean in `SandboxServiceConfig`.

- [ ] **Step 5: Implement file service**

Implementation rules:

- Validate `path` is non-blank and starts with `properties.getDocker().getWorkspaceRoot()`.
- Decode upload base64 using `Base64.getDecoder()`.
- Encode download bytes using `Base64.getEncoder()`.
- For `write`, `edit`, `delete`, `move`, inspect operation result `isSuccess()` and throw `SandboxServiceException.fileOperation(...)` when false.
- For `read`, return `FileReadResponse(path, read.fileData().content(), read.fileData().encoding())`.
- For `list`, call `filesystemOperations.list(key, path)` and return `LsResult.entries()`.
- For `glob`, call `filesystemOperations.glob(key, path, pattern)` and return `GlobResult.matches()`.
- For `grep`, call `filesystemOperations.grep(key, path, pattern, glob)` and return `GrepResult.matches()`.
- For upload/download, use one file per request in the first version.

- [ ] **Step 6: Run file service tests**

Run: `mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=SandboxFileToolServiceTest test`

Expected: PASS.

- [ ] **Step 7: Commit Task 4**

Run:

```bash
git add agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxFilesystemOperations.java agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/HarnessSandboxFilesystemOperations.java agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/service/SandboxFileToolService.java agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/dto agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/service/SandboxFileToolServiceTest.java
git commit -m "feat: expose sandbox file operations service"
```

---

### Task 5: REST Controllers And Error Handling

**Files:**
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/controller/SandboxController.java`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/controller/SandboxFileController.java`
- Create DTOs:
  - `SandboxKeyRequest.java`
  - `SandboxCreateRequest.java`
  - `SandboxExecRequest.java`
  - `ErrorResponse.java`
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/error/SandboxExceptionHandler.java`
- Test: `agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/controller/SandboxControllerTest.java`
- Test: `agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/controller/SandboxFileControllerTest.java`

**Interfaces:**
- Consumes:
  - `SandboxLifecycleService`
  - `SandboxFileToolService`
- Produces REST endpoints:
  - `POST /api/sandboxes`
  - `POST /api/sandboxes/start`
  - `POST /api/sandboxes/stop`
  - `DELETE /api/sandboxes`
  - `GET /api/sandboxes/status`
  - `POST /api/sandboxes/exec`
  - all `/api/sandboxes/files/*` endpoints from the spec

- [ ] **Step 1: Write failing MVC tests**

Create `SandboxControllerTest.java` using `@WebMvcTest(SandboxController.class)` and `@MockitoBean SandboxLifecycleService`:

```java
/** 验证 start API 使用 userId 和 sessionId 启动沙箱并返回状态。 */
@Test
void startReturnsSandboxStatus() throws Exception {
    given(service.start(SandboxKey.of("alice", "conv-1")))
            .willReturn(new SandboxStatusResponse("alice", "conv-1", SandboxLifecycleStatus.RUNNING, true, "container-1", "sandbox-1", false, "/workspace", Instant.parse("2026-09-02T10:00:00Z"), Instant.parse("2026-09-02T10:01:00Z")));

    mvc.perform(post("/api/sandboxes/start")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"alice\",\"sessionId\":\"conv-1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RUNNING"))
            .andExpect(jsonPath("$.containerId").value("container-1"));
}

/** 验证 exec API 返回命令 exitCode 和输出，而不是把非业务异常吞掉。 */
@Test
void execReturnsCommandOutput() throws Exception {
    given(service.exec(SandboxKey.of("alice", "conv-1"), "pwd", 30))
            .willReturn(new SandboxExecResponse(0, "/workspace\n", "", "/workspace\n", false));

    mvc.perform(post("/api/sandboxes/exec")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"alice\",\"sessionId\":\"conv-1\",\"command\":\"pwd\",\"timeoutSeconds\":30}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.exitCode").value(0))
            .andExpect(jsonPath("$.stdout").value("/workspace\n"));
}
```

Create `SandboxFileControllerTest.java` using `@WebMvcTest(SandboxFileController.class)` and `@MockitoBean SandboxFileToolService`. Cover `read`, `write`, `edit`, `list`, `exists`, `glob`, `grep`, `delete`, `move`, `upload`, `download`.

Example:

```java
/** 验证 read API 把查询参数传给文件工具服务。 */
@Test
void readFileReturnsContent() throws Exception {
    given(fileService.read(SandboxKey.of("alice", "conv-1"), "/workspace/a.txt", 0, 20))
            .willReturn(new FileReadResponse("/workspace/a.txt", "hello", "utf-8"));

    mvc.perform(get("/api/sandboxes/files/read")
            .param("userId", "alice")
            .param("sessionId", "conv-1")
            .param("path", "/workspace/a.txt")
            .param("offset", "0")
            .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("hello"));
}
```

- [ ] **Step 2: Run controller tests to verify they fail**

Run: `mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=SandboxControllerTest,SandboxFileControllerTest test`

Expected: FAIL because controllers and request DTOs are missing.

- [ ] **Step 3: Implement request DTOs with validation annotations**

Records:

```java
public record SandboxKeyRequest(@NotBlank String userId, @NotBlank String sessionId) {}
public record SandboxCreateRequest(@NotBlank String userId, @NotBlank String sessionId) {}
public record SandboxExecRequest(@NotBlank String userId, @NotBlank String sessionId, @NotBlank String command, Integer timeoutSeconds) {}
public record ErrorResponse(String code, String message, String details, String userId, String sessionId) {}
```

Add comments to each record explaining API purpose.

- [ ] **Step 4: Implement `SandboxController`**

Controller methods:

```java
@PostMapping
public SandboxStatusResponse create(@Valid @RequestBody SandboxCreateRequest request)

@PostMapping("/start")
public SandboxStatusResponse start(@Valid @RequestBody SandboxKeyRequest request)

@PostMapping("/stop")
public SandboxStatusResponse stop(@Valid @RequestBody SandboxKeyRequest request)

@DeleteMapping
public SandboxStatusResponse close(@Valid @RequestBody SandboxKeyRequest request)

@GetMapping("/status")
public ResponseEntity<SandboxStatusResponse> status(@RequestParam String userId, @RequestParam String sessionId)

@PostMapping("/exec")
public SandboxExecResponse exec(@Valid @RequestBody SandboxExecRequest request)
```

- [ ] **Step 5: Implement `SandboxFileController`**

Map exactly the routes from the spec:

```java
@GetMapping("/read")
@PutMapping("/write")
@PatchMapping("/edit")
@GetMapping("/list")
@GetMapping("/exists")
@GetMapping("/glob")
@GetMapping("/grep")
@DeleteMapping
@PostMapping("/move")
@PostMapping("/upload")
@GetMapping("/download")
```

Each method constructs `SandboxKey.of(userId, sessionId)` and delegates to `SandboxFileToolService`.

- [ ] **Step 6: Implement unified exception handling**

`SandboxServiceException` must include:

```java
public enum Code {
    INVALID_REQUEST,
    SANDBOX_NOT_FOUND,
    SANDBOX_START_FAILED,
    SANDBOX_EXEC_FAILED,
    SANDBOX_EXEC_TIMEOUT,
    FILE_OPERATION_FAILED,
    STATE_STORE_FAILED
}
```

`SandboxExceptionHandler` maps:

- `MethodArgumentNotValidException`, `MissingServletRequestParameterException`, `IllegalArgumentException` to 400.
- `SandboxServiceException` to its configured HTTP status.
- other `Exception` to 500 with code `SANDBOX_EXEC_FAILED`.

- [ ] **Step 7: Run controller tests**

Run: `mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dtest=SandboxControllerTest,SandboxFileControllerTest test`

Expected: PASS.

- [ ] **Step 8: Commit Task 5**

Run:

```bash
git add agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/controller agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/dto agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice/error agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/controller
git commit -m "feat: expose sandbox service rest api"
```

---

### Task 6: Optional Docker Integration Test

**Files:**
- Create: `agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/integration/DockerSandboxServiceIntegrationTest.java`

**Interfaces:**
- Consumes public Spring API from Tasks 1-5.
- Produces optional Docker-backed verification enabled by `-Dsandbox.integration.docker=true`.

- [ ] **Step 1: Write disabled-by-default integration test**

Create `DockerSandboxServiceIntegrationTest.java`:

```java
package io.agentscope.sandboxservice.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.sandboxservice.dto.FileReadResponse;
import io.agentscope.sandboxservice.dto.SandboxExecResponse;
import io.agentscope.sandboxservice.service.SandboxFileToolService;
import io.agentscope.sandboxservice.service.SandboxKey;
import io.agentscope.sandboxservice.service.SandboxLifecycleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("docker")
@SpringBootTest(properties = {
        "sandbox-service.state-dir=${java.io.tmpdir}/agentscope-sandbox-service-it/state",
        "sandbox-service.snapshot-dir=${java.io.tmpdir}/agentscope-sandbox-service-it/snapshots"
})
@EnabledIfSystemProperty(named = "sandbox.integration.docker", matches = "true")
class DockerSandboxServiceIntegrationTest {

    @Autowired SandboxLifecycleService lifecycleService;
    @Autowired SandboxFileToolService fileToolService;

    /** 验证真实 Docker 沙箱能执行命令、保存快照、关闭后再次恢复文件。 */
    @Test
    void restoresWorkspaceFromSnapshotAfterCloseAndRestart() {
        SandboxKey key = SandboxKey.of("it-user", "it-session");
        lifecycleService.start(key);
        SandboxExecResponse exec = lifecycleService.exec(key, "echo restored > /workspace/restored.txt", 30);
        assertThat(exec.exitCode()).isZero();

        lifecycleService.stop(key);
        lifecycleService.close(key);
        lifecycleService.start(key);

        FileReadResponse read = fileToolService.read(key, "/workspace/restored.txt", 0, 10);
        assertThat(read.content()).contains("restored");

        lifecycleService.close(key);
    }
}
```

- [ ] **Step 2: Run default tests and verify Docker test is skipped**

Run: `mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am test`

Expected: PASS; Docker integration test skipped because `sandbox.integration.docker` is not set.

- [ ] **Step 3: Run optional Docker integration test when Docker is available**

Run only on a machine where Docker is installed and `ubuntu:24.04` can be pulled:

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -Dsandbox.integration.docker=true -Dtest=DockerSandboxServiceIntegrationTest test
```

Expected: PASS; command creates a file, `stop` persists snapshot, `close` removes container, later `start` restores file.

- [ ] **Step 4: Commit Task 6**

Run:

```bash
git add agentscope-examples/agents/agentscope-sandbox-service/src/test/java/io/agentscope/sandboxservice/integration
git commit -m "test: add optional docker sandbox service integration"
```

---

### Task 7: README And Usage Examples

**Files:**
- Create: `agentscope-examples/agents/agentscope-sandbox-service/README.md`

**Interfaces:**
- Consumes REST API from Tasks 5-6.
- Produces Chinese user documentation for running and calling the service.

- [ ] **Step 1: Draft README**

Create `README.md` in Chinese. Include:

- 服务定位：独立 Spring Boot Docker 沙箱服务，不使用 `ReactAgent`。
- 启动命令:

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am spring-boot:run
```

- 配置说明 for `sandbox-service.state-dir`, `snapshot-dir`, `docker.image`, `docker.workspace-root`, `docker.network`, `docker.memory-size-bytes`, `docker.cpu-count`, `exec.default-timeout-seconds`.
- `curl` examples for:
  - `POST /api/sandboxes/start`
  - `POST /api/sandboxes/exec`
  - `PUT /api/sandboxes/files/write`
  - `GET /api/sandboxes/files/read`
  - `POST /api/sandboxes/stop`
  - `DELETE /api/sandboxes`
- 安全说明:
  - 默认网络关闭。
  - 不挂载 Docker socket。
  - 生产环境必须放在受控内网或前置鉴权网关之后。

- [ ] **Step 2: Verify README commands match implemented routes**

Run: `rg -n "/api/sandboxes|sandbox-service\\.|spring-boot:run" agentscope-examples/agents/agentscope-sandbox-service/README.md agentscope-examples/agents/agentscope-sandbox-service/src/main/java/io/agentscope/sandboxservice`

Expected: README mentions routes that exist in controller annotations.

- [ ] **Step 3: Commit Task 7**

Run:

```bash
git add agentscope-examples/agents/agentscope-sandbox-service/README.md
git commit -m "docs: document sandbox service usage"
```

---

### Task 8: Final Verification

**Files:**
- Modify only if verification reveals a concrete defect in files from Tasks 1-7.

**Interfaces:**
- Consumes all previous task outputs.
- Produces final validated module.

- [ ] **Step 1: Run formatting and whitespace checks for changed files**

Run:

```bash
git diff --check
```

Expected: no trailing whitespace or patch formatting errors.

- [ ] **Step 2: Run full module test suite**

Run:

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am test
```

Expected: PASS with Docker integration skipped by default.

- [ ] **Step 3: Run compile check for example aggregator**

Run:

```bash
mvn -pl agentscope-examples/agents/agentscope-sandbox-service -am -DskipTests compile
```

Expected: PASS.

- [ ] **Step 4: Inspect git diff for unrelated changes**

Run:

```bash
git status --short
git diff --stat
```

Expected: only files from this plan are modified by the implementation. Pre-existing files from before this plan may still appear; do not stage or revert them unless the user explicitly asks.

- [ ] **Step 5: Final commit**

If Task 8 required fixes, commit them:

```bash
git add agentscope-examples/pom.xml agentscope-examples/agents/agentscope-sandbox-service
git commit -m "chore: verify sandbox service module"
```

If no fixes were needed and all previous tasks already committed, no final commit is necessary.
