# 多数据源与多 ORM 使用指南

> 基线：JDK 25 + Spring Boot 4.0.5 + MyBatis-Flex 1.11.6 + dynamic-datasource 4.5.0 + Spring Data JPA

## 1. 多数据源架构概述

本项目采用 **`dynamic-datasource-spring-boot4-starter` 4.5.0** 实现多数据源动态切换：

- **主从架构**：支持 master/slave 主从读写分离
- **灵活扩展**：支持任意数量的数据源动态切换
- **连接池**：结合 HikariCP（Spring Boot BOM 锁定版本）
- **Starter 版本化**：Boot 4 必须使用 `dynamic-datasource-spring-boot4-starter`（旧版 `-spring-boot3-starter` 已不兼容）

### 架构示意

```
┌──────────────────────────────────────────────────────────────┐
│                    Application Layer                         │
├──────────────────────────────────────────────────────────────┤
│  @DS("master")  │  @DS("slave")  │  @DS("report")           │
├──────────────────────────────────────────────────────────────┤
│           Dynamic DataSource (dynamic-datasource 4.5.0)      │
├──────────────┬──────────────┬────────────────────────────────┤
│   master     │    slave     │         report                 │
│  (HikariCP)  │  (HikariCP)  │       (HikariCP)              │
└──────────────┴──────────────┴────────────────────────────────┘
```

---

## 2. 配置方法

### 2.1 Maven 依赖

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>dynamic-datasource-spring-boot4-starter</artifactId>
    <version>4.5.0</version>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>9.6.0</version>
    <scope>runtime</scope>
</dependency>
```

### 2.2 application.yml

```yaml
spring:
  datasource:
    dynamic:
      primary: master
      strict: false
      datasource:
        master:
          url: jdbc:mysql://localhost:3306/ddd_platform?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
          username: root
          password: 123456
          driver-class-name: com.mysql.cj.jdbc.Driver
          hikari:
            minimum-idle: 5
            maximum-pool-size: 20
        slave:
          url: jdbc:mysql://localhost:3306/ddd_platform?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
          username: root
          password: 123456
          driver-class-name: com.mysql.cj.jdbc.Driver
          hikari:
            minimum-idle: 5
            maximum-pool-size: 20
```

---

## 3. 使用方法

### 3.1 @DS 注解

| 注解 | 说明 |
|------|------|
| `@DS("master")` | 指定使用 master 数据源 |
| `@DS("slave")` | 指定使用 slave 数据源 |
| 无注解 | 使用 `primary` 配置的默认数据源（master） |

### 3.2 Service 层

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    public UserDO getById(Long id) {
        return userMapper.selectOneById(id);
    }

    @DS("slave")
    public UserDO getByIdFromSlave(Long id) {
        return userMapper.selectOneById(id);
    }

    @DS("master")
    @Transactional(rollbackFor = Exception.class)
    public void saveUser(UserDO user) {
        userMapper.insert(user);
    }
}
```

### 3.3 Mapper 层

```java
import com.mybatisflex.core.BaseMapper;

@Mapper
public interface UserMapper extends BaseMapper<UserDO> {

    @DS("master")
    int insertSelective(UserDO user);

    @DS("slave")
    UserDO selectByPhone(@Param("phone") String phone);
}
```

### 3.4 动态切换示例

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;

    public OrderDO getOrder(Long orderId, boolean needLatest) {
        return needLatest
            ? getOrderFromMaster(orderId)
            : getOrderFromSlave(orderId);
    }

    @DS("master")
    public OrderDO getOrderFromMaster(Long orderId) {
        return orderMapper.selectOneById(orderId);
    }

    @DS("slave")
    public OrderDO getOrderFromSlave(Long orderId) {
        return orderMapper.selectOneById(orderId);
    }
}
```

---

## 4. 多 ORM 共存（MyBatis-Flex + Spring Data JPA）

本项目同时支持 **MyBatis-Flex**（主）和 **Spring Data JPA**（辅），按场景选型。

> **重要**：本项目已从 MyBatis-Plus 迁移到 **MyBatis-Flex 1.11.6**，注解与 API 见下文。

### 4.1 MyBatis-Flex 使用方式

#### 实体与 Mapper

```java
import com.mybatisflex.annotation.*;

