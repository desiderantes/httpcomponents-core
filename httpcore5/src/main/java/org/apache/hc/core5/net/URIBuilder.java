/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.core5.net;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.Tokenizer;

/**
 * Builder for {@link URI} instances.
 *
 * @since 5.0
 */
public class URIBuilder {

    /**
     * Creates a new builder for the host {@link InetAddress#getLocalHost()}.
     *
     * @return a new builder.
     * @throws UnknownHostException if the local host name could not be resolved into an address.
     */
    public static URIBuilder localhost() throws UnknownHostException {
        return new URIBuilder().setHost(InetAddress.getLocalHost());
    }

    /**
     * Creates a new builder for the host {@link InetAddress#getLoopbackAddress()}.
     */
    public static URIBuilder loopbackAddress() {
        return new URIBuilder().setHost(InetAddress.getLoopbackAddress());
    }

    private String scheme;
    private String encodedSchemeSpecificPart;
    private String encodedAuthority;
    private String userInfo;
    private String encodedUserInfo;
    private String host;
    private int port;
    private String encodedPath;
    private boolean pathRootless;
    private List<String> pathSegments;
    private String encodedQuery;
    private List<NameValuePair> queryParams;
    private String query;
    private Charset charset;
    private String fragment;
    private String encodedFragment;
    private EncodingPolicy encodingPolicy = EncodingPolicy.ALL_RESERVED;

    private boolean plusAsBlank;

    /**
     * Defines the encoding policy for URI components in {@link URIBuilder}.
     * This enum controls how characters are percent-encoded when constructing a URI,
     * allowing flexibility between strict encoding and RFC 3986-compliant behavior.
     *
     * @since 5.4
     */
    public enum EncodingPolicy {
        /**
         * Encodes all reserved characters, allowing only unreserved characters
         * (ALPHA, DIGIT, "-", ".", "_", "~") to remain unencoded. This is a strict
         * policy suitable for conservative URI production where maximum encoding
         * is desired.
         */
        ALL_RESERVED,

        /**
         * Follows RFC 3986 component-specific encoding rules. For example, query and
         * fragment components allow unreserved characters, sub-delimiters ("!", "$",
         * "&", "'", "(", ")", "*", "+", ",", ";", "="), and additional characters
         * (":", "@", "/", "?") to remain unencoded, as defined by {@code PercentCodec.FRAGMENT}.
         * This policy ensures compliance with RFC 3986 while maintaining interoperability.
         */
        RFC_3986
    }

    /**
     * Constructs an empty instance.
     */
    public URIBuilder() {
        super();
        this.port = -1;
    }

    /**
     * Constructs an instance from the string which must be a valid URI.
     *
     * @param uriString a valid URI in string form.
     * @throws URISyntaxException if the input is not a valid URI.
     */
    public URIBuilder(final String uriString) throws URISyntaxException {
        this(new URI(uriString), StandardCharsets.UTF_8);
    }

    /**
     * Constructs an instance from the provided URI.
     * @param uri a URI.
     */
    public URIBuilder(final URI uri) {
        this(uri, StandardCharsets.UTF_8);
    }

    /**
     * Constructs an instance from the string which must be a valid URI.
     *
     * @param uriString a valid URI in string form.
     * @throws URISyntaxException if the input is not a valid URI
     */
    public URIBuilder(final String uriString, final Charset charset) throws URISyntaxException {
        this(new URI(uriString), charset);
    }

    /**
     * Constructs an instance from the provided URI.
     *
     * @param uri a URI.
     */
    public URIBuilder(final URI uri, final Charset charset) {
        super();
        digestURI(uri, charset);
    }

    /**
     * Sets the authority.
     *
     * @param authority the authority.
     * @return this instance.
     * @since 5.2
     */
    public URIBuilder setAuthority(final NamedEndpoint authority) {
        setUserInfo(null);
        setHost(authority.getHostName());
        setPort(authority.getPort());
        return this;
    }

    /**
     * Sets the authority.
     *
     * @param authority the authority.
     * @return this instance.
     * @since 5.2
     */
    public URIBuilder setAuthority(final URIAuthority authority) {
        setUserInfo(authority.getUserInfo());
        setHost(authority.getHostName());
        setPort(authority.getPort());
        return this;
    }

    /**
     * Sets the Charset.
     *
     * @param charset the Charset.
     * @return this instance.
     */
    public URIBuilder setCharset(final Charset charset) {
        this.charset = charset;
        return this;
    }

