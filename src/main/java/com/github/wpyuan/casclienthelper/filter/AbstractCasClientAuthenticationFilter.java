package com.github.wpyuan.casclienthelper.filter;

import com.github.wpyuan.casclienthelper.config.CasConfig;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jasig.cas.client.util.CommonUtils;
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
@AllArgsConstructor
public abstract class AbstractCasClientAuthenticationFilter extends OncePerRequestFilter {

    private CasConfig casConfig;

    /**
     * 加载时监听，这里必须装配{@link com.github.wpyuan.casclienthelper.config.CasConfig}配置，顺序0（数字越小执行顺序越靠前）
     *
     * @param request  请求
     * @param response 响应
     * @param chain    过滤器链
     * @return cas配置
     */
    public abstract CasConfig load(HttpServletRequest request, HttpServletResponse response, FilterChain chain);

    /**
     * 前置监听，顺序1（数字越小执行顺序越靠前）
     *
     * @param request  请求
     * @param response 响应
     * @param chain    过滤器链
     */
    public void before(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {

    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        this.casConfig = this.load(request, response, chain);
        this.before(request, response, chain);

        HttpSession session = request.getSession(false);
        Assertion assertion = session != null ? (Assertion) session.getAttribute(CONST_CAS_ASSERTION) : null;
        final String serviceUrl = CommonUtils.constructServiceUrl(request, response, null, this.casConfig.getServerName(),
                this.casConfig.getArtifactParameterName(), this.casConfig.isEncodeServiceUrl());
        final String ticket = CommonUtils.safeGetParameter(request, this.casConfig.getArtifactParameterName());
        final boolean wasGatewayed = this.casConfig.isGateway() && this.casConfig.getGatewayStorage().hasGatewayedAlready(request, serviceUrl);
        if (assertion == null && CommonUtils.isBlank(ticket) && !wasGatewayed) {
            log.debug("no ticket and no assertion found");
            if (this.casConfig.isRedirectAuthUrl()) {
                final String modifiedServiceUrl;

                if (this.casConfig.isGateway()) {
                    log.debug("setting isGateway() attribute in session");
                    modifiedServiceUrl = this.casConfig.getGatewayStorage().storeGatewayInformation(request, serviceUrl);
                } else {
                    modifiedServiceUrl = serviceUrl;
                }

                log.debug("Constructed service url: {}", modifiedServiceUrl);

                final String urlToRedirectTo = CommonUtils.constructRedirectUrl(this.casConfig.getCasServerLoginUrl(),
                        this.casConfig.getServiceParameterName(), modifiedServiceUrl, this.casConfig.isRenew(), this.casConfig.isGateway());

                log.debug("redirecting to \"{}\"", urlToRedirectTo);
                this.casConfig.getAuthenticationRedirectStrategy().redirect(request, response, urlToRedirectTo);
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

        Cas20ProxyTicketValidator ticketValidator = new Cas20ProxyTicketValidator(this.casConfig.getCasServerUrlPrefix());

        if (CommonUtils.isNotBlank(ticket)) {
            log.debug("Attempting to validate ticket: {}", ticket);

            try {
                assertion = ticketValidator.validate(ticket,
                        CommonUtils.constructServiceUrl(request, response, null, this.casConfig.getServerName(),
                                this.casConfig.getArtifactParameterName(), this.casConfig.isEncodeServiceUrl()));

                log.debug("Successfully authenticated user: {}", assertion.getPrincipal().getName());

                request.setAttribute(CONST_CAS_ASSERTION, assertion);

                if (this.casConfig.isUseSession()) {
                    request.getSession().setAttribute(CONST_CAS_ASSERTION, assertion);
                }
                request = onSuccessfulValidation(request, response, assertion);

                if (this.casConfig.isRedirectAfterValidation()) {
                    log.debug("Redirecting after successful ticket validation.");
                    response.sendRedirect(CommonUtils.constructServiceUrl(request, response, null, this.casConfig.getServerName(),
                            this.casConfig.getArtifactParameterName(), this.casConfig.isEncodeServiceUrl()));
                    return;
                }
            } catch (final TicketValidationException e) {
                log.debug(e.getMessage(), e);

                onFailedValidation(request, response);

                if (this.casConfig.isExceptionOnValidationFailure()) {
                    throw new ServletException(e);
                }
            }
        }

        chain.doFilter(request, response);
    }


    /**
     * This processes the ProxyReceptor request before the ticket validation code executes.
     */
    protected final boolean preFilter(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        final String requestUri = request.getRequestURI();

        if (CommonUtils.isEmpty(this.casConfig.getProxyReceptorUrl()) || !requestUri.endsWith(this.casConfig.getProxyReceptorUrl())) {
            return true;
        }

        CommonUtils.readAndRespondToProxyReceptorRequest(request, response, this.casConfig.getProxyGrantingTicketStorage());

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
     */
    public abstract HttpServletRequest onSuccessfulValidation(HttpServletRequest request, HttpServletResponse response, Assertion assertion);


    /**
     * Template method that gets executed if validation fails.  This method is called right after the exception is caught from the ticket validator
     * but before any of the processing of the exception occurs.
     *
     * @param request  the HttpServletRequest.
     * @param response the HttpServletResponse.
     */
    public abstract void onFailedValidation(final HttpServletRequest request, final HttpServletResponse response);

}