@Data
@Table("sys_user")
public class UserDO {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String username;
    private String email;

    private LocalDateTime createTime;   // 由 InsertListener 自动填充
    private LocalDateTime updateTime;   // 由 InsertListener / UpdateListener 自动填充

    @Column(isLogicDelete = true)
    private Integer deleted;
}
```

```java
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.paginate.Page;

@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}

// 分页示例：
Page<UserDO> page = userMapper.paginate(
    Page.of(pageNum, pageSize),
    QueryWrapper.create().eq("status", 1).orderBy("id", false)
);
```

#### 全局配置 `MybatisFlexConfig`

```java
@Configuration
@MapperScan("cn.pandora.infrastructure.persistence.mapper")
public class MybatisFlexConfig {

    @Bean
    InsertListener insertListener() {
        return entity -> {
            if (entity instanceof BaseEntity b) {
                LocalDateTime now = LocalDateTime.now();
                b.setCreateTime(now);
                b.setUpdateTime(now);
                LoginUser u = LoginUserHolder.get();
                if (u != null) {
                    b.setCreateBy(u.getUserId());
                    b.setUpdateBy(u.getUserId());
                }
            }
        };
    }

    @Bean
    UpdateListener updateListener() {
        return entity -> {
            if (entity instanceof BaseEntity b) {
                b.setUpdateTime(LocalDateTime.now());
                LoginUser u = LoginUserHolder.get();
                if (u != null) b.setUpdateBy(u.getUserId());
            }
        };
    }
}
```

#### 适用场景

- 复杂 SQL、多表关联查询
- 动态条件查询（`QueryWrapper.create().and(...).or(...)`）
- 批量插入、批量更新（`mapper.insertBatch(list)`）
- 需要精细控制 SQL 的场景

> **MyBatis-Plus → MyBatis-Flex 注解迁移表**
>
> | MyBatis-Plus | MyBatis-Flex |
> |---|---|
> | `@TableName("t")` | `@Table("t")` |
> | `@TableId(type = IdType.AUTO)` | `@Id(keyType = KeyType.Auto)` |
> | `@TableField("col")` | `@Column("col")` |
> | `@TableField(fill = FieldFill.INSERT)` | `InsertListener` |
> | `@TableField(fill = FieldFill.INSERT_UPDATE)` | `UpdateListener` |
> | `@TableLogic` | `@Column(isLogicDelete = true)` |
> | `com.baomidou.mybatisplus.core.mapper.BaseMapper` | `com.mybatisflex.core.BaseMapper` |
> | `MybatisPlusInterceptor + InnerInterceptor` | 标准 `org.apache.ibatis.plugin.Interceptor` |
> | `LambdaQueryWrapper.eq(E::f, v)` | `QueryWrapper.create().eq("f", v)`（或打开 `mybatis-flex-processor` 使用 TableDef 类型安全） |

### 4.2 Spring Data JPA 使用方式

#### 实体定义

```java
@Entity
@jakarta.persistence.Table(name = "sys_user")
@Data
public class UserJpaEntity {
    @jakarta.persistence.Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String email;
    private LocalDateTime createTime;
}
```

> 注意：Spring Boot 4 / Framework 7 下 JPA 使用 **jakarta.persistence.\*** 包。

#### Repository

```java
@Repository
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long>,
        JpaSpecificationExecutor<UserJpaEntity> {
    List<UserJpaEntity> findByUsername(String username);
    Optional<UserJpaEntity> findByEmail(String email);
}
```

#### application.yml 中 JPA 配置

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: none    # 必须 none，避免 JPA 自动建表与 MyBatis-Flex 冲突
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
```

#### 适用场景

- 简单 CRUD
- 强类型查询、方法名派生
- 领域模型映射（DDD 风格）
- 与 Spring Data 生态（Pageable / Specification）结合

### 4.3 共存原理