    /**
     * Sets the encoding policy for this {@link URIBuilder}.
     * The encoding policy determines how URI components (e.g., query, fragment) are
     * percent-encoded when building the URI string. If not set, the default policy
     * is {@link EncodingPolicy#RFC_3986}.
     *
     * @param encodingPolicy the encoding policy to apply, or {@code null} to reset
     *                       to the default ({@link EncodingPolicy#ALL_RESERVED})
     * @return this {@link URIBuilder} instance for method chaining
     * @since 5.4
     */
    public URIBuilder setEncodingPolicy(final EncodingPolicy encodingPolicy) {
        this.encodingPolicy = encodingPolicy;
        return this;
    }

    /**
     * Gets the authority.
     *
     * @return the authority.
     * @since 5.2
     */
    public URIAuthority getAuthority() {
        return new URIAuthority(getUserInfo(), getHost(), getPort());
    }

    /**
     * Gets the Charset.
     *
     * @return the Charset.
     */
    public Charset getCharset() {
        return charset;
    }

    /**
     * Sets whether the plus sign ('+') should be interpreted as a blank space (' ') when parsing
     * the query parameters of the URI.
     * <p>
     * In HTTP URLs, query strings may contain spaces encoded as '+' characters or as '%20'.
     * This flag controls whether '+' is interpreted as a space or remains as a plus sign.
     * </p>
     *
     * <p>
     * If the query string was already set, calling this method will re-parse the query
     * using the updated flag. This ensures that the query parameters are processed correctly
     * based on the specified interpretation of the '+' character.
     * </p>
     *
     * @param plusAsBlank {@code true} to interpret '+' as a space, {@code false} to keep '+' as a literal plus sign.
     * @return this {@link URIBuilder} instance for method chaining.
     * @since 5.4
     */
    public URIBuilder setPlusAsBlank(final boolean plusAsBlank) {
        this.plusAsBlank = plusAsBlank;
        // Re-parse the query string using the updated flag
        if (this.encodedQuery != null) {
            this.queryParams = parseQuery(this.encodedQuery, this.charset, this.plusAsBlank);
        }
        return this;
    }

    private static final char QUERY_PARAM_SEPARATOR = '&';
    private static final char PARAM_VALUE_SEPARATOR = '=';
    private static final char PATH_SEPARATOR = '/';

    private static final Tokenizer.Delimiter QUERY_PARAM_SEPARATORS = Tokenizer.delimiters(QUERY_PARAM_SEPARATOR, PARAM_VALUE_SEPARATOR);
    private static final Tokenizer.Delimiter QUERY_VALUE_SEPARATORS = Tokenizer.delimiters(QUERY_PARAM_SEPARATOR);

    static List<NameValuePair> parseQuery(final CharSequence s, final Charset charset, final boolean plusAsBlank) {
        if (s == null) {
            return null;
        }
        final Tokenizer tokenParser = Tokenizer.INSTANCE;
        final ParserCursor cursor = new ParserCursor(0, s.length());
        final List<NameValuePair> list = new ArrayList<>();
        while (!cursor.atEnd()) {
            final String name = tokenParser.parseToken(s, cursor, QUERY_PARAM_SEPARATORS);
            String value = null;
            if (!cursor.atEnd()) {
                final int delim = s.charAt(cursor.getPos());
                cursor.updatePos(cursor.getPos() + 1);
                if (delim == PARAM_VALUE_SEPARATOR) {
                    value = tokenParser.parseToken(s, cursor, QUERY_VALUE_SEPARATORS);
                    if (!cursor.atEnd()) {
                        cursor.updatePos(cursor.getPos() + 1);
                    }
                }
            }
            if (!name.isEmpty()) {
                list.add(new BasicNameValuePair(
                        PercentCodec.decode(name, charset, plusAsBlank),
                        PercentCodec.decode(value, charset, plusAsBlank)));
            }
        }
        return list;
    }

