# 多数据源与多ORM使用指南

## 1. 多数据源架构概述

本项目采用 **dynamic-datasource-spring-boot3-starter**（v4.3.0）实现多数据源动态切换，具备以下特性：

- **主从架构**：支持 master/slave 主从读写分离
- **灵活扩展**：支持任意数量的数据源动态切换
- **连接池**：结合 HikariCP 连接池，保障高性能与稳定性

### 架构示意

```
┌─────────────────────────────────────────────────────────┐
│                    Application Layer                     │
├─────────────────────────────────────────────────────────┤
│  @DS("master")  │  @DS("slave")  │  @DS("report")       │
├─────────────────────────────────────────────────────────┤
│           Dynamic DataSource (dynamic-datasource)        │
├──────────────┬──────────────┬───────────────────────────┤
│   master     │    slave     │         report            │
│  (HikariCP)  │  (HikariCP)  │       (HikariCP)          │
└──────────────┴──────────────┴───────────────────────────┘
```

---

## 2. 配置方法

### Maven 依赖

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>dynamic-datasource-spring-boot3-starter</artifactId>
    <version>4.3.0</version>
</dependency>
```

### application.yml 配置

```yaml
spring:
  datasource:
    dynamic:
      primary: master          # 默认数据源
      strict: false            # 严格模式，未匹配到数据源时是否抛异常
      datasource:
        master:
          url: jdbc:mysql://localhost:3306/ddd_platform?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
          username: root
          password: 123456
          driver-class-name: com.mysql.cj.jdbc.Driver
          hikari:
            minimum-idle: 5
            maximum-pool-size: 20
        slave:
          url: jdbc:mysql://localhost:3306/ddd_platform?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
          username: root
          password: 123456
          driver-class-name: com.mysql.cj.jdbc.Driver
          hikari:
            minimum-idle: 5
            maximum-pool-size: 20
```

---

## 3. 使用方法

### 3.1 @DS 注解说明

| 注解 | 说明 |
|------|------|
| `@DS("master")` | 指定使用 master 数据源 |
| `@DS("slave")` | 指定使用 slave 数据源 |
| 无注解 | 使用 `primary` 配置的默认数据源（master） |

### 3.2 在 Service 层使用

```java
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    // 默认使用 master 数据源
    public UserDO getById(Long id) {
        return userMapper.selectById(id);
    }

    // 指定使用 slave 数据源（读从库）
    @DS("slave")
    public UserDO getByIdFromSlave(Long id) {
        return userMapper.selectById(id);
    }

    // 写操作使用 master
    @DS("master")
    @Transactional(rollbackFor = Exception.class)
    public void saveUser(UserDO user) {
        userMapper.insert(user);
    }
}
```

### 3.3 在 Mapper 层使用

```java
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {

    @DS("master")
    int insert(UserDO user);

    @DS("slave")
    UserDO selectById(Long id);

    // 未加 @DS，使用 primary 数据源
    List<UserDO> selectList();
}
```

### 3.4 动态切换示例代码

```java
@Service
public class OrderService {

    @Autowired
    private OrderMapper orderMapper;

    // 根据业务逻辑动态选择数据源
    public OrderDO getOrder(Long orderId, boolean needLatest) {
        if (needLatest) {
            // 需要最新数据，查主库
            return getOrderFromMaster(orderId);
        } else {
            // 可接受延迟，查从库减轻主库压力
            return getOrderFromSlave(orderId);
        }
    }

    @DS("master")
    public OrderDO getOrderFromMaster(Long orderId) {
        return orderMapper.selectById(orderId);
    }

    @DS("slave")
    public OrderDO getOrderFromSlave(Long orderId) {
        return orderMapper.selectById(orderId);
    }
}
```

---

## 4. 多 ORM 共存

本项目同时支持 **MyBatis-Plus** 和 **Spring Data JPA**，可根据业务场景灵活选型。

### 4.1 MyBatis-Plus 使用方式

#### 实体与 Mapper

```java
@Data
@TableName("sys_user")
public class UserDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String email;
    private LocalDateTime createTime;
}

@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
    // 继承 BaseMapper 即拥有基础 CRUD
    // 可自定义复杂 SQL
}
```

#### 配置

```java
@Configuration
@MapperScan("com.example.mapper")
public class MyBatisPlusConfig {
    // 使用 dynamic-datasource 时，SqlSessionFactory 会自动绑定
}
```

#### 适用场景

- 复杂 SQL、多表关联查询
- 动态条件查询（LambdaQueryWrapper）
- 批量插入、批量更新
- 需要精细控制 SQL 的场景

### 4.2 JPA 使用方式

#### 实体定义

```java
@Entity
@Table(name = "sys_user")
@Data
public class UserJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String email;
    private LocalDateTime createTime;
}
```

#### Repository

```java
@Repository
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long>,
        JpaSpecificationExecutor<UserJpaEntity> {
    // 方法名派生查询
    List<UserJpaEntity> findByUsername(String username);
    Optional<UserJpaEntity> findByEmail(String email);
}
```

#### 配置

```java
@Configuration
@EnableJpaRepositories(basePackages = "com.example.jpa.repository")
@EntityScan(basePackages = "com.example.jpa.entity")
public class JpaConfig {
}
```

#### application.yml 中 JPA 配置

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: none    # 重要：设为 none，避免 JPA 自动建表与 MyBatis-Plus 冲突
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
```

#### 适用场景

- 简单 CRUD
- 强类型查询、方法名派生
- 领域模型映射（DDD 风格）

### 4.3 共存原理

