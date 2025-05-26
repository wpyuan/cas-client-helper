//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.github.wpyuan.casclienthelper.utill;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jasig.cas.client.proxy.ProxyGrantingTicketStorage;
import org.jasig.cas.client.ssl.HttpURLConnectionFactory;
import org.jasig.cas.client.ssl.HttpsURLConnectionFactory;
import org.jasig.cas.client.validation.ProxyList;
import org.jasig.cas.client.validation.ProxyListEditor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CommonUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonUtils.class);
    private static final String PARAM_PROXY_GRANTING_TICKET_IOU = "pgtIou";
    private static final String PARAM_PROXY_GRANTING_TICKET = "pgtId";
    private static final HttpURLConnectionFactory DEFAULT_URL_CONNECTION_FACTORY = new HttpsURLConnectionFactory();

    private CommonUtils() {
    }

    public static String formatForUtcTime(Date date) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        ((DateFormat)dateFormat).setTimeZone(TimeZone.getTimeZone("UTC"));
        return ((DateFormat)dateFormat).format(date);
    }

    public static void assertNotNull(Object object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void assertNotEmpty(Collection<?> c, String message) {
        assertNotNull(c, message);
        if (c.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void assertTrue(boolean cond, String message) {
        if (!cond) {
            throw new IllegalArgumentException(message);
        }
    }

    public static boolean isEmpty(String string) {
        return string == null || string.length() == 0;
    }

    public static boolean isNotEmpty(String string) {
        return !isEmpty(string);
    }

    public static boolean isBlank(String string) {
        return isEmpty(string) || string.trim().length() == 0;
    }

    public static boolean isNotBlank(String string) {
        return !isBlank(string);
    }

    public static String constructRedirectUrl(String casServerLoginUrl, String serviceParameterName, String serviceUrl, boolean renew, boolean gateway) {
        return casServerLoginUrl + (casServerLoginUrl.contains("?") ? "&" : "?") + serviceParameterName + "=" + urlEncode(serviceUrl) + (renew ? "&renew=true" : "") + (gateway ? "&gateway=true" : "");
    }

    public static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException var2) {
            UnsupportedEncodingException e = var2;
            throw new RuntimeException(e);
        }
    }

    public static void readAndRespondToProxyReceptorRequest(HttpServletRequest request, HttpServletResponse response, ProxyGrantingTicketStorage proxyGrantingTicketStorage) throws IOException {
        String proxyGrantingTicketIou = request.getParameter("pgtIou");
        String proxyGrantingTicket = request.getParameter("pgtId");
        if (!isBlank(proxyGrantingTicket) && !isBlank(proxyGrantingTicketIou)) {
            LOGGER.debug("Received proxyGrantingTicketId [{}] for proxyGrantingTicketIou [{}]", proxyGrantingTicket, proxyGrantingTicketIou);
            proxyGrantingTicketStorage.save(proxyGrantingTicketIou, proxyGrantingTicket);
            LOGGER.debug("Successfully saved proxyGrantingTicketId [{}] for proxyGrantingTicketIou [{}]", proxyGrantingTicket, proxyGrantingTicketIou);
            response.getWriter().write("<?xml version=\"1.0\"?>");
            response.getWriter().write("<casClient:proxySuccess xmlns:casClient=\"http://www.yale.edu/tp/casClient\" />");
        } else {
            response.getWriter().write("");
        }
    }

    protected static String findMatchingServerName(HttpServletRequest request, String serverName) {
        String[] serverNames = serverName.split(" ");
        if (serverNames != null && serverNames.length != 0 && serverNames.length != 1) {
            String host = request.getHeader("Host");
            String xHost = request.getHeader("X-Forwarded-Host");
            String comparisonHost;
            if (xHost != null && host == "localhost") {
                comparisonHost = xHost;
            } else {
                comparisonHost = host;
            }

            if (comparisonHost == null) {
                return serverName;
            } else {
                String[] arr$ = serverNames;
                int len$ = arr$.length;

                for(int i$ = 0; i$ < len$; ++i$) {
                    String server = arr$[i$];
                    String lowerCaseServer = server.toLowerCase();
                    if (lowerCaseServer.contains(comparisonHost)) {
                        return server;
                    }
                }

                return serverNames[0];
            }
        } else {
            return serverName;
        }
    }

    private static boolean serverNameContainsPort(boolean containsScheme, String serverName) {
        if (!containsScheme && serverName.contains(":")) {
            return true;
        } else {
            int schemeIndex = serverName.indexOf(":");
            int portIndex = serverName.lastIndexOf(":");
            return schemeIndex != portIndex;
        }
    }

    private static boolean requestIsOnStandardPort(HttpServletRequest request) {
        int serverPort = request.getServerPort();
        return serverPort == 80 || serverPort == 443;
    }

    public static String constructServiceUrl(HttpServletRequest request, HttpServletResponse response, String service, String serverNames, String artifactParameterName, boolean appendPort, boolean encode) {
        if (isNotBlank(service)) {
            return encode ? response.encodeURL(service) : service;
        } else {
            StringBuilder buffer = new StringBuilder();
            String serverName = findMatchingServerName(request, serverNames);
            boolean containsScheme = true;
            if (!serverName.startsWith("https://") && !serverName.startsWith("http://")) {
                buffer.append(request.isSecure() ? "https://" : "http://");
                containsScheme = false;
            }

            buffer.append(serverName);
            if (appendPort && !serverNameContainsPort(containsScheme, serverName) && !requestIsOnStandardPort(request)) {
                buffer.append(":");
                buffer.append(request.getServerPort());
            }

            buffer.append(request.getRequestURI());
            if (isNotBlank(request.getQueryString())) {
                int location = request.getQueryString().indexOf(artifactParameterName + "=");
                if (location == 0) {
                    String returnValue = encode ? response.encodeURL(buffer.toString()) : buffer.toString();
                    LOGGER.debug("serviceUrl generated: {}", returnValue);
                    return returnValue;
                }

                buffer.append("?");
                if (location == -1) {
                    buffer.append(request.getQueryString());
                } else if (location > 0) {
                    int actualLocation = request.getQueryString().indexOf("&" + artifactParameterName + "=");
                    if (actualLocation == -1) {
                        buffer.append(request.getQueryString());
                    } else if (actualLocation > 0) {
                        buffer.append(request.getQueryString().substring(0, actualLocation));
                    }
                }
            }

            String returnValue = encode ? response.encodeURL(buffer.toString()) : buffer.toString();
            LOGGER.debug("serviceUrl generated: {}", returnValue);
            return returnValue;
        }
    }

    public static String constructServiceUrl(HttpServletRequest request, HttpServletResponse response, String service, String serverNames, String artifactParameterName, boolean encode) {
        if (isNotBlank(service)) {
            return encode ? response.encodeURL(service) : service;
        } else {
            StringBuilder buffer = new StringBuilder();
            String serverName = findMatchingServerName(request, serverNames);
            boolean containsScheme = true;
            if (!serverName.startsWith("https://") && !serverName.startsWith("http://")) {
                buffer.append(request.isSecure() ? "https://" : "http://");
                containsScheme = false;
            }

            buffer.append(serverName);
            if (!serverNameContainsPort(containsScheme, serverName) && !requestIsOnStandardPort(request)) {
                buffer.append(":");
                buffer.append(request.getServerPort());
            }

            buffer.append(request.getRequestURI());
            if (isNotBlank(request.getQueryString())) {
                int location = request.getQueryString().indexOf(artifactParameterName + "=");
                if (location == 0) {
                    String returnValue = encode ? response.encodeURL(buffer.toString()) : buffer.toString();
                    LOGGER.debug("serviceUrl generated: {}", returnValue);
                    return returnValue;
                }

                buffer.append("?");
                if (location == -1) {
                    buffer.append(request.getQueryString());
                } else if (location > 0) {
                    int actualLocation = request.getQueryString().indexOf("&" + artifactParameterName + "=");
                    if (actualLocation == -1) {
                        buffer.append(request.getQueryString());
                    } else if (actualLocation > 0) {
                        buffer.append(request.getQueryString().substring(0, actualLocation));
                    }
                }
            }

            String returnValue = encode ? response.encodeURL(buffer.toString()) : buffer.toString();
            LOGGER.debug("serviceUrl generated: {}", returnValue);
            return returnValue;
        }
    }

    public static String safeGetParameter(HttpServletRequest request, String parameter, List<String> parameters) {
        if ("POST".equals(request.getMethod()) && parameters.contains(parameter)) {
            LOGGER.debug("safeGetParameter called on a POST HttpServletRequest for Restricted Parameters.  Cannot complete check safely.  Reverting to standard behavior for this Parameter");
            return request.getParameter(parameter);
        } else {
            return request.getQueryString() != null && request.getQueryString().contains(parameter) ? request.getParameter(parameter) : null;
        }
    }

    public static String safeGetParameter(HttpServletRequest request, String parameter) {
        return safeGetParameter(request, parameter, Arrays.asList("logoutRequest"));
    }

    /** @deprecated */
    @Deprecated
    public static String getResponseFromServer(String constructedUrl, String encoding) {
        try {
            return getResponseFromServer(new URL(constructedUrl), DEFAULT_URL_CONNECTION_FACTORY, encoding);
        } catch (Exception var3) {
            Exception e = var3;
            throw new RuntimeException(e);
        }
    }

    /** @deprecated */
    @Deprecated
    public static String getResponseFromServer(URL constructedUrl, String encoding) {
        return getResponseFromServer(constructedUrl, DEFAULT_URL_CONNECTION_FACTORY, encoding);
    }

    public static String getResponseFromServer(URL constructedUrl, HttpURLConnectionFactory factory, String encoding) {
        HttpURLConnection conn = null;
        InputStreamReader in = null;

        try {
            conn = factory.buildHttpURLConnection(constructedUrl.openConnection());
            if (isEmpty(encoding)) {
                in = new InputStreamReader(conn.getInputStream());
            } else {
                in = new InputStreamReader(conn.getInputStream(), encoding);
            }

            StringBuilder builder = new StringBuilder(255);

            int byteRead;
            while((byteRead = in.read()) != -1) {
                builder.append((char)byteRead);
            }

            String var7 = builder.toString();
            return var7;
        } catch (Exception var11) {
            Exception e = var11;
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            closeQuietly(in);
            if (conn != null) {
                conn.disconnect();
            }

        }
    }

    public static ProxyList createProxyList(String proxies) {
        if (isBlank(proxies)) {
            return new ProxyList();
        } else {
            ProxyListEditor editor = new ProxyListEditor();
            editor.setAsText(proxies);
            return (ProxyList)editor.getValue();
        }
    }

    public static void sendRedirect(HttpServletResponse response, String url) {
        try {
            response.sendRedirect(url);
        } catch (Exception var3) {
            Exception e = var3;
            LOGGER.warn(e.getMessage(), e);
        }

    }

    public static void closeQuietly(Closeable resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (IOException var2) {
        }

    }
}
