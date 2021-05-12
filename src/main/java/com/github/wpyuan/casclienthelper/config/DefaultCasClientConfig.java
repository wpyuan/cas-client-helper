package com.github.wpyuan.casclienthelper.config;

import lombok.*;
import org.jasig.cas.client.authentication.AuthenticationRedirectStrategy;
import org.jasig.cas.client.authentication.DefaultAuthenticationRedirectStrategy;
import org.jasig.cas.client.authentication.DefaultGatewayResolverImpl;
import org.jasig.cas.client.authentication.GatewayResolver;
import org.jasig.cas.client.proxy.ProxyGrantingTicketStorage;
import org.jasig.cas.client.proxy.ProxyGrantingTicketStorageImpl;
import org.jasig.cas.client.util.AbstractCasFilter;

/**
 * <p>
 *     cas配置
 * </p>
 *
 * @author wangpeiyuan
 * @date 2021/5/6 17:10
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
public class DefaultCasClientConfig {

    private String casServerUrlPrefix;
    private String casServerLoginUrl;
    private String serverName;
    private String includeUrlMath;
    private String excludeUrlMath;

    /**
     * Defines the parameter to look for for the artifact.
     */
    private String artifactParameterName = "ticket";
    /**
     * Sets where response.encodeUrl should be called on service urls when constructed.
     */
    private boolean encodeServiceUrl = true;

    /**
     * Whether to send the gateway request or not.
     */
    private boolean gateway = false;

    private GatewayResolver gatewayStorage = new DefaultGatewayResolverImpl();

    /**
     * Defines the parameter to look for for the service.
     */
    private String serviceParameterName = "service";

    /**
     * Whether to send the renew request or not.
     */
    private boolean renew = false;
    private AuthenticationRedirectStrategy authenticationRedirectStrategy = new DefaultAuthenticationRedirectStrategy();
    /**
     * 是否重定向CAS认证页面
     */
    private boolean isRedirectAuthUrl = true;
    /**
     * The URL to send to the CAS server as the URL that will process proxying requests on the CAS client.
     */
    private String proxyReceptorUrl;

    /**
     * Storage location of ProxyGrantingTickets and Proxy Ticket IOUs.
     */
    private ProxyGrantingTicketStorage proxyGrantingTicketStorage = new ProxyGrantingTicketStorageImpl();

    /**
     * Specify whether the Assertion should be stored in a session
     * attribute {@link AbstractCasFilter#CONST_CAS_ASSERTION}.
     */
    private boolean useSession = true;

    /**
     * Specify whether the filter should redirect the user agent after a
     * successful validation to remove the ticket parameter from the query
     * string.
     */
    private boolean redirectAfterValidation = true;

    /**
     * Determines whether an exception is thrown when there is a ticket validation failure.
     */
    private boolean exceptionOnValidationFailure = false;
}