```
┌──────────────────────────────────────────────────────────────┐
│                      @DS 注解                                 │
│           （在 JDBC DataSource 层面切换）                       │
└─────────────────────────────────┬────────────────────────────┘
                                  │
          ┌───────────────────────┴────────────────────┐
          │ Dynamic DataSource (AbstractRoutingDataSource) │
          └───────────────────────┬────────────────────┘
                                  │
          ┌──────────────┬────────┴────────┬────────────┐
          ▼              ▼                 ▼            ▼
┌────────────────────┐ ┌──────────────────────┐
│ SqlSessionFactory  │ │ EntityManagerFactory │
│ (MyBatis-Flex)    │ │   (Spring Data JPA)  │
└────────────────────┘ └──────────────────────┘
```

- 两个 ORM 共享同一个 dynamic DataSource
- `@DS` 在 JDBC DataSource 层面切换，对 MyBatis-Flex 和 JPA 均生效
- JPA 必须 `ddl-auto: none`，由 SQL 脚本（或 Flyway/Liquibase）管理表结构

### 4.4 选型建议

| 场景 | 推荐 ORM | 原因 |
|------|---------|------|
| 简单 CRUD | JPA | 零 SQL，方法名派生查询 |
| 复杂动态查询 | MyBatis-Flex | `QueryWrapper` + `TableDef` 灵活 |
| 多表关联 | MyBatis-Flex | 原生 XML / `QueryWrapper` 可控 |
| 领域模型映射 | JPA | `@Entity` 天然贴合 DDD |
| 批量数据操作 | MyBatis-Flex | `insertBatch` 性能更优 |

---

## 5. 新增数据源

### 5.1 添加 report 报表库

```yaml
spring:
  datasource:
    dynamic:
      primary: master
      strict: false
      datasource:
        master:  { url: jdbc:mysql://localhost:3306/ddd_platform?..., username: root, password: 123456, driver-class-name: com.mysql.cj.jdbc.Driver }
        slave:   { url: jdbc:mysql://localhost:3306/ddd_platform?..., username: root, password: 123456, driver-class-name: com.mysql.cj.jdbc.Driver }
        report:
          url: jdbc:mysql://localhost:3306/report_db?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
          username: report_user
          password: report_pwd
          driver-class-name: com.mysql.cj.jdbc.Driver
```

### 5.2 使用新数据源

```java
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper reportMapper;

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

---

## 6. 注意事项

### 6.1 事务与 @DS 的交互

- **@DS 生效时机**：在进入方法时确定数据源，整个事务期间使用同一数据源
- **注解优先级**：方法上的 `@DS` > 类上的 `@DS` > `primary` 默认数据源

```java
@Service
@DS("slave")
public class UserService {

    @DS("master")
    @Transactional
    public void updateUser(UserDO user) {
        // 整个事务使用 master
    }

    public UserDO getById(Long id) {
        // 使用类级别 slave
        return userMapper.selectOneById(id);
    }
}
```

### 6.2 跨数据源事务限制

- **不支持跨数据源分布式事务**：同一 `@Transactional` 方法内只能操作一个数据源
- 若需跨库操作，应拆分为多个 Service 方法；或引入 Seata 等分布式事务方案

### 6.3 MyBatis-Flex 与 JPA 映射同一张表

| 注意点 | 说明 |
|--------|-----|
| 表结构一致性 | 两边字段、类型、命名需与数据库一致 |
| 主键策略 | `@Id(keyType = KeyType.Auto)` 与 `@GeneratedValue` 协调，统一使用数据库自增 |
| 避免并发写 | 同一记录不建议同时用两种 ORM 写 |
| ddl-auto | JPA 必须 `none`，表结构由 SQL 脚本或 Flyway/Liquibase 管理 |

---

## 附录：完整依赖示例

```xml
<dependencies>
    <!-- 多数据源（Boot 4 专用） -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>dynamic-datasource-spring-boot4-starter</artifactId>
        <version>4.5.0</version>
    </dependency>

    <!-- MyBatis-Flex（Boot 4 专用） -->
    <dependency>
        <groupId>com.mybatis-flex</groupId>
        <artifactId>mybatis-flex-spring-boot4-starter</artifactId>
        <version>1.11.6</version>
    </dependency>

    <!-- Spring Data JPA（可选，与 MyBatis-Flex 共存） -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- MySQL 9 驱动 -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <version>9.6.0</version>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

---

> **作者**：vahoa  
> **日期**：2026 年  
> **作品**：pandora-arch · DDD 架构底座  
> **版权**：© 2026 vahoa. All rights reserved.
