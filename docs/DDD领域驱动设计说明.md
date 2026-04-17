# DDD领域驱动设计 — 四层架构设计说明

本文档描述基于 **JDK 25 + Spring Boot 4.0.5 + DDD** 四层架构的底座设计，旨在为团队提供清晰的架构理解和开发规范。

> 基线：JDK 25 + Spring Boot 4.0.5 + Spring Cloud Alibaba 2025.1.0.0 + MyBatis-Flex 1.11.6 + Redisson 4.3.1
> 更新日期：2026-04

---

## 1. DDD 四层架构概述

DDD 四层架构将系统划分为四个职责清晰的层次，遵循**依赖倒置**和**关注点分离**原则，使领域逻辑与基础设施解耦，便于测试和演进。

### 1.1 架构分层图（ASCII）

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        接口层 (pandora-api)                                      │
│  REST 控制器、全局异常处理、Swagger 配置、请求/响应转换                         │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       应用层 (pandora-application)                               │
│  应用服务、Command/DTO、编排领域逻辑、事务边界、定义基础设施抽象接口               │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       领域层 (pandora-domain)                                     │
│  聚合根、值对象、领域事件、仓储接口（契约）、领域服务                             │
│  ★ 核心层，不依赖任何上层 ★                                                    │
└─────────────────────────────────────────────────────────────────────────────┘
                                      ▲
                                      │
┌─────────────────────────────────────────────────────────────────────────────┐
│                    基础设施层 (pandora-infrastructure)                            │
│  仓储实现、Mapper、DO/JPA 实体、外部服务实现、配置类                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 各层职责与原则

| 层次 | 职责 | 核心原则 |
|------|------|----------|
| **接口层** | 接收 HTTP 请求、参数校验、调用应用服务、返回统一响应 | 薄控制器，不包含业务逻辑 |
| **应用层** | 编排用例、事务管理、调用领域对象和仓储、发布领域事件 | 不包含业务规则，只做流程编排 |
| **领域层** | 承载核心业务逻辑、不变量验证、领域事件定义 | 纯净领域，不依赖框架和基础设施 |
| **基础设施层** | 持久化、缓存、消息、外部 API 等技术实现 | 实现领域层和应用层定义的接口 |

---

## 2. 各层在本项目中的具体体现

### 2.1 领域层 (pandora-domain)

领域层是 DDD 的核心，包含聚合根、值对象、领域事件和仓储接口，**不依赖任何上层模块**。

#### 2.1.1 聚合根：User.java

`User` 是用户聚合的根实体，负责维护业务不变量，通过 `create`、`changePassword`、`disable`、`enable` 等方法封装领域逻辑。

```java
// pandora-domain/.../user/User.java
@Getter
public class User extends AggregateRoot<Long> {

    private Long id;
    private String username;
    private String password;
    private String email;
    private String phone;
    private UserStatus status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 创建用户（工厂方法）—— 业务不变量：用户名非空、邮箱格式等由 Command 层校验 */
    public static User create(String username, String password, String email, String phone) {
        User user = new User();
        user.username = username;
        user.password = password;
        user.email = email;
        user.phone = phone;
        user.status = UserStatus.ACTIVE;
        user.createTime = LocalDateTime.now();
        user.updateTime = LocalDateTime.now();
        user.registerEvent(new UserCreatedEvent(user.username, user.email));
        return user;
    }

    public void changePassword(String oldPassword, String newPassword) {
        if (!this.password.equals(oldPassword)) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }
        this.password = newPassword;
        this.updateTime = LocalDateTime.now();
    }

    public void disable() {
        this.status = UserStatus.DISABLED;
        this.updateTime = LocalDateTime.now();
    }

    public void enable() {
        this.status = UserStatus.ACTIVE;
        this.updateTime = LocalDateTime.now();
    }
}
```

#### 2.1.2 值对象：Email.java（建议实现）

值对象是不可变的，用于表达领域概念并封装格式校验。本项目当前在 Command 层使用 `@Email` 校验，可进一步在领域层引入 `Email` 值对象以强化领域表达：

```java
// 建议：pandora-domain/.../user/Email.java
@Getter
public final class Email {

    private final String value;

    private Email(String value) {
        this.value = value;
    }

    public static Email of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        String regex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        if (!value.matches(regex)) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        return new Email(value);
    }
}
```

#### 2.1.3 仓储接口：UserRepository.java

仓储接口定义在领域层，由基础设施层实现，实现**依赖倒置**：

