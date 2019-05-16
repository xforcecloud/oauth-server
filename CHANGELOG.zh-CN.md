# Changelog

这个项目的所有显著变化都将被记录在这个文件中。

## [0.12.0] - 2018-12-12

### 新增

- 新增删除token的内部接口

### 修复

- 修复oauth登入重定向问题

### 移除

- 移除zipkin依赖及相关配置
- 移除hystrix-stream依赖
- 移除kafka依赖及相关配置

## [0.11.0] - 2018-11-23

### 新增

- 添加单元测试
- 忘记密码页添加密码策略校验
- 新增客户端授权模式

### 修改

- 升级`choerodon-starter`依赖版本为`0.7.0.RELEASE`
- 修改`/api/user`接口，客户端授权时`UserDetails`中添加客户端信息
- 修改了ci文件
- 更新了基础镜像
- token关联了session

## [0.10.0] - 2018-09-27

### 新增

- 支持oracle数据库
- 添加找回密码功能
- 支持ie10以上的浏览器

### 修改

- 更新license 
- 修改了ci文件
- 更新了基础镜像
- 重新设计了登陆界面

## [0.9.0] - 2018-08-17

### 修改

- 修改`choerodon-framework-parent` 依赖版本为`0.8.0`。
- 修改`choerodon-starter-parent` 依赖版本为`0.6.0`。

## [0.8.3] - 2018-08-03

### 修改

- 修复了移动端登录页重定向问题，该问题会导致移动端无法登录。

## [0.8.2] - 2018-08-01

### 新增

- 添加`choerodon.oauth.login.ssl` 配置，该配置用于支持`choerodon.oauth.login.path` 协议为`https` 时的处理。默认为`false`。

### 修改

- 修改`choerodon.oauth.login.domain`为`choerodon.oauth.login.path` 。

## [0.8.1] - 2018-07-26

### 修改

- 修改`choerodon.oauth.loginPage.domain`为`choerodon.oauth.login.domain` 。

## [0.8.0] - 2018-07-19

### 新增

- 添加`choerodon.oauth.loginPage.domain` 配置，改配置可以指定`/login` 页面的地址，支持`https`。eg.:`https://api.example.com/oauth/login`
- 添加用户登出接口，并移除用户redis里的session
- 添加https支持
 
### 修改

- 修改了移动端登陆页面
- security放开管理端口url
- 优化了点击登陆后密码位数变长的问题
- 优化登陆页面为不使用缓存



## [0.7.0] - 2018-06-22

### 修改

- 升级了chart中dbtool的版本为0.5.2。
- 登录页面用户被锁定时不再显示验证码。
- 修改前端传输密码方式为密文传输。
- 修改登录用户名或密码为空时的报错信息。
- 修复登录错误次数计数错误的问题。
- 升级choerodon-starter依赖版本为0.5.3.RELEASE。

## [0.6.0] - 2018-06-08

### 新增

- 添加redis作为存储登录session，用于保证认证服务开启多实例时的用户会话。
- customUserDetails中设置了is_admin的值。

### 修改

- Ldap 用户登录逻辑修改。