    static List<String> splitPath(final CharSequence s) {
        if (s == null) {
            return Collections.emptyList();
        }
        final ParserCursor cursor = new ParserCursor(0, s.length());
        // Skip leading separator
        if (cursor.atEnd()) {
            return new ArrayList<>(0);
        }
        if (s.charAt(cursor.getPos()) == PATH_SEPARATOR) {
            cursor.updatePos(cursor.getPos() + 1);
        }
        final List<String> list = new ArrayList<>();
        final StringBuilder buf = new StringBuilder();
        for (;;) {
            if (cursor.atEnd()) {
                list.add(buf.toString());
                break;
            }
            final char current = s.charAt(cursor.getPos());
            if (current == PATH_SEPARATOR) {
                list.add(buf.toString());
                buf.setLength(0);
            } else {
                buf.append(current);
            }
            cursor.updatePos(cursor.getPos() + 1);
        }
        return list;
    }

    static List<String> parsePath(final CharSequence s, final Charset charset) {
        if (s == null) {
            return Collections.emptyList();
        }
        final List<String> segments = splitPath(s);
        final List<String> list = new ArrayList<>(segments.size());
        for (final String segment: segments) {
            list.add(PercentCodec.decode(segment, charset));
        }
        return list;
    }

    static void formatPath(final StringBuilder buf, final Iterable<String> segments, final boolean rootless,
                           final Charset charset, final BitSet safechars) {
        int i = 0;
        for (final String segment : segments) {
            if (i > 0 || !rootless) {
                buf.append(PATH_SEPARATOR);
            }
            PercentCodec.encode(buf, segment, charset, safechars, false);
            i++;
        }
    }

    static void formatPath(final StringBuilder buf, final Iterable<String> segments, final boolean rootless,
                           final Charset charset) {
        formatPath(buf, segments, rootless, charset, PercentCodec.UNRESERVED);
    }


    static void formatQuery(final StringBuilder buf, final Iterable<? extends NameValuePair> params,
                            final Charset charset, final BitSet safechars, final boolean blankAsPlus) {
        int i = 0;
        for (final NameValuePair parameter : params) {
            if (i > 0) {
                buf.append(QUERY_PARAM_SEPARATOR);
            }
            PercentCodec.encode(buf, parameter.getName(), charset, safechars, blankAsPlus);
            if (parameter.getValue() != null) {
                buf.append(PARAM_VALUE_SEPARATOR);
                PercentCodec.encode(buf, parameter.getValue(), charset, safechars, blankAsPlus);
            }
            i++;
        }
    }

    static void formatQuery(final StringBuilder buf, final Iterable<? extends NameValuePair> params,
                            final Charset charset, final boolean blankAsPlus) {
        formatQuery(buf, params, charset, PercentCodec.UNRESERVED, blankAsPlus);
    }


    /**
     * Builds a {@link URI} instance.
     */
    public URI build() throws URISyntaxException {
        if ((URIScheme.HTTPS.same(scheme) || URIScheme.HTTP.same(scheme)) && (TextUtils.isBlank(host))) {
            throw new URISyntaxException(scheme, "http/https URI cannot have an empty host identifier");
        }
        return new URI(buildString());
    }