```java
// pandora-domain/.../user/UserRepository.java
public interface UserRepository extends Repository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
```

#### 2.1.4 领域事件：UserCreatedEvent.java

领域事件记录聚合内发生的业务事实，可被事件监听器捕获并触发后续流程（如发送欢迎邮件、同步到其他系统）：

```java
// pandora-domain/.../user/event/UserCreatedEvent.java
@Getter
public class UserCreatedEvent extends DomainEvent {

    private final String username;
    private final String email;

    public UserCreatedEvent(String username, String email) {
        super();
        this.username = username;
        this.email = email;
    }
}
```

---

### 2.2 应用层 (pandora-application)

应用层负责用例编排、事务边界和 DTO 转换，不包含核心业务规则。

#### 2.2.1 应用服务：UserApplicationService.java

编排领域逻辑，处理事务，调用仓储和领域事件发布：

```java
// pandora-application/.../user/UserApplicationService.java
@Service
public class UserApplicationService {

    private final UserRepository userRepository;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public UserDTO createUser(CreateUserCommand command) {
        if (userRepository.existsByUsername(command.getUsername())) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS, "用户名已被注册");
        }
        if (userRepository.existsByEmail(command.getEmail())) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS, "邮箱已被注册");
        }

        User user = User.create(
                command.getUsername(),
                command.getPassword(),
                command.getEmail(),
                command.getPhone()
        );

        user = userRepository.save(user);
        domainEventPublisher.publishAll(user.getDomainEvents());
        user.clearDomainEvents();

        return UserAssembler.toDTO(user);
    }
}
```

#### 2.2.2 命令对象：CreateUserCommand、ChangePasswordCommand

CQRS 命令模式，承载写操作的输入参数和校验注解：

```java
// CreateUserCommand.java
@Data
public class CreateUserCommand {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3-50个字符之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在6-100个字符之间")
    private String password;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;
}
```

#### 2.2.3 DTO：UserDTO

与领域模型隔离的传输对象，用于接口层与应用层之间的数据传递：

```java
// UserDTO.java
@Data
public class UserDTO {

    private Long id;
    private String username;
    private String email;
    private String phone;
    private String status;
    private LocalDateTime createTime;
}
```

#### 2.2.4 基础设施抽象接口：FileService、AiService

应用层定义能力接口，基础设施层实现，实现依赖倒置：

```java
// FileService.java（应用层定义）
public interface FileService {
    String upload(MultipartFile file);
    InputStream download(String objectName);
    void delete(String objectName);
    // ...
}

// AiService.java（应用层定义）
public interface AiService {
    String chat(String userMessage);
    String chat(String userMessage, String systemPrompt);
    String summarize(String text);
    String translate(String text, String targetLanguage);
}
```

---

### 2.3 基础设施层 (pandora-infrastructure)

基础设施层实现领域层和应用层定义的接口，负责持久化、外部服务等技术细节。

#### 2.3.1 仓储实现：UserRepositoryImpl

实现 `UserRepository` 接口，通过 `UserConverter` 完成领域对象与数据对象的转换：

```java
// UserRepositoryImpl.java
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;

    @Override
    public User save(User user) {
        UserDO dataObject = UserConverter.toDataObject(user);
        if (dataObject.getId() == null) {
            userMapper.insert(dataObject);
            user.assignId(dataObject.getId());
        } else {
            userMapper.updateById(dataObject);
        }
        return user;
    }

    @Override
    public Optional<User> findById(Long id) {
        UserDO dataObject = userMapper.selectById(id);
        return Optional.ofNullable(UserConverter.toDomainModel(dataObject));
    }
    // ...
}
```

#### 2.3.2 数据对象：UserDO

与领域模型分离的数据库映射对象，使用 **MyBatis-Flex** 注解：

```java
// UserDO.java
import com.mybatisflex.annotation.*;

@Data
@Table("sys_user")
public class UserDO {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String username;
    private String password;
    private String email;
    private String phone;
    private Integer status;

    // 由 InsertListener 自动填充
    private LocalDateTime createTime;

    // 由 InsertListener / UpdateListener 自动填充
    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Integer deleted;
}
```

