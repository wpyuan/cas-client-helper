# cas-client-helper
基于`spring`的`cas`认证客户端辅助工具。简单判断请求是否通过cas认证，没则自动跳转cas认证页。

## 引入依赖
maven
```xml
<dependency>
    <groupId>com.github.wpyuan</groupId>
    <artifactId>cas-client-helper</artifactId>
    <version>0.0.1</version>
</dependency>
```
gradle
```
implementation 'com.github.wpyuan:cas-client-helper:0.0.1'
```

## 准备配置数据

假设有个`cas`服务发布在`https://cas`

客户端发布在`https://localhost:8080`

则准备以下数据，供客户端判断当前请求是否`cas`认证

| 参数 | 说明 | 示例 |
| ---- | ---- | ---- |
| casServerUrlPrefix | cas服务地址前缀 | https://cas/sso |
| casServerLoginUrl | cas登录地址 | https://cas/sso/login |
| serverName | 客户端服务地址 | https://localhost:8080 |

## 编写客户端客制化认证处理

继承抽象类`AbstractCasClientAuthenticationFilter`，示例：
```java
public class CasClientAuthFilter extends AbstractCasClientAuthenticationFilter {

    /**
     * 前置监听，顺序0（数字越小执行顺序越靠前）
     *
     * @param request  请求
     * @param response 响应
     * @return 是否继续执行后续逻辑，否则直接返回
     */
    @Override
    public boolean before(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        // 方案一：可以不覆盖此方法，默认所有请求走下面的判断是否cas认证逻辑
        // 方案二：可以覆盖此方法，指定的请求走cas认证逻辑
        if (...) {
            // 请求符合某些条件则走cas认证
            return true;
        }
        // 请求不符合则不走cas认证
        return false;
    }

    /**
     * 加载时监听，这里必须装配{@link DefaultCasClientConfig}配置，顺序1（数字越小执行顺序越靠前）
     *
     * @param request  请求
     * @param response 响应
     * @return cas配置
     * @throws ServletException
     * @throws IOException
     */
    @Override
    public DefaultCasClientConfig load(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        // 将上述 casServerUrlPrefix、casServerLoginUrl、serverName 存放在DefaultCasClientConfig对象对应属性里，并返回
        return new DefaultCasClientConfig().toBuilder()
                .casServerUrlPrefix(casServerUrlPrefix)
                .casServerLoginUrl(casServerLoginUrl)
                .serverName(serverName)
                .build();

    }

    /**
     * 当前请求已通过cas认证，则执行该方法
     * @param request
     * @param response
     * @param assertion the successful Assertion from the server.
     * @return 可返回加工后的request
     */
    @Override
    public HttpServletRequest onSuccessfulValidation(HttpServletRequest request, HttpServletResponse response, Assertion assertion) {
        // cas认证通过后会执行此方法
    }

    @Override
    public void onFailedValidation(HttpServletRequest request, HttpServletResponse response) {
        // cas认证失败后会执行此方法
    }
}
```

> `DefaultCasClientConfig`为配置类，存放上述配置`casServerUrlPrefix`等数据

## 配置过滤器使其生效
上述处理器`CasClientAuthFilter`实际上是过滤器，在`spring`中进行配置才能生效，配置方式很多，不一样举例，需要注意的是，过滤器的执行顺序即可。

下面介绍下如果项目使用了`spring security`，即客户端本身有自己的认证。这种情况下，需要客户端自行维护自己的登录状态，则建议cas认证处理在`security`用户名密码认证处理之前：

```java
public void configure(HttpSecurity http) throws Exception {
    // ... 省略security配置
    CasClientAuthFilter casClientAuthFilter = new CasClientAuthFilter();
    // 添加cas客户端认证到security过滤器执行链，顺序建议是”用户名密码认证“之前，这样就不走原有项目的登录认证，先走cas认证，然后在cas客户端认证处理器中处理本系统的登录状态
    http.addFilterBefore(casClientAuthFilter, UsernamePasswordAuthenticationFilter.class);
}
```