    private String buildString() {
        final StringBuilder sb = new StringBuilder();
        if (this.scheme != null) {
            sb.append(this.scheme).append(':');
        }
        if (this.encodedSchemeSpecificPart != null) {
            sb.append(this.encodedSchemeSpecificPart);
        } else {
            final boolean authoritySpecified;
            if (this.encodedAuthority != null) {
                sb.append("//").append(this.encodedAuthority);
                authoritySpecified = true;
            } else if (this.host != null) {
                sb.append("//");
                if (this.encodedUserInfo != null) {
                    sb.append(this.encodedUserInfo).append("@");
                } else if (this.userInfo != null) {
                    final int idx = this.userInfo.indexOf(':');
                    if (idx != -1) {
                        PercentCodec.encode(sb, this.userInfo.substring(0, idx), this.charset,
                                encodingPolicy == EncodingPolicy.ALL_RESERVED ? PercentCodec.UNRESERVED : PercentCodec.USERINFO, false);
                        sb.append(':');
                        PercentCodec.encode(sb, this.userInfo.substring(idx + 1), this.charset,
                                encodingPolicy == EncodingPolicy.ALL_RESERVED ? PercentCodec.UNRESERVED : PercentCodec.USERINFO, false);
                    } else {
                        PercentCodec.encode(sb, this.userInfo, this.charset,
                                encodingPolicy == EncodingPolicy.ALL_RESERVED ? PercentCodec.UNRESERVED : PercentCodec.USERINFO, false);
                    }
                    sb.append("@");
                }
                if (InetAddressUtils.isIPv6(this.host)) {
                    sb.append("[").append(this.host).append("]");
                } else {
                    PercentCodec.encode(sb, this.host, this.charset,
                            encodingPolicy == EncodingPolicy.ALL_RESERVED ? PercentCodec.UNRESERVED : PercentCodec.REG_NAME, false);
                }
                if (this.port >= 0) {
                    sb.append(":").append(this.port);
                }
                authoritySpecified = true;
            } else {
                authoritySpecified = false;
            }
            if (this.encodedPath != null) {
                if (authoritySpecified && !TextUtils.isEmpty(this.encodedPath) && !this.encodedPath.startsWith("/")) {
                    sb.append('/');
                }
                sb.append(this.encodedPath);
            } else if (this.pathSegments != null) {
                formatPath(sb, this.pathSegments, !authoritySpecified && this.pathRootless, this.charset,
                        encodingPolicy == EncodingPolicy.ALL_RESERVED ? PercentCodec.UNRESERVED : PercentCodec.PATH_SEGMENT);
            }
            if (this.encodedQuery != null) {
                sb.append("?").append(this.encodedQuery);
            } else if (this.queryParams != null && !this.queryParams.isEmpty()) {
                sb.append("?");
                formatQuery(sb, this.queryParams, this.charset,
                        encodingPolicy == EncodingPolicy.ALL_RESERVED ? PercentCodec.UNRESERVED : PercentCodec.QUERY, false);
            } else if (this.query != null) {
                sb.append("?");
                PercentCodec.encode(sb, this.query, this.charset,
                        encodingPolicy == EncodingPolicy.ALL_RESERVED ? PercentCodec.URIC : PercentCodec.QUERY, false);
            }
        }
        if (this.encodedFragment != null) {
            sb.append("#").append(this.encodedFragment);
        } else if (this.fragment != null) {
            sb.append("#");
            PercentCodec.encode(sb, this.fragment, this.charset,
                    encodingPolicy == EncodingPolicy.ALL_RESERVED ? PercentCodec.URIC : PercentCodec.FRAGMENT, false);
        }
        return sb.toString();
    }

    private void digestURI(final URI uri, final Charset charset) {
        this.scheme = uri.getScheme();
        this.encodedSchemeSpecificPart = uri.getRawSchemeSpecificPart();
        this.encodedAuthority = uri.getRawAuthority();
        final String uriHost = uri.getHost();
        // URI.getHost incorrectly returns bracketed (encoded) IPv6 values. Brackets are an
        // encoding detail of the URI and not part of the host string.
        this.host = uriHost != null && InetAddressUtils.isIPv6URLBracketed(uriHost)
                ? uriHost.substring(1, uriHost.length() - 1)
                : uriHost;
        this.port = uri.getPort();
        this.encodedUserInfo = uri.getRawUserInfo();
        this.userInfo = uri.getUserInfo();
        if (this.encodedAuthority != null && this.host == null) {
            try {
                final URIAuthority uriAuthority = URIAuthority.parse(this.encodedAuthority);
                this.encodedUserInfo = uriAuthority.getUserInfo();
                this.userInfo = PercentCodec.decode(uriAuthority.getUserInfo(), charset);
                this.host = PercentCodec.decode(uriAuthority.getHostName(), charset);
                this.port = uriAuthority.getPort();
            } catch (final URISyntaxException ignore) {
                // ignore
            }
        }
        this.encodedPath = uri.getRawPath();
        this.pathSegments = parsePath(uri.getRawPath(), charset);
        this.pathRootless = uri.getRawPath() == null || !uri.getRawPath().startsWith("/");
        this.encodedQuery = uri.getRawQuery();
        this.queryParams = parseQuery(uri.getRawQuery(), charset, this.plusAsBlank);
        this.encodedFragment = uri.getRawFragment();
        this.fragment = uri.getFragment();
        this.charset = charset;
    }

    /**
     * Sets URI scheme.
     *
     * @return this instance.
     */
    public URIBuilder setScheme(final String scheme) {
        this.scheme = !TextUtils.isBlank(scheme) ? scheme : null;
        return this;
    }

    /**
     * Sets the URI scheme specific part.
     *
     * @param schemeSpecificPart
     * @return this instance.
     * @since 5.1
     */
    public URIBuilder setSchemeSpecificPart(final String schemeSpecificPart) {
        this.encodedSchemeSpecificPart = schemeSpecificPart;
        return this;
    }

