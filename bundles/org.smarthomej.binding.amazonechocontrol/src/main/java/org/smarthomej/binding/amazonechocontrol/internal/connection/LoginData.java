/**
 * Copyright (c) 2021-2023 Contributors to the SmartHome/J project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.smarthomej.binding.amazonechocontrol.internal.connection;

import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Scanner;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.util.HexUtils;

/**
 * The {@link LoginData} holds the login data and provides the methods for serialization and deserialization
 *
 * @author Jan N. Klug - Initial contribution
 */
@NonNullByDefault
public class LoginData {
    private static final String DEVICE_TYPE = "A2IVLV5VM2W81";

    private final Random rand = new Random();
    private final CookieManager cookieManager;

    // data fields
    public String frc;
    public String serial;
    public String deviceId;
    public @Nullable String refreshToken;
    public String amazonSite = "amazon.com";
    public String deviceName = "Unknown";
    public @Nullable String accountCustomerId;
    public @Nullable Date loginTime;
    private List<Cookie> cookies = new ArrayList<>();

    public LoginData(CookieManager cookieManager, String deviceId, String frc, String serial) {
        this.cookieManager = cookieManager;
        this.frc = frc;
        this.serial = serial;
        this.deviceId = deviceId;
    }

    public LoginData(CookieManager cookieManager) {
        this.cookieManager = cookieManager;

        // FRC
        byte[] frcBinary = new byte[313];
        rand.nextBytes(frcBinary);
        this.frc = Base64.getEncoder().encodeToString(frcBinary);

        // Serial number
        byte[] serialBinary = new byte[16];
        rand.nextBytes(serialBinary);
        this.serial = HexUtils.bytesToHex(serialBinary);

        // Device id 16 random bytes in upper-case hex format, a # as separator and a fixed DEVICE_TYPE
        byte[] bytes = new byte[16];
        rand.nextBytes(bytes);
        String hexStr = HexUtils.bytesToHex(bytes).toUpperCase() + "#" + DEVICE_TYPE;
        this.deviceId = HexUtils.bytesToHex(hexStr.getBytes());
    }

    public String serializeLoginData() {
        Date loginTime = this.loginTime;
        if (refreshToken == null || loginTime == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("7\n"); // version
        builder.append(frc).append("\n");
        builder.append(serial).append("\n");
        builder.append(deviceId).append("\n");
        builder.append(refreshToken).append("\n");
        builder.append(amazonSite).append("\n");
        builder.append(deviceName).append("\n");
        builder.append(accountCustomerId).append("\n");
        builder.append(loginTime.getTime()).append("\n");
        cookies = cookieManager.getCookieStore().getCookies().stream().map(LoginData.Cookie::fromHttpCookie)
                .collect(Collectors.toList());
        builder.append(cookies.size()).append("\n");
        cookies.forEach(cookie -> builder.append(cookie.serialize()));
        return builder.toString();
    }

    public boolean deserialize(String data) {
        Scanner scanner = new Scanner(data);
        String version = scanner.nextLine();
        // check if serialize version is supported
        if (!"7".equals(version)) {
            scanner.close();
            return false;
        }

        frc = scanner.nextLine();
        serial = scanner.nextLine();
        deviceId = scanner.nextLine();

        refreshToken = scanner.nextLine();
        amazonSite = scanner.nextLine();
        deviceName = scanner.nextLine();
        accountCustomerId = scanner.nextLine();
        loginTime = new Date(Long.parseLong(scanner.nextLine()));

        int numberOfCookies = Integer.parseInt(scanner.nextLine());
        for (int i = 0; i < numberOfCookies; i++) {
            cookies.add(Cookie.fromScanner(scanner));
        }
        scanner.close();

        CookieStore cookieStore = cookieManager.getCookieStore();
        cookieStore.removeAll();
        cookies.forEach(cookie -> cookieStore.add(null, cookie.toHttpCookie()));

        return true;
    }

    private static class Cookie {
        private final String name;
        private final String value;
        private final String comment;
        private final String commentURL;
        private final String domain;
        private final long maxAge;
        private final String path;
        private final String portlist;
        private final int version;
        private final boolean secure;
        private final boolean discard;

        private Cookie(String name, String value, String comment, String commentURL, String domain, long maxAge,
                String path, String portlist, int version, boolean secure, boolean discard) {
            this.name = name;
            this.value = value;
            this.comment = comment;
            this.commentURL = commentURL;
            this.domain = domain;
            this.maxAge = maxAge;
            this.path = path;
            this.portlist = portlist;
            this.version = version;
            this.secure = secure;
            this.discard = discard;
        }

        private static String readValue(Scanner scanner) {
            if (scanner.nextLine().equals("1")) {
                return Objects.requireNonNullElse(scanner.nextLine(), "");
            }
            return "";
        }

        private void writeValue(StringBuilder builder, @Nullable Object value) {
            if (value == null) {
                builder.append("0\n");
            } else {
                builder.append("1").append("\n").append(value).append("\n");
            }
        }

        public static Cookie fromScanner(Scanner scanner) {
            return new Cookie(readValue(scanner), readValue(scanner), readValue(scanner), readValue(scanner),
                    readValue(scanner), Long.parseLong(readValue(scanner)), readValue(scanner), readValue(scanner),
                    Integer.parseInt(readValue(scanner)), Boolean.parseBoolean(readValue(scanner)),
                    Boolean.parseBoolean(readValue(scanner)));
        }

        public String serialize() {
            StringBuilder builder = new StringBuilder();
            writeValue(builder, name);
            writeValue(builder, value);
            writeValue(builder, comment);
            writeValue(builder, commentURL);
            writeValue(builder, domain);
            writeValue(builder, maxAge);
            writeValue(builder, path);
            writeValue(builder, portlist);
            writeValue(builder, version);
            writeValue(builder, secure);
            writeValue(builder, discard);

            return builder.toString();
        }

        public static Cookie fromHttpCookie(HttpCookie cookie) {
            return new Cookie(cookie.getName(), cookie.getValue(), cookie.getComment(), cookie.getCommentURL(),
                    cookie.getDomain(), cookie.getMaxAge(), cookie.getPath(), cookie.getPortlist(), cookie.getVersion(),
                    cookie.getSecure(), cookie.getDiscard());
        }

        public HttpCookie toHttpCookie() {
            HttpCookie clientCookie = new HttpCookie(name, value);
            clientCookie.setComment(comment);
            clientCookie.setCommentURL(commentURL);
            clientCookie.setDomain(domain);
            clientCookie.setMaxAge(maxAge);
            clientCookie.setPath(path);
            clientCookie.setPortlist(portlist);
            clientCookie.setVersion(version);
            clientCookie.setSecure(secure);
            clientCookie.setDiscard(discard);
            return clientCookie;
        }
    }
}