> **从 MyBatis-Plus 到 MyBatis-Flex 的注解迁移：**
>
> | MyBatis-Plus | MyBatis-Flex |
> |---|---|
> | `@TableName("sys_user")` | `@Table("sys_user")` |
> | `@TableId(type = IdType.AUTO)` | `@Id(keyType = KeyType.Auto)` |
> | `@TableField(fill = FieldFill.INSERT)` | 改由 `InsertListener` 实现自动填充 |
> | `@TableField(fill = FieldFill.INSERT_UPDATE)` | 改由 `UpdateListener` 实现自动填充 |
> | `@TableLogic` | `@Column(isLogicDelete = true)` |

#### 2.3.3 MyBatis-Flex Mapper：UserMapper

```java
import com.mybatisflex.core.BaseMapper;

@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}
```

分页查询示例：

```java
Page<UserDO> page = mapper.paginate(
    Page.of(pageNum, pageSize),
    QueryWrapper.create().where(USER_DO.STATUS.eq(1))
);
```

#### 2.3.4 JPA 实体与仓储：UserJpaEntity、UserJpaRepository

支持多 ORM 共存，JPA 可用于部分场景：

```java
@Entity
@Table(name = "sys_user")
public class UserJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // ...
}

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {
    Optional<UserJpaEntity> findByEmail(String email);
}
```

#### 2.3.5 外部服务实现

- **MinioFileService**：实现 `FileService` 接口，提供 MinIO 文件存储
- **AiChatService**：实现 `AiService` 接口，提供 AI 对话能力
- **Config 配置类**：MybatisFlexConfig、MinioConfig、RedissonConfig、SecurityConfig 等

---

### 2.4 接口层 (pandora-api)

接口层负责接收 HTTP 请求、参数校验、调用应用服务并返回统一响应。

#### 2.4.1 REST 控制器

- **UserController**：用户 CRUD、修改密码、禁用/启用
- **FileController**：文件上传、下载
- **AiController**：AI 对话、摘要、翻译

```java
// UserController.java
@Tag(name = "用户管理", description = "用户增删改查接口")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserApplicationService userApplicationService;

    @Operation(summary = "创建用户")
    @PostMapping
    public Result<UserDTO> createUser(@Valid @RequestBody CreateUserCommand command) {
        UserDTO user = userApplicationService.createUser(command);
        return Result.success("用户创建成功", user);
    }
}
```

#### 2.4.2 全局异常处理：GlobalExceptionHandler

统一处理业务异常、系统异常、参数校验异常等：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.failure(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        // 参数校验失败处理
    }
}
```

#### 2.4.3 Swagger 配置：SwaggerConfig

基于 SpringDoc OpenAPI 3 的 API 文档配置，支持 Bearer Token 认证。

---

## 3. 数据流转示例：创建用户

以「创建用户」为例，完整请求流转如下：

```
┌──────────────┐     ┌─────────────────────┐     ┌─────────────────────┐
│   Client     │────▶│  UserController     │────▶│ UserApplication     │
│  POST /api/  │     │  @Valid CreateUser   │     │ Service.createUser  │
│  users       │     │  Command             │     │                     │
└──────────────┘     └─────────────────────┘     └──────────┬──────────┘
                                                             │
                                                             ▼
┌──────────────┐     ┌─────────────────────┐     ┌─────────────────────┐
│   Database   │◀────│ UserRepositoryImpl  │◀────│ User.create()       │
│   sys_user   │     │ UserMapper.insert   │     │ UserRepository.save │
└──────────────┘     └─────────────────────┘     └──────────┬──────────┘
                                                             │
                                                             ▼
                                              ┌─────────────────────────────┐
                                              │ DomainEventPublisher        │
                                              │ .publishAll(UserCreatedEvent)│
                                              └─────────────────────────────┘
                                                             │
                                                             ▼
                                              ┌─────────────────────────────┐
                                              │ UserAssembler.toDTO(user)   │
                                              │ Result.success(UserDTO)     │
                                              └─────────────────────────────┘