    /**
     * Sets the URI scheme specific part and append a variable arguments list of NameValuePair instance(s) to this part.
     *
     * @param schemeSpecificPart
     * @param nvps Optional, can be null. Variable arguments list of NameValuePair query parameters to be reused by the specific scheme part
     * @return this instance.
     * @since 5.1
     */
    public URIBuilder setSchemeSpecificPart(final String schemeSpecificPart, final NameValuePair... nvps) {
        return setSchemeSpecificPart(schemeSpecificPart, nvps != null ? Arrays.asList(nvps) : null);
    }

    /**
     * Sets the URI scheme specific part and append a list of NameValuePair to this part.
     *
     * @param schemeSpecificPart
     * @param nvps Optional, can be null. List of query parameters to be reused by the specific scheme part
     * @return this instance.
     * @since 5.1
     */
    public URIBuilder setSchemeSpecificPart(final String schemeSpecificPart, final List<NameValuePair> nvps) {
        this.encodedSchemeSpecificPart = null;
        if (!TextUtils.isBlank(schemeSpecificPart)) {
            final StringBuilder sb = new StringBuilder(schemeSpecificPart);
            if (nvps != null && !nvps.isEmpty()) {
                sb.append("?");
                formatQuery(sb, nvps, this.charset, false);
            }
            this.encodedSchemeSpecificPart = sb.toString();
        }
        return this;
    }

    /**
     * Sets URI user info. The value is expected to be unescaped and may contain non ASCII
     * characters.
     *
     * @return this instance.
     */
    public URIBuilder setUserInfo(final String userInfo) {
        this.userInfo = !TextUtils.isBlank(userInfo) ? userInfo : null;
        this.encodedSchemeSpecificPart = null;
        this.encodedAuthority = null;
        this.encodedUserInfo = null;
        return this;
    }

    /**
     * Sets URI user info as a combination of username and password. These values are expected to
     * be unescaped and may contain non ASCII characters.
     *
     * @return this instance.
     *
     * @deprecated The use of clear-text passwords in {@link URI}s has been deprecated and is strongly
     * discouraged.
     */
    @Deprecated
    public URIBuilder setUserInfo(final String username, final String password) {
        return setUserInfo(username + ':' + password);
    }

    /**
     * Sets URI host.
     *
     * @return this instance.
     */
    public URIBuilder setHost(final InetAddress host) {
        this.host = host != null ? host.getHostAddress() : null;
        this.encodedSchemeSpecificPart = null;
        this.encodedAuthority = null;
        return this;
    }

    /**
     * Sets URI host. The input value must not already be URI encoded, for example {@code ::1} is valid however
     * {@code [::1]} is not. It is dangerous to call {@code uriBuilder.setHost(uri.getHost())} due
     * to {@link URI#getHost()} returning URI encoded values.
     *
     * @return this instance.
     */
    public URIBuilder setHost(final String host) {
        this.host = host;
        this.encodedSchemeSpecificPart = null;
        this.encodedAuthority = null;
        return this;
    }

    /**
     * Sets the scheme, host name, and port.
     *
     * @param httpHost the scheme, host name, and port.
     * @return this instance.
     */
    public URIBuilder setHttpHost(final HttpHost httpHost) {
        setScheme(httpHost.getSchemeName());
        setHost(httpHost.getHostName());
        setPort(httpHost.getPort());
        return this;
    }

    /**
     * Sets URI port.
     *
     * @return this instance.
     */
    public URIBuilder setPort(final int port) {
        this.port = port < 0 ? -1 : port;
        this.encodedSchemeSpecificPart = null;
        this.encodedAuthority = null;
        return this;
    }

    /**
     * Sets URI path. The value is expected to be unescaped and may contain non ASCII characters.
     *
     * @return this instance.
     */
    public URIBuilder setPath(final String path) {
        setPathSegments(path != null ? splitPath(path) : null);
        this.pathRootless = path != null && !path.startsWith("/");
        return this;
    }

    /**
     * Appends path to URI. The value is expected to be unescaped and may contain non ASCII characters.
     *
     * @return this instance.
     */
    public URIBuilder appendPath(final String path) {
        if (path != null) {
            appendPathSegments(splitPath(path));
        }
        return this;
    }

