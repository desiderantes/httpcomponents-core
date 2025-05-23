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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * Percent-encoding.
 *
 * @since 5.1
 */
public class PercentCodec {

    static final BitSet GEN_DELIMS = new BitSet(256);
    static final BitSet SUB_DELIMS = new BitSet(256);
    static final BitSet UNRESERVED = new BitSet(256);
    static final BitSet URIC = new BitSet(256);

    static {
        GEN_DELIMS.set(':');
        GEN_DELIMS.set('/');
        GEN_DELIMS.set('?');
        GEN_DELIMS.set('#');
        GEN_DELIMS.set('[');
        GEN_DELIMS.set(']');
        GEN_DELIMS.set('@');

        SUB_DELIMS.set('!');
        SUB_DELIMS.set('$');
        SUB_DELIMS.set('&');
        SUB_DELIMS.set('\'');
        SUB_DELIMS.set('(');
        SUB_DELIMS.set(')');
        SUB_DELIMS.set('*');
        SUB_DELIMS.set('+');
        SUB_DELIMS.set(',');
        SUB_DELIMS.set(';');
        SUB_DELIMS.set('=');

        for (int i = 'a'; i <= 'z'; i++) {
            UNRESERVED.set(i);
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            UNRESERVED.set(i);
        }
        // numeric characters
        for (int i = '0'; i <= '9'; i++) {
            UNRESERVED.set(i);
        }
        UNRESERVED.set('-');
        UNRESERVED.set('.');
        UNRESERVED.set('_');
        UNRESERVED.set('~');
        URIC.or(SUB_DELIMS);
        URIC.or(UNRESERVED);
    }

    static final BitSet RFC5987_UNRESERVED = new BitSet(256);

    static {
        // Alphanumeric characters
        for (int i = 'a'; i <= 'z'; i++) {
            RFC5987_UNRESERVED.set(i);
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            RFC5987_UNRESERVED.set(i);
        }
        for (int i = '0'; i <= '9'; i++) {
            RFC5987_UNRESERVED.set(i);
        }

        // Additional characters as per RFC 5987 attr-char
        RFC5987_UNRESERVED.set('!');
        RFC5987_UNRESERVED.set('#');
        RFC5987_UNRESERVED.set('$');
        RFC5987_UNRESERVED.set('&');
        RFC5987_UNRESERVED.set('+');
        RFC5987_UNRESERVED.set('-');
        RFC5987_UNRESERVED.set('.');
        RFC5987_UNRESERVED.set('^');
        RFC5987_UNRESERVED.set('_');
        RFC5987_UNRESERVED.set('`');
        RFC5987_UNRESERVED.set('|');
        RFC5987_UNRESERVED.set('~');
    }

    static final BitSet PCHAR = new BitSet(256);
    static final BitSet USERINFO = new BitSet(256);
    static final BitSet REG_NAME = new BitSet(256);
    static final BitSet PATH_SEGMENT = new BitSet(256);
    static final BitSet QUERY = new BitSet(256);
    static final BitSet FRAGMENT = new BitSet(256);

    static {
        PCHAR.or(UNRESERVED);
        PCHAR.or(SUB_DELIMS);
        PCHAR.set(':');
        PCHAR.set('@');
        USERINFO.or(UNRESERVED);
        USERINFO.or(SUB_DELIMS);
        USERINFO.set(':');
        REG_NAME.or(UNRESERVED);
        REG_NAME.or(SUB_DELIMS);
        PATH_SEGMENT.or(PCHAR);
        QUERY.or(PCHAR);
        QUERY.set('/');
        QUERY.set('?');
        FRAGMENT.or(PCHAR);
        FRAGMENT.set('/');
        FRAGMENT.set('?');
    }

    private static final int RADIX = 16;

    static void encode(final StringBuilder buf, final CharSequence content, final Charset charset,
                       final BitSet safechars, final boolean blankAsPlus) {
        if (content == null) {
            return;
        }
        final CharBuffer cb = CharBuffer.wrap(content);
        final ByteBuffer bb = (charset != null ? charset : StandardCharsets.UTF_8).encode(cb);
        while (bb.hasRemaining()) {
            final int b = bb.get() & 0xff;
            if (safechars.get(b)) {
                buf.append((char) b);
            } else if (blankAsPlus && b == ' ') {
                buf.append("+");
            } else {
                buf.append("%");
                final char hex1 = Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, RADIX));
                final char hex2 = Character.toUpperCase(Character.forDigit(b & 0xF, RADIX));
                buf.append(hex1);
                buf.append(hex2);
            }
        }
    }

    static void encode(final StringBuilder buf, final CharSequence content, final Charset charset, final boolean blankAsPlus) {
        encode(buf, content, charset, UNRESERVED, blankAsPlus);
    }

    public static void encode(final StringBuilder buf, final CharSequence content, final Charset charset) {
        encode(buf, content, charset, UNRESERVED, false);
    }

    public static String encode(final CharSequence content, final Charset charset) {
        if (content == null) {
            return null;
        }
        final StringBuilder buf = new StringBuilder();
        encode(buf, content, charset, UNRESERVED, false);
        return buf.toString();
    }

    static String decode(final CharSequence content, final Charset charset, final boolean plusAsBlank) {
        if (content == null) {
            return null;
        }
        final ByteBuffer bb = ByteBuffer.allocate(content.length());
        final CharBuffer cb = CharBuffer.wrap(content);
        while (cb.hasRemaining()) {
            final char c = cb.get();
            if (c == '%' && cb.remaining() >= 2) {
                final char uc = cb.get();
                final char lc = cb.get();
                final int u = Character.digit(uc, RADIX);
                final int l = Character.digit(lc, RADIX);
                if (u != -1 && l != -1) {
                    bb.put((byte) ((u << 4) + l));
                } else {
                    bb.put((byte) '%');
                    bb.put((byte) uc);
                    bb.put((byte) lc);
                }
            } else if (plusAsBlank && c == '+') {
                bb.put((byte) ' ');
            } else {
                bb.put((byte) c);
            }
        }
        bb.flip();
        return (charset != null ? charset : StandardCharsets.UTF_8).decode(bb).toString();
    }

    public static String decode(final CharSequence content, final Charset charset) {
        return decode(content, charset, false);
    }

    public static final PercentCodec RFC3986 = new PercentCodec(UNRESERVED);
    public static final PercentCodec RFC5987 = new PercentCodec(RFC5987_UNRESERVED);

    private final BitSet unreserved;

    private PercentCodec(final BitSet unreserved) {
        this.unreserved = unreserved;
    }

    public PercentCodec() {
        this.unreserved = UNRESERVED;
    }

    /**
     * @since 5.3
     */
    public void encode(final StringBuilder buf, final CharSequence content) {
        encode(buf, content, StandardCharsets.UTF_8, unreserved, false);
    }

    /**
     * @since 5.3
     */
    public String encode(final CharSequence content) {
        if (content == null) {
            return null;
        }
        final StringBuilder buf = new StringBuilder();
        encode(buf, content, StandardCharsets.UTF_8, unreserved, false);
        return buf.toString();
    }

    /**
     * @since 5.3
     */
    public String decode(final CharSequence content) {
        return decode(content, StandardCharsets.UTF_8, false);
    }

}