```

**步骤说明：**

1. **Controller**：接收 `CreateUserCommand`，`@Valid` 触发参数校验，调用 `UserApplicationService.createUser`
2. **ApplicationService**：检查用户名/邮箱唯一性，调用 `User.create()` 创建聚合根
3. **User.create()**：构造 `User`，注册 `UserCreatedEvent`
4. **UserRepository.save()**：`UserRepositoryImpl` 将 `User` 转为 `UserDO`，通过 `UserMapper` 写入数据库
5. **DomainEventPublisher**：发布 `UserCreatedEvent`，由 `UserEventHandler` 异步消费
6. **UserAssembler.toDTO()**：将 `User` 转为 `UserDTO`，封装为 `Result` 返回

---

## 4. 领域事件流转

### 4.1 发布流程

1. **聚合根注册事件**：`User.create()` 中调用 `registerEvent(new UserCreatedEvent(...))`
2. **持久化后发布**：`UserApplicationService` 在 `userRepository.save(user)` 之后调用 `domainEventPublisher.publishAll(user.getDomainEvents())`
3. **基础设施实现**：`SpringDomainEventPublisher` 实现 `DomainEventPublisher`，将领域事件转发给 Spring `ApplicationEventPublisher`

```java
// SpringDomainEventPublisher.java
@Override
public void publish(DomainEvent event) {
    applicationEventPublisher.publishEvent(event);
}
```

### 4.2 消费流程

`UserEventHandler` 使用 `@EventListener` 监听 `UserCreatedEvent`，`@Async` 实现异步处理：

```java
@Async
@EventListener
public void handleUserCreated(UserCreatedEvent event) {
    log.info("处理用户创建事件: username={}, email={}, eventId={}",
            event.getUsername(), event.getEmail(), event.getEventId());
    // 可扩展：发送欢迎邮件、初始化用户配置、同步到其他系统等
}
```

### 4.2 流转示意

```
User.create()                    UserApplicationService              SpringDomainEventPublisher
     │                                    │                                    │
     │ registerEvent(UserCreatedEvent)    │                                    │
     │──────────────────────────────────▶│                                    │
     │                                    │ publishAll(events)                 │
     │                                    │───────────────────────────────────▶│
     │                                    │                                    │ publishEvent()
     │                                    │                                    │───────────────▶
     │                                    │                                    │                │
     │                                    │                                    │   UserEventHandler
     │                                    │                                    │   @EventListener
     │                                    │                                    │   handleUserCreated()
```

---

## 5. 依赖倒置原则

### 5.1 设计意图

- **领域层定义接口**：`UserRepository`、`DomainEventPublisher` 等在领域层声明
- **基础设施层实现接口**：`UserRepositoryImpl`、`SpringDomainEventPublisher` 在基础设施层实现

这样领域层**不依赖**具体数据库、消息队列等技术实现，可独立测试和演进。

### 5.2 依赖方向

```
领域层 (定义 UserRepository 接口)
    ▲
    │ 实现
    │
基础设施层 (UserRepositoryImpl 实现 UserRepository)
```

应用层同样定义 `FileService`、`AiService` 等接口，由基础设施层的 `MinioFileService`、`AiChatService` 实现，实现「面向接口编程」。

---

## 6. 模块间的依赖方向

```
                    ┌─────────────┐
                    │  pandora-api    │  接口层
                    └──────┬──────┘
                           │ 依赖
                           ▼
                    ┌─────────────────────┐
                    │ pandora-application     │  应用层
                    └──────┬──────────────┘
                           │ 依赖
                           ▼
                    ┌─────────────────────┐
                    │ pandora-domain          │  领域层（核心，无外部依赖）
                    └──────▲──────────────┘
                           │ 实现/依赖
          ┌────────────────┴────────────────┐
          │                                  │
   ┌──────┴──────────┐              ┌───────┴──────────┐
   │ pandora-infrastructure│              │ pandora-common       │
   │ 实现 UserRepository │              │ 工具类、异常等   │
   │ 实现 FileService   │              └─────────────────┘
   │ 实现 AiService     │
   └───────────────────┘
```

**依赖规则总结：**

| 模块 | 可依赖 | 不可依赖 |
|------|--------|----------|
| **pandora-api** | pandora-application, pandora-common | pandora-domain, pandora-infrastructure |
| **pandora-application** | pandora-domain, pandora-common | pandora-api, pandora-infrastructure（仅依赖接口，不依赖实现包） |
| **pandora-domain** | 无（仅 JDK、Lombok 等基础库） | 其他业务模块 |
| **pandora-infrastructure** | pandora-domain, pandora-application, pandora-common | pandora-api |

---

## 附录：相关文档

- [架构设计说明](architecture.md) — 技术栈、模块职责、端口规划
- [DDD 分层规范](ddd-specification.md) — 各层代码规范与扩展指南
- [快速开始](getting-started.md) — 环境要求与启动步骤

---

> **作者**：vahoa  
> **日期**：2026 年  
> **作品**：pandora-arch · DDD 架构底座  
> **版权**：© 2026 vahoa. All rights reserved.