    /**
     * Sets URI path. The value is expected to be unescaped and may contain non ASCII characters.
     *
     * @return this instance.
     */
    public URIBuilder setPathSegments(final String... pathSegments) {
        return setPathSegments(Arrays.asList(pathSegments));
    }

    /**
     * Appends segments URI path. The value is expected to be unescaped and may contain non ASCII characters.
     *
     * @return this instance.
     */
    public URIBuilder appendPathSegments(final String... pathSegments) {
        return appendPathSegments(Arrays.asList(pathSegments));
    }

    /**
     * Sets rootless URI path (the first segment does not start with a /).
     * The value is expected to be unescaped and may contain non ASCII characters.
     *
     * @return this instance.
     *
     * @since 5.1
     */
    public URIBuilder setPathSegmentsRootless(final String... pathSegments) {
        return setPathSegmentsRootless(Arrays.asList(pathSegments));
    }

    /**
     * Sets URI path. The value is expected to be unescaped and may contain non ASCII characters.
     *
     * @return this instance.
     */
    public URIBuilder setPathSegments(final List<String> pathSegments) {
        this.pathSegments = pathSegments != null && !pathSegments.isEmpty() ? new ArrayList<>(pathSegments) : null;
        this.encodedSchemeSpecificPart = null;
        this.encodedPath = null;
        this.pathRootless = false;
        return this;
    }

    /**
     * Appends segments to URI path. The value is expected to be unescaped and may contain non ASCII characters.
     *
     * @return this instance.
     */
    public URIBuilder appendPathSegments(final List<String> pathSegments) {
        if (pathSegments != null && !pathSegments.isEmpty()) {
            if (this.pathSegments == null) {
                this.pathSegments = new ArrayList<>();
            }
            this.pathSegments.addAll(pathSegments);
            this.encodedSchemeSpecificPart = null;
            this.encodedPath = null;
        }
        return this;
    }

    /**
     * Sets rootless URI path (the first segment does not start with a /).
     * The value is expected to be unescaped and may contain non ASCII characters.
     *
     * @return this instance.
     *
     * @since 5.1
     */
    public URIBuilder setPathSegmentsRootless(final List<String> pathSegments) {
        this.pathSegments = pathSegments != null && !pathSegments.isEmpty() ? new ArrayList<>(pathSegments) : null;
        this.encodedSchemeSpecificPart = null;
        this.encodedPath = null;
        this.pathRootless = true;
        return this;
    }

    /**
     * Removes URI query.
     *
     * @return this instance.
     */
    public URIBuilder removeQuery() {
        this.queryParams = null;
        this.query = null;
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        return this;
    }

    /**
     * Sets URI query parameters. The parameter name / values are expected to be unescaped
     * and may contain non ASCII characters.
     * <p>
     * Please note query parameters and custom query component are mutually exclusive. This method
     * will remove custom query if present.
     * </p>
     *
     * @return this instance.
     */
    public URIBuilder setParameters(final List<NameValuePair> nameValuePairs) {
        if (this.queryParams == null) {
            this.queryParams = new ArrayList<>();
        } else {
            this.queryParams.clear();
        }
        if (nameValuePairs != null) {
            this.queryParams.addAll(nameValuePairs);
        }
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        this.query = null;
        return this;
    }

    /**
     * Adds URI query parameters. The parameter name / values are expected to be unescaped
     * and may contain non ASCII characters.
     * <p>
     * Please note query parameters and custom query component are mutually exclusive. This method
     * will remove custom query if present.
     * </p>
     *
     * @return this instance.
     */
    public URIBuilder addParameters(final List<NameValuePair> nameValuePairs) {
        if (this.queryParams == null) {
            this.queryParams = new ArrayList<>();
        }
        if (nameValuePairs != null) {
            this.queryParams.addAll(nameValuePairs);
        }
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        this.query = null;
        return this;
    }

    /**
     * Sets URI query parameters. The parameter name / values are expected to be unescaped
     * and may contain non ASCII characters.
     * <p>
     * Please note query parameters and custom query component are mutually exclusive. This method
     * will remove custom query if present.
     * </p>
     *
     * @return this instance.
     */
    public URIBuilder setParameters(final NameValuePair... nameValuePairs) {
        if (this.queryParams == null) {
            this.queryParams = new ArrayList<>();
        } else {
            this.queryParams.clear();
        }
        if (nameValuePairs != null) {
            Collections.addAll(this.queryParams, nameValuePairs);
        }
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        this.query = null;
        return this;
    }

