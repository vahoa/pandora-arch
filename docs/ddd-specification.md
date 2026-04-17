# DDD 分层规范与扩展指南

> 基线：JDK 25 + Spring Boot 4.0.5 + MyBatis-Flex 1.11.6 + Redisson 4.3.1

## 分层架构总览

```
┌──────────────────────────────────────────────┐
│           接口层 (pandora-api)                    │
│   Controller / VO / 全局异常 / Swagger        │
├──────────────────────────────────────────────┤
│           应用层 (pandora-application)             │
│   ApplicationService / Command / DTO          │
├──────────────────────────────────────────────┤
│           领域层 (pandora-domain)                  │
│   AggregateRoot / Entity / ValueObject        │
│   DomainEvent / Repository接口                │
├──────────────────────────────────────────────┤
│           基础设施层 (pandora-infrastructure)       │
│   Repository实现 / Mapper / DO / Redis        │
│   Security / Event发布                        │
└──────────────────────────────────────────────┘
```

**依赖方向**：上层依赖下层，领域层不依赖任何其他层（依赖倒置）。

## 领域层（pandora-domain）

领域层是整个架构的核心，承载业务规则和领域知识，**不依赖任何框架和基础设施**。

### 聚合根（AggregateRoot）

```java
public class User extends AggregateRoot<Long> {

    // 状态变更必须通过聚合根的方法
    public static User create(String username, String password, String email, String phone) {
        User user = new User();
        // ... 设置属性
        user.registerEvent(new UserCreatedEvent(username, email));
        return user;
    }

    public void changePassword(String oldPassword, String newPassword) {
        if (!this.password.equals(oldPassword)) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }
        this.password = newPassword;
    }
}
```

规范要点：
- 继承 `AggregateRoot<ID>`
- 通过工厂方法或构造器创建（`create()`）
- 提供 `reconstruct()` 方法从持久化数据重建
- 所有状态变更通过实例方法，内部校验业务规则
- 通过 `registerEvent()` 注册领域事件

### 实体（Entity）

```java
public abstract class Entity<ID extends Serializable> implements Serializable {
    public abstract ID getId();
    // 通过 ID 判断相等性
}
```

### 值对象（ValueObject）

```java
public class UserId implements ValueObject {
    private final Long value;
    // 不可变，通过属性值判断相等性
}
```

### 领域事件（DomainEvent）

```java
public class UserCreatedEvent extends DomainEvent {
    private final String username;
    private final String email;
}
```

### 仓储接口（Repository）

```java
public interface UserRepository extends Repository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
}
```

仓储接口定义在领域层，实现在基础设施层，遵循依赖倒置原则。

## 应用层（pandora-application）

应用层负责**编排领域逻辑**，不包含业务规则。

### 应用服务

```java
@Service
public class UserApplicationService {

    @Transactional
    public UserDTO createUser(CreateUserCommand command) {
        // 1. 校验唯一性约束（应用层关注点）
        if (userRepository.existsByUsername(command.getUsername())) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS);
        }
        // 2. 调用领域对象的工厂方法
        User user = User.create(command.getUsername(), ...);
        // 3. 持久化
        user = userRepository.save(user);
        // 4. 发布领域事件
        domainEventPublisher.publishAll(user.getDomainEvents());
        user.clearDomainEvents();
        // 5. 返回 DTO
        return UserAssembler.toDTO(user);
    }
}
```

### Command 对象

```java
@Data
public class CreateUserCommand {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
    @Email(message = "邮箱格式不正确")
    private String email;
}
```

### Assembler 转换

```java
public class UserAssembler {
    public static UserDTO toDTO(User user) {
        // 领域模型 → DTO
    }
}
```

## 基础设施层（pandora-infrastructure）

### 仓储实现

```java
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
}
```

### 数据对象（DO）

```java
@Data
@Table("sys_user")
public class UserDO {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private String username;
    // ... 与数据库表一一对应
}
```

> 注：本项目使用 **MyBatis-Flex 1.11.6**，实体注解来自 `com.mybatisflex.annotation.*`（`@Table` / `@Id` / `@Column`）。**严禁引入 MyBatis-Plus** 的 `@TableName` / `@TableId`。

### Converter

```java
public class UserConverter {
    public static UserDO toDataObject(User user) { ... }
    public static User toDomainModel(UserDO dataObject) { ... }
}
```

DO 与领域模型严格隔离，通过 Converter 转换，避免持久化细节污染领域模型。

## 接口层（pandora-api）

### Controller

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @PostMapping
    public Result<UserDTO> createUser(@Valid @RequestBody CreateUserCommand command) {
        UserDTO user = userApplicationService.createUser(command);
        return Result.success("用户创建成功", user);
    }
}
```

Controller 只做参数接收、调用应用服务、包装返回值，不包含任何业务逻辑。

---

## 扩展新的限界上下文

以新增"订单管理"为例：

### 第一步：领域层（pandora-domain）

在 `cn.pandora.domain.order` 包下创建：
- `Order.java` — 订单聚合根
- `OrderId.java` — 订单ID值对象
- `OrderStatus.java` — 订单状态枚举
- `OrderRepository.java` — 订单仓储接口
- `event/OrderCreatedEvent.java` — 订单创建事件

### 第二步：应用层（pandora-application）

在 `cn.pandora.application.order` 包下创建：
- `OrderApplicationService.java` — 订单应用服务
- `command/CreateOrderCommand.java` — 创建订单命令
- `dto/OrderDTO.java` — 订单数据传输对象
- `assembler/OrderAssembler.java` — 转换器

### 第三步：基础设施层（pandora-infrastructure）

在 `cn.pandora.infrastructure.persistence.order` 包下创建：
- `OrderDO.java` — 订单数据对象
- `OrderMapper.java` — MyBatis-Flex `BaseMapper<OrderDO>`
- `OrderRepositoryImpl.java` — 订单仓储实现

在 `cn.pandora.infrastructure.persistence.converter` 包下创建：
- `OrderConverter.java` — DO 与领域模型转换

### 第四步：接口层（pandora-api）

在 `cn.pandora.interfaces.rest` 包下创建：
- `OrderController.java` — 订单 REST 控制器

### 第五步：数据库

在 `docs/sql/schema.sql` 中添加订单表 DDL。

### 第六步：部署

- **单体模式**：无需额外操作，`pandora-start` 自动包含新代码
- **微服务模式**：在 `pandora-service/` 下新建 `pandora-service-order` 模块，结构参照 `pandora-service-user`

---

> **作者**：vahoa  
> **日期**：2026 年  
> **作品**：pandora-arch · DDD 架构底座  
> **版权**：© 2026 vahoa. All rights reserved.
