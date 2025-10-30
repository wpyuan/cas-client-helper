package com.github.wpyuan.casclienthelper.filter;

import com.github.wpyuan.casclienthelper.config.DefaultCasClientConfig;
import lombok.extern.slf4j.Slf4j;
import com.github.wpyuan.casclienthelper.utill.CommonUtils;
import org.jasig.cas.client.validation.Assertion;
import org.jasig.cas.client.validation.Cas20ProxyTicketValidator;
import org.jasig.cas.client.validation.TicketValidationException;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

import static org.jasig.cas.client.util.AbstractCasFilter.CONST_CAS_ASSERTION;

/**
 * <p>
 * </p>
 *
 * @author wangpeiyuan
 * @date 2021/5/7 10:44
 */
@Slf4j
public abstract class AbstractCasClientAuthenticationFilter extends OncePerRequestFilter {

    private DefaultCasClientConfig defaultCasClientConfig;

    /**
     * 前置监听，顺序0（数字越小执行顺序越靠前）
     *
     * @param request  请求
     * @param response 响应
     * @return 是否继续执行后续逻辑，否则直接返回
     */
    public boolean before(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        return true;
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
    public abstract DefaultCasClientConfig load(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (!this.before(request, response)) {
            chain.doFilter(request, response);
            return;
        }
        this.defaultCasClientConfig = this.load(request, response);
        if (this.defaultCasClientConfig == null) {
            log.warn("casConfig can not be null");
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        Assertion assertion = session != null ? (Assertion) session.getAttribute(CONST_CAS_ASSERTION) : null;
        final String serviceUrl = CommonUtils.constructServiceUrl(request, response, null, this.defaultCasClientConfig.getServerName(),
                this.defaultCasClientConfig.getArtifactParameterName(), this.defaultCasClientConfig.isAppendPort(), this.defaultCasClientConfig.isEncodeServiceUrl());
        String ticket = CommonUtils.safeGetParameter(request, this.defaultCasClientConfig.getArtifactParameterName());
        if (CommonUtils.isNotBlank(ticket) && request.getCookies() == null) {
            // cas服务端登录成功后跳转携带的ticket，已经失效了，此时要作废，否则会报错“org.jasig.cas.client.validation.TicketValidationException: 票根'ST-XX'不符合目标服务”
            ticket = null;
        }
        final boolean wasGatewayed = this.defaultCasClientConfig.isGateway() && this.defaultCasClientConfig.getGatewayStorage().hasGatewayedAlready(request, serviceUrl);
        if (assertion == null && CommonUtils.isBlank(ticket) && !wasGatewayed) {
            log.debug("no ticket and no assertion found");
            if (this.defaultCasClientConfig.isRedirectAuthUrl()) {
                final String modifiedServiceUrl;

                if (this.defaultCasClientConfig.isGateway()) {
                    log.debug("setting isGateway() attribute in session");
                    modifiedServiceUrl = this.defaultCasClientConfig.getGatewayStorage().storeGatewayInformation(request, serviceUrl);
                } else {
                    modifiedServiceUrl = serviceUrl;
                }

                log.debug("Constructed service url: {}", modifiedServiceUrl);

                final String urlToRedirectTo = CommonUtils.constructRedirectUrl(this.defaultCasClientConfig.getCasServerLoginUrl(),
                        this.defaultCasClientConfig.getServiceParameterName(), modifiedServiceUrl, this.defaultCasClientConfig.isRenew(), this.defaultCasClientConfig.isGateway());

                log.debug("redirecting to \"{}\"", urlToRedirectTo);
                this.defaultCasClientConfig.getAuthenticationRedirectStrategy().redirect(request, response, urlToRedirectTo);
                return;
            }

            chain.doFilter(request, response);
            return;
        }

        if (assertion != null && assertion.getPrincipal() != null) {
            request = onSuccessfulValidation(request, response, assertion);
            chain.doFilter(request, response);
            return;
        }

        if (!preFilter(request, response)) {
            return;
        }

        Cas20ProxyTicketValidator ticketValidator = new Cas20ProxyTicketValidator(this.defaultCasClientConfig.getCasServerUrlPrefix());

        if (CommonUtils.isNotBlank(ticket)) {
            log.debug("Attempting to validate ticket: {}", ticket);

            try {
                assertion = ticketValidator.validate(ticket,
                        CommonUtils.constructServiceUrl(request, response, null, this.defaultCasClientConfig.getServerName(),
                                this.defaultCasClientConfig.getArtifactParameterName(), this.defaultCasClientConfig.isEncodeServiceUrl()));

                log.debug("Successfully authenticated user: {}", assertion.getPrincipal().getName());

                request.setAttribute(CONST_CAS_ASSERTION, assertion);

                if (this.defaultCasClientConfig.isUseSession()) {
                    request.getSession().setAttribute(CONST_CAS_ASSERTION, assertion);
                }
                request = onSuccessfulValidation(request, response, assertion);

                if (this.defaultCasClientConfig.isRedirectAfterValidation()) {
                    log.debug("Redirecting after successful ticket validation.");
                    response.sendRedirect(CommonUtils.constructServiceUrl(request, response, null, this.defaultCasClientConfig.getServerName(),
                            this.defaultCasClientConfig.getArtifactParameterName(), this.defaultCasClientConfig.isEncodeServiceUrl()));
                    return;
                }
            } catch (final TicketValidationException e) {
                log.debug(e.getMessage(), e);

                onFailedValidation(request, response, e);

                if (this.defaultCasClientConfig.isExceptionOnValidationFailure()) {
                    throw new ServletException(e.getMessage(), e);
                }
                return;
            }
        }

        chain.doFilter(request, response);
    }


    /**
     * This processes the ProxyReceptor request before the ticket validation code executes.
     */
    protected final boolean preFilter(final HttpServletRequest request, final HttpServletResponse response) throws IOException, ServletException {
        final String requestUri = request.getRequestURI();

        if (CommonUtils.isEmpty(this.defaultCasClientConfig.getProxyReceptorUrl()) || !requestUri.endsWith(this.defaultCasClientConfig.getProxyReceptorUrl())) {
            return true;
        }

        CommonUtils.readAndRespondToProxyReceptorRequest(request, response, this.defaultCasClientConfig.getProxyGrantingTicketStorage());

        return false;
    }


    /**
     * Template method that gets executed if ticket validation succeeds.  Override if you want additional behavior to occur
     * if ticket validation succeeds.  This method is called after all ValidationFilter processing required for a successful authentication
     * occurs.
     *
     * @param request   the HttpServletRequest.
     * @param response  the HttpServletResponse.
     * @param assertion the successful Assertion from the server.
     * @return 可返回加工后的request
     */
    public abstract HttpServletRequest onSuccessfulValidation(HttpServletRequest request, HttpServletResponse response, final Assertion assertion);


    /**
     * Template method that gets executed if validation fails.  This method is called right after the exception is caught from the ticket validator
     * but before any of the processing of the exception occurs.
     *
     * @param request  the HttpServletRequest.
     * @param response the HttpServletResponse.
     */
    public abstract void onFailedValidation(HttpServletRequest request, HttpServletResponse response, TicketValidationException e);
}