    /**
     * Adds parameter to URI query. The parameter name and value are expected to be unescaped
     * and may contain non ASCII characters.
     * <p>
     * Please note query parameters and custom query component are mutually exclusive. This method
     * will remove custom query if present.
     * </p>
     *
     * @return this instance.
     */
    public URIBuilder addParameter(final String param, final String value) {
        return addParameter(new BasicNameValuePair(param, value));
    }

    /**
     * Adds parameter to URI query. The parameter name and value are expected to be unescaped
     * and may contain non ASCII characters.
     * <p>
     * Please note query parameters and custom query component are mutually exclusive. This method
     * will remove custom query if present.
     * </p>
     *
     * @return this instance.
     * @since 5.2
     */
    public URIBuilder addParameter(final NameValuePair nameValuePair) {
        if (this.queryParams == null) {
            this.queryParams = new ArrayList<>();
        }
        if (nameValuePair != null) {
            this.queryParams.add(nameValuePair);
        }
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        this.query = null;
        return this;
    }

    /**
     * Removes parameter of URI query if set. The parameter name is expected to be unescaped and may
     * contain non ASCII characters.
     * <p>
     * Please note query parameters and custom query component are mutually exclusive. This method
     * will remove custom query if present, even when no parameter was actually removed.
     * </p>
     *
     * @return this instance.
     * @since 5.2
     */
    public URIBuilder removeParameter(final String param) {
        Args.notNull(param, "param");
        if (this.queryParams != null && !this.queryParams.isEmpty()) {
            this.queryParams.removeIf(nvp -> nvp.getName().equals(param));
        }
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        this.query = null;
        return this;
    }

    /**
     * Sets parameter of URI query overriding existing value if set. The parameter name and value
     * are expected to be unescaped and may contain non ASCII characters.
     * <p>
     * Please note query parameters and custom query component are mutually exclusive. This method
     * will remove custom query if present.
     * </p>
     *
     * @return this instance.
     */
    public URIBuilder setParameter(final String param, final String value) {
        if (this.queryParams == null) {
            this.queryParams = new ArrayList<>();
        }
        if (!this.queryParams.isEmpty()) {
            this.queryParams.removeIf(nvp -> nvp.getName().equals(param));
        }
        this.queryParams.add(new BasicNameValuePair(param, value));
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        this.query = null;
        return this;
    }

    /**
     * Clears URI query parameters.
     *
     * @return this instance.
     */
    public URIBuilder clearParameters() {
        this.queryParams = null;
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        return this;
    }

    /**
     * Sets custom URI query. The value is expected to be unescaped and may contain non ASCII
     * characters.
     * <p>
     * Please note query parameters and custom query component are mutually exclusive. This method
     * will remove query parameters if present.
     * </p>
     *
     * @return this instance.
     */
    public URIBuilder setCustomQuery(final String query) {
        this.query = !TextUtils.isBlank(query) ? query : null;
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        this.queryParams = null;
        return this;
    }

    /**
     * Sets URI fragment. The value is expected to be unescaped and may contain non ASCII
     * characters.
     *
     * @return this instance.
     */
    public URIBuilder setFragment(final String fragment) {
        this.fragment = !TextUtils.isBlank(fragment) ? fragment : null;
        this.encodedFragment = null;
        return this;
    }

    /**
     * Tests whether the URI is absolute.
     *
     * @return whether the URI is absolute.
     */
    public boolean isAbsolute() {
        return this.scheme != null;
    }

    /**
     * Tests whether the URI is opaque.
     *
     * @return whether the URI is opaque.
     */
    public boolean isOpaque() {
        return this.pathSegments == null && this.encodedPath == null;
    }

    /**
     * Gets the scheme.
     *
     * @return the scheme.
     */
    public String getScheme() {
        return this.scheme;
    }

    /**
     * Gets the scheme specific part.
     *
     * @return String
     * @since 5.1
     */
    public String getSchemeSpecificPart() {
        return this.encodedSchemeSpecificPart;
    }

    /**
     * Gets the user info.
     *
     * @return  the user info.
     */
    public String getUserInfo() {
        return this.userInfo;
    }

    /**
     * Gets the host portion of the {@link URI}. This method returns unencoded IPv6 addresses (without brackets).
     * This behavior differs from values returned by {@link URI#getHost()}.
     *
     * @return The host portion of the URI.
     */
    public String getHost() {
        return this.host;
    }