```
┌────────────────────────────────────────────────────────────────┐
│                        @DS 注解                                 │
│              （在 JDBC DataSource 层面切换）                     │
└───────────────────────────┬────────────────────────────────────┘
                            │
        ┌───────────────────┴───────────────────┐
        │     Dynamic DataSource (AbstractRoutingDataSource)     │
        └───────────────────┬───────────────────────────────────┘
                            │
        ┌───────────────────┴───────────────────┐
        │                                       │
        ▼                                       ▼
┌───────────────────┐                 ┌───────────────────┐
│   SqlSessionFactory │                 │ EntityManagerFactory │
│   (MyBatis-Plus)   │                 │   (Spring Data JPA)  │
└───────────────────┘                 └───────────────────┘
```

- 两个 ORM 共享同一个 dynamic DataSource
- `@DS` 在 JDBC DataSource 层面切换，对 MyBatis-Plus 和 JPA 均生效
- JPA 必须配置 `ddl-auto: none`，避免自动建表与 MyBatis-Plus 管理的表结构冲突

### 4.4 选型建议

| 场景 | 推荐 ORM | 原因 |
|------|---------|------|
| 简单 CRUD | JPA | 零 SQL，方法名派生查询 |
| 复杂动态查询 | MyBatis-Plus | LambdaQueryWrapper 灵活 |
| 多表关联 | MyBatis-Plus | XML SQL 可控 |
| 领域模型映射 | JPA | @Entity 天然贴合 DDD |
| 批量数据操作 | MyBatis-Plus | 批量插入性能优 |

---

## 5. 新增数据源

### 5.1 添加 report 报表库

在 `application.yml` 中增加数据源配置：

```yaml
spring:
  datasource:
    dynamic:
      primary: master
      strict: false
      datasource:
        master:
          url: jdbc:mysql://localhost:3306/ddd_platform?...
          username: root
          password: 123456
          driver-class-name: com.mysql.cj.jdbc.Driver
        slave:
          url: jdbc:mysql://localhost:3306/ddd_platform?...
          username: root
          password: 123456
          driver-class-name: com.mysql.cj.jdbc.Driver
        report:                                    # 新增报表库
          url: jdbc:mysql://localhost:3306/report_db?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
          username: report_user
          password: report_pwd
          driver-class-name: com.mysql.cj.jdbc.Driver
```

### 5.2 使用新数据源

```java
@Service
public class ReportService {

    @Autowired
    private ReportMapper reportMapper;

    @DS("report")
    public List<ReportVO> getDailyReport(LocalDate date) {
        return reportMapper.selectDailyReport(date);
    }

    @DS("report")
    @Transactional(rollbackFor = Exception.class)
    public void exportReport(ReportExportDTO dto) {
        // 报表库的写操作
    }
}
```

```java
@Mapper
public interface ReportMapper extends BaseMapper<ReportDO> {

    @DS("report")
    List<ReportVO> selectDailyReport(@Param("date") LocalDate date);
}
```

---

## 6. 注意事项

### 6.1 事务与 @DS 的交互

- **@DS 生效时机**：在进入方法时确定数据源，整个事务期间使用同一数据源
- **注解优先级**：方法上的 `@DS` > 类上的 `@DS` > `primary` 默认数据源

```java
@Service
@DS("slave")  // 类级别默认
public class UserService {

    @DS("master")  // 方法级别覆盖类级别
    @Transactional
    public void updateUser(UserDO user) {
        // 整个事务使用 master
    }

    // 无方法注解时，使用类级别的 slave
    public UserDO getById(Long id) {
        return userMapper.selectById(id);
    }
}
```

### 6.2 跨数据源事务限制

- **不支持跨数据源分布式事务**：同一 `@Transactional` 方法内，只能操作一个数据源
- 若需跨库操作，应拆分为多个 Service 方法，各自加 `@DS` 和 `@Transactional`，或考虑引入 Seata 等分布式事务方案

```java
// ❌ 错误示例：同一事务内切换数据源无效
@Transactional
public void wrongExample() {
    userMapper.insert(user);   // 假设用 master
    reportMapper.insert(report); // 期望用 report，但事务已绑定 master
}

// ✅ 正确示例：拆分为独立事务
@DS("master")
@Transactional
public void saveUser(UserDO user) {
    userMapper.insert(user);
}

@DS("report")
@Transactional
public void saveReport(ReportDO report) {
    reportMapper.insert(report);
}
```

### 6.3 MyBatis-Plus 与 JPA 映射同一张表

当两种 ORM 同时操作同一张表时，需注意：

| 注意点 | 说明 |
|--------|------|
| 表结构一致性 | 两边的实体字段、类型、命名需与数据库一致，避免映射差异 |
| 主键策略 | MyBatis-Plus 的 `@TableId` 与 JPA 的 `@GeneratedValue` 需协调，建议统一使用数据库自增 |
| 避免并发写 | 同一记录不建议同时用两种 ORM 写，易产生锁或脏读 |
| ddl-auto | JPA 必须设为 `none`，由 MyBatis-Plus 或 Flyway/Liquibase 管理表结构 |

```java
// MyBatis-Plus 实体
@TableName("sys_user")
public class UserDO { ... }

// JPA 实体 - 表名、字段需与 UserDO 一致
@Entity
@Table(name = "sys_user")
public class UserJpaEntity { ... }
```

---

## 附录：完整依赖示例

```xml
<dependencies>
    <!-- 多数据源 -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>dynamic-datasource-spring-boot3-starter</artifactId>
        <version>4.3.0</version>
    </dependency>
    <!-- MyBatis-Plus -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        <version>3.5.5</version>
    </dependency>
    <!-- Spring Data JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <!-- MySQL -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```