    /**
     * Gets the port.
     *
     * @return  the port.
     */
    public int getPort() {
        return this.port;
    }

    /**
     * Tests whether the path is empty.
     *
     * @return whether the path is empty.
     */
    public boolean isPathEmpty() {
        return (this.pathSegments == null || this.pathSegments.isEmpty()) &&
                (this.encodedPath == null || this.encodedPath.isEmpty());
    }

    /**
     * Gets the path segments.
     *
     * @return the path segments.
     */
    public List<String> getPathSegments() {
        return this.pathSegments != null ? new ArrayList<>(this.pathSegments) : new ArrayList<>();
    }

    /**
     * Gets the path.
     *
     * @return the path.
     */
    public String getPath() {
        if (this.pathSegments == null) {
            return null;
        }
        final StringBuilder result = new StringBuilder();
        for (final String segment : this.pathSegments) {
            result.append('/').append(segment);
        }
        return result.toString();
    }

    /**
     * Tests whether the query is empty.
     *
     * @return whether the query is empty.
     */
    public boolean isQueryEmpty() {
        return (this.queryParams == null || this.queryParams.isEmpty()) && this.encodedQuery == null;
    }

    /**
     * Gets the query parameters as a List.
     *
     * @return the query parameters as a List.
     */
    public List<NameValuePair> getQueryParams() {
        return this.queryParams != null ? new ArrayList<>(this.queryParams) : new ArrayList<>();
    }

    /**
     * Gets the first {@link NameValuePair} for a given name.
     *
     * @param name the name
     * @return the first named {@link NameValuePair} or null if not found.
     * @since 5.2
     */
    public NameValuePair getFirstQueryParam(final String name) {
        if (queryParams == null) {
            return null;
        }
        return queryParams.stream().filter(e -> name.equals(e.getName())).findFirst().orElse(null);
    }

    /**
     * Gets the fragments.
     *
     * @return the fragments.
     */
    public String getFragment() {
        return this.fragment;
    }

    /**
     * @deprecated do not use this method.
     *
     * @see #optimize()
     */
    @Deprecated
    public URIBuilder normalizeSyntax() {
        return optimize();
    }

    /**
     * Optimizes URI components if the URI is considered non-opaque (the path component has a root):
     * <ul>
     *  <li>characters of scheme and host components are converted to lower case</li>
     *  <li>dot segments of the path component are removed if the path has a root</li>
     *  <li>percent encoding of all components is re-applied</li>
     * </ul>
     * <p>
     *  Please note some URI consumers may consider the optimized URI components produced
     *  by this method as semantically different from the original ones.
     *
     * @since 5.3
     */
    public URIBuilder optimize() {
        final String scheme = this.scheme;
        if (scheme != null) {
            this.scheme = TextUtils.toLowerCase(scheme);
        }

        if (this.pathRootless) {
            return this;
        }

        // Force Percent-Encoding re-encoding
        this.encodedSchemeSpecificPart = null;
        this.encodedAuthority = null;
        this.encodedUserInfo = null;
        this.encodedPath = null;
        this.encodedQuery = null;
        this.encodedFragment = null;

        final String host = this.host;
        if (host != null) {
            this.host = TextUtils.toLowerCase(host);
        }

        if (this.pathSegments != null) {
            final List<String> inputSegments = this.pathSegments;
            if (!inputSegments.isEmpty()) {
                final LinkedList<String> outputSegments = new LinkedList<>();
                for (final String inputSegment : inputSegments) {
                    if (!inputSegment.isEmpty() && !".".equals(inputSegment)) {
                        if ("..".equals(inputSegment)) {
                            if (!outputSegments.isEmpty()) {
                                outputSegments.removeLast();
                            }
                        } else {
                            outputSegments.addLast(inputSegment);
                        }
                    }
                }
                if (!inputSegments.isEmpty()) {
                    final String lastSegment = inputSegments.get(inputSegments.size() - 1);
                    if (lastSegment.isEmpty()) {
                        outputSegments.addLast("");
                    }
                }
                this.pathSegments = outputSegments;
            } else {
                this.pathSegments = Collections.singletonList("");
            }
        }

        return this;
    }

    /**
     * Converts this instance to a URI string.
     *
     * @return this instance to a URI string.
     */
    @Override
    public String toString() {
        return buildString();
    }

}
