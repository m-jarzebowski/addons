/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smarthomej.binding.amazonechocontrol.internal.ConnectionException;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonAnnouncementContent;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonAnnouncementTarget;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonAscendingAlarm;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonAscendingAlarm.AscendingAlarmModel;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonAutomation;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonAutomation.Payload;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonBluetoothStates;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonBootstrapResult;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonBootstrapResult.Authentication;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonCustomerHistoryRecords;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonCustomerHistoryRecords.CustomerHistoryRecord;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonDeviceNotificationState;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonDeviceNotificationState.DeviceNotificationState;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonDevices;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonDevices.Device;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonDoNotDisturb;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonDoNotDisturb.DoNotDisturbDeviceStatus;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonEnabledFeeds;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonEqualizer;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonExchangeTokenResponse;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonExchangeTokenResponse.Cookie;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonFeed;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonMediaState;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonMusicProvider;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonNetworkDetails;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonNotificationRequest;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonNotificationResponse;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonNotificationSound;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonNotificationSounds;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonNotificationsResponse;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonPlaySearchPhraseOperationPayload;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonPlayValidationResult;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonPlayerState;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonPlaylists;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonRegisterAppRequest;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonRegisterAppResponse;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonRegisterAppResponse.Bearer;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonRegisterAppResponse.DeviceInfo;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonRegisterAppResponse.Extensions;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonRegisterAppResponse.MacDms;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonRegisterAppResponse.Response;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonRegisterAppResponse.Success;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonRegisterAppResponse.Tokens;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonRenewTokenResponse;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonSmartHomeDevice;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonSmartHomeGroups.SmartHomeGroup;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonStartRoutineRequest;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonUsersMeResponse;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonWakeWords;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonWakeWords.WakeWord;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonWebSiteCookie;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.SmartHomeBaseDevice;
import org.unbescape.json.JsonEscape;
import org.unbescape.json.JsonEscapeLevel;
import org.unbescape.json.JsonEscapeType;
import org.unbescape.xml.XmlEscape;
import org.unbescape.xml.XmlEscapeLevel;
import org.unbescape.xml.XmlEscapeType;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link Connection} is responsible for the connection to the amazon server
 * and handling of the commands
 *
 * @author Michael Geramb - Initial contribution
 */
@NonNullByDefault
public class Connection {
    private static final String THING_THREADPOOL_NAME = "thingHandler";
    private static final long EXPIRES_IN = 432000; // five days
    private static final Pattern CHARSET_PATTERN = Pattern.compile("(?i)\\bcharset=\\s*\"?([^\\s;\"]*)");
    private static final String USER_AGENT = "AmazonWebView/Amazon Alexa/2.2.443692.0/iOS/14.8/iPhone";

    private final Logger logger = LoggerFactory.getLogger(Connection.class);

    protected final ScheduledExecutorService scheduler = ThreadPoolManager.getScheduledPool(THING_THREADPOOL_NAME);

    private final CookieManager cookieManager = new CookieManager();
    private final Gson gson;
    private final Gson gsonWithNullSerialization;

    private final LoginData loginData;
    private String alexaServer = "https://alexa.amazon.com";
    private @Nullable Date verifyTime;
    private long renewTime = 0;
    private @Nullable String customerName;
    private @Nullable MacDms macDms;

    private final Map<Integer, AnnouncementWrapper> announcements = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<Integer, TextWrapper> textToSpeeches = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<Integer, TextWrapper> textCommands = Collections.synchronizedMap(new LinkedHashMap<>());

    private final Map<Integer, Volume> volumes = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, LinkedBlockingQueue<QueueObject>> devices = Collections
            .synchronizedMap(new LinkedHashMap<>());

    private final Map<TimerType, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();
    private final Map<TimerType, Lock> locks = new ConcurrentHashMap<>();

    private enum TimerType {
        ANNOUNCEMENT,
        TTS,
        VOLUME,
        DEVICES,
        TEXT_COMMAND
    }

    public Connection(@Nullable Connection oldConnection, Gson gson) {
        this.gson = gson;
        if (oldConnection != null) {
            this.loginData = new LoginData(cookieManager, oldConnection.getDeviceId(), oldConnection.getFrc(),
                    oldConnection.getSerial());
        } else {
            this.loginData = new LoginData(cookieManager);
        }

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonWithNullSerialization = gsonBuilder.create();

        replaceTimer(TimerType.DEVICES,
                scheduler.scheduleWithFixedDelay(this::handleExecuteSequenceNode, 0, 500, TimeUnit.MILLISECONDS));
    }

    private void setAmazonSite(@Nullable String amazonSite) {
        String correctedAmazonSite = Objects.requireNonNullElse(amazonSite, "amazon.com").toLowerCase();
        if (correctedAmazonSite.startsWith("http://")) {
            correctedAmazonSite = correctedAmazonSite.substring(7);
        }
        if (correctedAmazonSite.startsWith("https://")) {
            correctedAmazonSite = correctedAmazonSite.substring(8);
        }
        if (correctedAmazonSite.startsWith("www.")) {
            correctedAmazonSite = correctedAmazonSite.substring(4);
        }
        if (correctedAmazonSite.startsWith("alexa.")) {
            correctedAmazonSite = correctedAmazonSite.substring(6);
        }
        this.loginData.amazonSite = correctedAmazonSite;
        this.alexaServer = "https://alexa." + correctedAmazonSite;
    }

    public LoginData getLoginData() {
        // update cookies
        return loginData;
    }

    public @Nullable Date tryGetVerifyTime() {
        return verifyTime;
    }

    public String getFrc() {
        return loginData.frc;
    }

    public String getSerial() {
        return loginData.serial;
    }

    public String getDeviceId() {
        return loginData.deviceId;
    }

    public String getAmazonSite() {
        return loginData.amazonSite;
    }

    public String getAlexaServer() {
        return alexaServer;
    }

    public @Nullable MacDms getMacDms() {
        return this.macDms;
    }

    public String getDeviceName() {
        return this.loginData.deviceName;
    }

    public String getCustomerId() {
        return Objects.requireNonNullElse(loginData.accountCustomerId, "Unknown");
    }

    public String getCustomerName() {
        return Objects.requireNonNullElse(customerName, "Unknown");
    }

    public boolean isSequenceNodeQueueRunning() {
        return devices.values().stream().anyMatch(
                (queueObjects) -> (queueObjects.stream().anyMatch(queueObject -> queueObject.future != null)));
    }

    public boolean tryRestoreLogin(@Nullable String data, @Nullable String overloadedDomain) {
        Date loginTime = tryRestoreSessionData(data, overloadedDomain);
        if (loginTime != null) {
            try {
                if (verifyLogin()) {
                    this.loginData.loginTime = loginTime;
                    return true;
                }
            } catch (ConnectionException e) {
                // no action
            }
        }
        return false;
    }

    private @Nullable Date tryRestoreSessionData(@Nullable String data, @Nullable String overloadedDomain) {
        // verify stored data
        if (data == null || data.isEmpty() || !loginData.deserialize(data)) {
            return null;
        }

        if (overloadedDomain != null) {
            loginData.amazonSite = overloadedDomain;
        }

        setAmazonSite(loginData.amazonSite);

        try {
            checkRenewSession();

            String accountCustomerId = this.loginData.accountCustomerId;
            if (accountCustomerId == null || accountCustomerId.isEmpty()) {
                List<Device> devices = this.getDeviceList();
                accountCustomerId = devices.stream().filter(device -> loginData.serial.equals(device.serialNumber))
                        .findAny().map(device -> device.deviceOwnerCustomerId).orElse(null);
                if (accountCustomerId == null || accountCustomerId.isEmpty()) {
                    accountCustomerId = devices.stream().filter(device -> "This Device".equals(device.accountName))
                            .findAny().map(device -> {
                                loginData.serial = Objects.requireNonNullElse(device.serialNumber, loginData.serial);
                                return device.deviceOwnerCustomerId;
                            }).orElse(null);
                }
                this.loginData.accountCustomerId = accountCustomerId;
            }
        } catch (URISyntaxException | ConnectionException e) {
            logger.debug("Getting account customer Id failed", e);
        }
        return loginData.loginTime;
    }

    private @Nullable Authentication tryGetBootstrap() throws ConnectionException {
        HttpsURLConnection connection = makeRequest("GET", alexaServer + "/api/bootstrap", null, false, false, Map.of(),
                0);
        String contentType = connection.getContentType();
        try {
            if (connection.getResponseCode() == 200 && contentType != null
                    && contentType.toLowerCase().startsWith("application/json")) {
                String bootstrapResultJson = convertStream(connection);
                JsonBootstrapResult result = parseJson(bootstrapResultJson, JsonBootstrapResult.class);
                if (result != null) {
                    Authentication authentication = result.authentication;
                    if (authentication != null && authentication.authenticated) {
                        this.customerName = authentication.customerName;
                        if (this.loginData.accountCustomerId == null) {
                            this.loginData.accountCustomerId = authentication.customerId;
                        }
                        return authentication;
                    }
                }
            }
        } catch (JsonSyntaxException | IllegalStateException e) {
            logger.info("No valid json received", e);
            return null;
        } catch (IOException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
        return null;
    }

    public String convertStream(HttpsURLConnection connection) throws ConnectionException {
        try (InputStream input = connection.getInputStream()) {
            if (input == null) {
                return "";
            }

            InputStream readerStream;
            if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
                readerStream = new GZIPInputStream(input);
            } else {
                readerStream = input;
            }
            String contentType = connection.getContentType();
            String charSet = StandardCharsets.UTF_8.name();

            if (contentType != null) {
                Matcher m = CHARSET_PATTERN.matcher(contentType);
                if (m.find()) {
                    String foundCharset = m.group(1).trim().toUpperCase();
                    if (!foundCharset.isEmpty()) {
                        charSet = foundCharset;
                    }
                }
            }

            Scanner inputScanner = new Scanner(readerStream, charSet);
            Scanner scannerWithoutDelimiter = inputScanner.useDelimiter("\\A");
            String result = scannerWithoutDelimiter.hasNext() ? scannerWithoutDelimiter.next() : "";
            inputScanner.close();
            scannerWithoutDelimiter.close();
            input.close();

            return result;
        } catch (IOException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
    }

    public String makeRequestAndReturnString(String url) throws ConnectionException {
        return makeRequestAndReturnString("GET", url, null, false, Map.of());
    }

    public String makeRequestAndReturnString(String requestMethod, String url, @Nullable String postData, boolean json,
            Map<String, String> customHeaders) throws ConnectionException {
        HttpsURLConnection connection = makeRequest(requestMethod, url, postData, json, true, customHeaders, 3);
        String result = convertStream(connection);
        logger.trace("Result of {} {}:{}", requestMethod, url, result);
        return result;
    }

    public HttpsURLConnection makeRequest(String requestMethod, String url, @Nullable String postData, boolean json,
            boolean autoredirect, Map<String, String> customHeaders, int badRequestRepeats) throws ConnectionException {
        String currentUrl = url;
        int redirectCounter = 0;
        int retryCounter = 0;
        // loop for handling redirect and bad request, using automatic redirect is not
        // possible, because all response headers must be catched
        while (true) {
            int code;
            HttpsURLConnection connection = null;
            try {
                logger.debug("Make request to {}", url);
                connection = (HttpsURLConnection) new URL(currentUrl).openConnection();
                connection.setRequestMethod(requestMethod);
                connection.setRequestProperty("Accept-Language", "en-US");
                if (!customHeaders.containsKey("User-Agent")) {
                    connection.setRequestProperty("User-Agent", USER_AGENT);
                }
                connection.setRequestProperty("Accept-Encoding", "gzip");
                connection.setRequestProperty("DNT", "1");
                connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
                for (Map.Entry<String, String> header : customHeaders.entrySet()) {
                    if (!header.getValue().isEmpty()) {
                        connection.setRequestProperty(header.getKey(), header.getValue());
                    }
                }
                connection.setInstanceFollowRedirects(false);

                // add cookies
                URI uri = connection.getURL().toURI();

                if (!customHeaders.containsKey("Cookie")) {
                    StringBuilder cookieHeaderBuilder = new StringBuilder();
                    for (HttpCookie cookie : cookieManager.getCookieStore().get(uri)) {
                        if (cookieHeaderBuilder.length() > 0) {
                            cookieHeaderBuilder.append(";");
                        }
                        cookieHeaderBuilder.append(cookie.getName());
                        cookieHeaderBuilder.append("=");
                        cookieHeaderBuilder.append(cookie.getValue());
                        if (cookie.getName().equals("csrf")) {
                            connection.setRequestProperty("csrf", cookie.getValue());
                        }

                    }
                    if (cookieHeaderBuilder.length() > 0) {
                        String cookies = cookieHeaderBuilder.toString();
                        connection.setRequestProperty("Cookie", cookies);
                    }
                }
                if (postData != null) {
                    logger.debug("{}: {}", requestMethod, postData);
                    // post data
                    byte[] postDataBytes = postData.getBytes(StandardCharsets.UTF_8);
                    int postDataLength = postDataBytes.length;

                    connection.setFixedLengthStreamingMode(postDataLength);

                    if (json) {
                        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    } else {
                        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    }
                    connection.setRequestProperty("Content-Length", Integer.toString(postDataLength));
                    if ("POST".equals(requestMethod)) {
                        connection.setRequestProperty("Expect", "100-continue");
                    }

                    connection.setDoOutput(true);
                    OutputStream outputStream = connection.getOutputStream();
                    outputStream.write(postDataBytes);
                    outputStream.close();
                }
                // handle result
                code = connection.getResponseCode();
                String location = null;

                // handle response headers
                Map<@Nullable String, List<String>> headerFields = connection.getHeaderFields();
                for (Map.Entry<@Nullable String, List<String>> header : headerFields.entrySet()) {
                    String key = header.getKey();
                    if (key != null && !key.isEmpty()) {
                        if ("Set-Cookie".equalsIgnoreCase(key)) {
                            // store cookie
                            for (String cookieHeader : header.getValue()) {
                                if (!cookieHeader.isEmpty()) {
                                    List<HttpCookie> cookies = HttpCookie.parse(cookieHeader);
                                    for (HttpCookie cookie : cookies) {
                                        cookieManager.getCookieStore().add(uri, cookie);
                                    }
                                }
                            }
                        }
                        if ("Location".equalsIgnoreCase(key)) {
                            // get redirect location
                            location = header.getValue().get(0);
                            if (!location.isEmpty()) {
                                location = uri.resolve(location).toString();
                                // check for https
                                if (location.toLowerCase().startsWith("http://")) {
                                    // always use https
                                    location = "https://" + location.substring(7);
                                    logger.debug("Redirect corrected to {}", location);
                                }
                            }
                        }
                    }
                }
                if (code == 200) {
                    logger.debug("Call to {} succeeded", url);
                    return connection;
                } else if (code == 301 || code == 302 && location != null) {
                    logger.debug("Redirected to {}", location);
                    redirectCounter++;
                    if (redirectCounter > 30) {
                        throw new ConnectionException("Too many redirects");
                    }
                    currentUrl = location;
                    if (autoredirect) {
                        continue; // repeat with new location
                    }
                    return connection;
                } else if (code == 400 && "QUEUE_EXPIRED".equals(connection.getHeaderField("x-amzn-error"))) {
                    // handle queue expired
                    throw new ConnectionException("Queue expired");
                } else {
                    logger.debug("Retry call to {}", url);
                    retryCounter++;
                    if (retryCounter > badRequestRepeats) {
                        throw new ConnectionException(requestMethod + " url '" + url + "' failed with code " + code
                                + ": " + connection.getResponseMessage());
                    }
                    Thread.sleep(2000);
                }
            } catch (Exception e) {
                if (connection != null) {
                    connection.disconnect();
                }
                logger.debug("Request to url '{}' fails:", url, e);
                throw new ConnectionException("Request failed", e);
            }
        }
    }

    public void registerConnectionAsApp(String oAutRedirectUrl)
            throws ConnectionException, IOException, URISyntaxException, InterruptedException {
        URI oAutRedirectUri = new URI(oAutRedirectUrl);

        Map<String, String> queryParameters = new LinkedHashMap<>();
        String query = oAutRedirectUri.getQuery();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            queryParameters.put(URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name()),
                    URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name()));
        }
        String accessToken = queryParameters.get("openid.oa2.access_token");

        reRegisterConnectionAsApp(accessToken);
    }

    private void reRegisterConnectionAsApp(@Nullable String accessToken) throws ConnectionException {
        List<JsonWebSiteCookie> webSiteCookies = new ArrayList<>();
        for (HttpCookie cookie : getSessionCookies("https://www.amazon.com")) {
            webSiteCookies.add(new JsonWebSiteCookie(cookie.getName(), cookie.getValue()));
        }

        JsonRegisterAppRequest registerAppRequest = new JsonRegisterAppRequest(loginData.serial, accessToken,
                loginData.frc, webSiteCookies);
        String registerAppRequestJson = gson.toJson(registerAppRequest);

        String registerAppResultJson = makeRequestAndReturnString("POST", "https://api.amazon.com/auth/register",
                registerAppRequestJson, true, Map.of("x-amzn-identity-auth-domain", "api.amazon.com"));
        JsonRegisterAppResponse registerAppResponse = parseJson(registerAppResultJson, JsonRegisterAppResponse.class);

        if (registerAppResponse == null) {
            throw new ConnectionException("Error: No response received from register application");
        }
        Response response = registerAppResponse.response;
        if (response == null) {
            throw new ConnectionException("Error: No response received from register application");
        }
        Success success = response.success;
        if (success == null) {
            throw new ConnectionException("Error: No success received from register application");
        }
        Tokens tokens = success.tokens;
        if (tokens == null) {
            throw new ConnectionException("Error: No tokens received from register application");
        }
        Bearer bearer = tokens.bearer;
        if (bearer == null) {
            throw new ConnectionException("Error: No bearer received from register application");
        }
        String refreshToken = bearer.refreshToken;

        this.macDms = tokens.macDms;

        this.loginData.refreshToken = refreshToken;
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new ConnectionException("Error: No refresh token received");
        }
        try {
            exchangeToken();
            // Check which is the owner domain
            String usersMeResponseJson = makeRequestAndReturnString(
                    "https://alexa.amazon.com/api/users/me?platform=ios&version=2.2.443692.0");
            JsonUsersMeResponse usersMeResponse = parseJson(usersMeResponseJson, JsonUsersMeResponse.class);
            if (usersMeResponse == null) {
                throw new IllegalStateException();
            }
            URI uri = new URI(usersMeResponse.marketPlaceDomainName);
            String host = uri.getHost();

            // Switch to owner domain
            setAmazonSite(host);
            exchangeToken();
            tryGetBootstrap();
        } catch (Exception e) {
            logout();
            throw new ConnectionException(e.getMessage(), e);
        }

        String deviceName = null;
        Extensions extensions = success.extensions;
        if (extensions != null) {
            DeviceInfo deviceInfo = extensions.deviceInfo;
            if (deviceInfo != null) {
                deviceName = deviceInfo.deviceName;
            }
        }

        this.loginData.deviceName = Objects.requireNonNullElse(deviceName, "Unknown");
    }

    private void exchangeToken() throws ConnectionException {
        this.renewTime = 0;
        String cookiesJson = "{\"cookies\":{\"." + getAmazonSite() + "\":[]}}";
        String cookiesBase64 = Base64.getEncoder().encodeToString(cookiesJson.getBytes());

        String exchangePostData = "di.os.name=iOS&app_version=2.2.443692.0&domain=." + getAmazonSite()
                + "&source_token=" + URLEncoder.encode(this.loginData.refreshToken, StandardCharsets.UTF_8)
                + "&requested_token_type=auth_cookies&source_token_type=refresh_token&di.hw.version=iPhone&di.sdk.version=6.10.0&cookies="
                + cookiesBase64 + "&app_name=Amazon%20Alexa&di.os.version=14.8";

        String exchangeTokenJson = makeRequestAndReturnString("POST",
                "https://www." + getAmazonSite() + "/ap/exchangetoken", exchangePostData, false, Map.of("Cookie", ""));
        JsonExchangeTokenResponse exchangeTokenResponse = Objects
                .requireNonNull(gson.fromJson(exchangeTokenJson, JsonExchangeTokenResponse.class));

        org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonExchangeTokenResponse.Response response = exchangeTokenResponse.response;
        if (response != null) {
            org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonExchangeTokenResponse.Tokens tokens = response.tokens;
            if (tokens != null) {
                Map<String, Cookie[]> cookiesMap = tokens.cookies;
                if (cookiesMap != null) {
                    for (String domain : cookiesMap.keySet()) {
                        Cookie[] cookies = cookiesMap.get(domain);
                        if (cookies != null) {
                            for (Cookie cookie : cookies) {
                                if (cookie != null) {
                                    HttpCookie httpCookie = new HttpCookie(cookie.name, cookie.value);
                                    httpCookie.setPath(cookie.path);
                                    httpCookie.setDomain(domain);
                                    Boolean secure = cookie.secure;
                                    if (secure != null) {
                                        httpCookie.setSecure(secure);
                                    }
                                    this.cookieManager.getCookieStore().add(null, httpCookie);
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!verifyLogin()) {
            throw new ConnectionException("Verify login failed after token exchange");
        }
        this.renewTime = (long) (System.currentTimeMillis() + Connection.EXPIRES_IN * 1000d / 0.8d); // start renew at
    }

    public boolean checkRenewSession() throws URISyntaxException, ConnectionException {
        if (System.currentTimeMillis() >= this.renewTime) {
            String renewTokenPostData = "app_name=Amazon%20Alexa&app_version=2.2.443692.0&di.sdk.version=6.10.0&source_token="
                    + URLEncoder.encode(loginData.refreshToken, StandardCharsets.UTF_8)
                    + "&package_name=com.amazon.echo&di.hw.version=iPhone&platform=iOS&requested_token_type=access_token&source_token_type=refresh_token&di.os.name=iOS&di.os.version=14.8&current_version=6.10.0";
            String renewTokenResponseJson = makeRequestAndReturnString("POST", "https://api.amazon.com/auth/token",
                    renewTokenPostData, false, Map.of());
            if (this.macDms == null) {
                JsonRenewTokenResponse tokenResponse = parseJson(renewTokenResponseJson, JsonRenewTokenResponse.class);
                if (tokenResponse == null) {
                    return false;
                }
                reRegisterConnectionAsApp(tokenResponse.accessToken);
            } else {
                exchangeToken();
            }
            return true;
        }
        return false;
    }

    public boolean getIsLoggedIn() {
        return loginData.loginTime != null;
    }

    public String getLoginPage() throws ConnectionException {
        // clear session data
        logout();

        logger.debug("Start Login to {}", alexaServer);

        String mapMdJson = "{\"device_user_dictionary\":[],\"device_registration_data\":{\"software_version\":\"1\"},\"app_identifier\":{\"app_version\":\"2.2.443692\",\"bundle_id\":\"com.amazon.echo\"}}";
        String mapMdCookie = Base64.getEncoder().encodeToString(mapMdJson.getBytes());

        cookieManager.getCookieStore().add(URI.create("https://www.amazon.com"), new HttpCookie("map-md", mapMdCookie));
        cookieManager.getCookieStore().add(URI.create("https://www.amazon.com"), new HttpCookie("frc", loginData.frc));

        String loginFormHtml = makeRequestAndReturnString("GET", "https://www.amazon.com"
                + "/ap/signin?openid.return_to=https://www.amazon.com/ap/maplanding&openid.assoc_handle=amzn_dp_project_dee_ios&openid.identity=http://specs.openid.net/auth/2.0/identifier_select&pageId=amzn_dp_project_dee_ios&accountStatusPolicy=P1&openid.claimed_id=http://specs.openid.net/auth/2.0/identifier_select&openid.mode=checkid_setup&openid.ns.oa2=http://www.amazon.com/ap/ext/oauth/2&openid.oa2.client_id=device:"
                + loginData.deviceId
                + "&openid.ns.pape=http://specs.openid.net/extensions/pape/1.0&openid.oa2.response_type=token&openid.ns=http://specs.openid.net/auth/2.0&openid.pape.max_auth_age=0&openid.oa2.scope=device_auth_access",
                null, false, Map.of("authority", "www.amazon.com"));

        logger.debug("Received login form {}", loginFormHtml);
        return loginFormHtml;
    }

    public boolean verifyLogin() throws ConnectionException {
        if (this.loginData.refreshToken == null) {
            return false;
        }
        Authentication authentication = tryGetBootstrap();
        if (authentication != null && authentication.authenticated) {
            verifyTime = new Date();
            if (loginData.loginTime == null) {
                loginData.loginTime = verifyTime;
            }
            return true;
        }
        return false;
    }

    public List<HttpCookie> getSessionCookies(String server) {
        try {
            return cookieManager.getCookieStore().get(new URI(server));
        } catch (URISyntaxException e) {
            return List.of();
        }
    }

    // current value in compute can be null
    private void replaceTimer(TimerType type, @Nullable ScheduledFuture<?> newTimer) {
        timers.compute(type, (timerType, oldTimer) -> {
            if (oldTimer != null) {
                oldTimer.cancel(true);
            }
            return newTimer;
        });
    }

    public void logout() {
        cookieManager.getCookieStore().removeAll();
        // reset all members
        loginData.refreshToken = null;
        loginData.loginTime = null;
        verifyTime = null;

        replaceTimer(TimerType.ANNOUNCEMENT, null);
        announcements.clear();
        replaceTimer(TimerType.TTS, null);
        textToSpeeches.clear();
        replaceTimer(TimerType.VOLUME, null);
        volumes.clear();
        replaceTimer(TimerType.DEVICES, null);
        textCommands.clear();
        replaceTimer(TimerType.TTS, null);

        devices.values().forEach((queueObjects) -> queueObjects.forEach((queueObject) -> {
            Future<?> future = queueObject.future;
            if (future != null) {
                future.cancel(true);
                queueObject.future = null;
            }
        }));
    }

    // parser
    private <T> @Nullable T parseJson(String json, Class<T> type) throws JsonSyntaxException, IllegalStateException {
        try {
            // gson.fromJson is non-null if json is non-null and not empty
            return gson.fromJson(json, type);
        } catch (JsonParseException | IllegalStateException e) {
            logger.warn("Parsing json failed: {}", json, e);
            throw e;
        }
    }

    // commands and states
    public List<WakeWord> getWakeWords() {
        String json;
        try {
            json = makeRequestAndReturnString(alexaServer + "/api/wake-word?cached=true");
            JsonWakeWords wakeWords = parseJson(json, JsonWakeWords.class);
            if (wakeWords == null) {
                return List.of();
            }
            return Objects.requireNonNullElse(wakeWords.wakeWords, List.of());
        } catch (ConnectionException e) {
            logger.info("getting wakewords failed", e);
        }
        return List.of();
    }

    public List<SmartHomeBaseDevice> getSmarthomeDeviceList() throws ConnectionException {
        try {
            String json = makeRequestAndReturnString(alexaServer + "/api/phoenix");
            logger.debug("getSmartHomeDevices result: {}", json);

            JsonNetworkDetails networkDetails = parseJson(json, JsonNetworkDetails.class);
            if (networkDetails == null) {
                return List.of();
            }
            Object jsonObject = gson.fromJson(networkDetails.networkDetail, Object.class);
            List<SmartHomeBaseDevice> result = new ArrayList<>();
            searchSmartHomeDevicesRecursive(jsonObject, result);

            return result;
        } catch (ConnectionException e) {
            logger.warn("getSmartHomeDevices fails: {}", e.getMessage());
            throw e;
        }
    }

    private void searchSmartHomeDevicesRecursive(@Nullable Object jsonNode, List<SmartHomeBaseDevice> devices) {
        if (jsonNode instanceof Map) {
            @SuppressWarnings({ "rawtypes", "unchecked" })
            Map<String, Object> map = (Map) jsonNode;
            if (map.containsKey("entityId") && map.containsKey("friendlyName") && map.containsKey("actions")) {
                // device node found, create type element and add it to the results
                JsonElement element = gson.toJsonTree(jsonNode);
                JsonSmartHomeDevice shd = parseJson(element.toString(), JsonSmartHomeDevice.class);
                if (shd != null) {
                    devices.add(shd);
                }
            } else if (map.containsKey("applianceGroupName")) {
                JsonElement element = gson.toJsonTree(jsonNode);
                SmartHomeGroup shg = parseJson(element.toString(), SmartHomeGroup.class);
                if (shg != null) {
                    devices.add(shg);
                }
            } else {
                map.values().forEach(value -> searchSmartHomeDevicesRecursive(value, devices));
            }
        }
    }

    public List<Device> getDeviceList() throws ConnectionException {
        JsonDevices devices = Objects.requireNonNull(parseJson(getDeviceListJson(), JsonDevices.class));
        logger.trace("Devices {}", devices.devices);

        // @Nullable because of a limitation of the null-checker, we filter null-serialNumbers before
        Set<@Nullable String> serialNumbers = ConcurrentHashMap.newKeySet();
        return devices.devices.stream().filter(d -> d.serialNumber != null && serialNumbers.add(d.serialNumber))
                .collect(Collectors.toList());
    }

    public String getDeviceListJson() throws ConnectionException {
        String json = makeRequestAndReturnString(alexaServer + "/api/devices-v2/device?cached=false");
        return json;
    }

    public Map<String, JsonArray> getSmartHomeDeviceStatesJson(Set<SmartHomeBaseDevice> devices)
            throws ConnectionException {
        JsonObject requestObject = new JsonObject();
        JsonArray stateRequests = new JsonArray();
        Map<String, String> mergedApplianceMap = new HashMap<>();
        for (SmartHomeBaseDevice device : devices) {
            String applianceId = device.findId();
            if (applianceId != null) {
                JsonObject stateRequest;
                if (device instanceof JsonSmartHomeDevice
                        && ((JsonSmartHomeDevice) device).mergedApplianceIds != null) {
                    List<String> mergedApplianceIds = Objects
                            .requireNonNullElse(((JsonSmartHomeDevice) device).mergedApplianceIds, List.of());
                    for (String idToMerge : mergedApplianceIds) {
                        mergedApplianceMap.put(idToMerge, applianceId);
                        stateRequest = new JsonObject();
                        stateRequest.addProperty("entityId", idToMerge);
                        stateRequest.addProperty("entityType", "APPLIANCE");
                        stateRequests.add(stateRequest);
                    }
                } else {
                    stateRequest = new JsonObject();
                    stateRequest.addProperty("entityId", applianceId);
                    stateRequest.addProperty("entityType", "APPLIANCE");
                    stateRequests.add(stateRequest);
                }
            }
        }
        requestObject.add("stateRequests", stateRequests);
        String requestBody = requestObject.toString();
        String json = makeRequestAndReturnString("POST", alexaServer + "/api/phoenix/state", requestBody, true,
                Map.of());
        logger.trace("Requested {} and received {}", requestBody, json);

        JsonObject responseObject = Objects.requireNonNull(gson.fromJson(json, JsonObject.class));
        JsonArray deviceStates = (JsonArray) responseObject.get("deviceStates");
        Map<String, JsonArray> result = new HashMap<>();
        for (JsonElement deviceState : deviceStates) {
            JsonObject deviceStateObject = deviceState.getAsJsonObject();
            JsonObject entity = deviceStateObject.get("entity").getAsJsonObject();
            String applianceId = entity.get("entityId").getAsString();
            JsonElement capabilityState = deviceStateObject.get("capabilityStates");
            if (capabilityState != null && capabilityState.isJsonArray()) {
                String realApplianceId = mergedApplianceMap.get(applianceId);
                if (realApplianceId != null) {
                    var capabilityArray = result.get(realApplianceId);
                    if (capabilityArray != null) {
                        capabilityArray.addAll(capabilityState.getAsJsonArray());
                        result.put(realApplianceId, capabilityArray);
                    } else {
                        result.put(realApplianceId, capabilityState.getAsJsonArray());
                    }
                } else {
                    result.put(applianceId, capabilityState.getAsJsonArray());
                }
            }
        }
        return result;
    }

    public @Nullable JsonPlayerState getPlayer(Device device) throws ConnectionException {
        String json = makeRequestAndReturnString(alexaServer + "/api/np/player?deviceSerialNumber="
                + device.serialNumber + "&deviceType=" + device.deviceType + "&screenWidth=1440");
        return parseJson(json, JsonPlayerState.class);
    }

    public @Nullable JsonMediaState getMediaState(Device device) throws ConnectionException {
        String json = makeRequestAndReturnString(alexaServer + "/api/media/state?deviceSerialNumber="
                + device.serialNumber + "&deviceType=" + device.deviceType);
        return parseJson(json, JsonMediaState.class);
    }

    public List<CustomerHistoryRecord> getActivities(@Nullable Long startTime, @Nullable Long endTime) {
        try {
            String json = makeRequestAndReturnString(
                    "https://" + getAmazonSite() + "/alexa-privacy/apd/rvh/customer-history-records?startTime="
                            + (startTime != null ? startTime : "") + "&endTime=" + (endTime != null ? endTime : "")
                            + "&maxRecordSize=1");
            JsonCustomerHistoryRecords customerHistoryRecords = parseJson(json, JsonCustomerHistoryRecords.class);
            if (customerHistoryRecords == null) {
                return List.of();
            }
            return Objects.requireNonNullElse(customerHistoryRecords.customerHistoryRecords, List.of());
        } catch (ConnectionException e) {
            logger.info("getting activities failed", e);
        }
        return List.of();
    }

    public JsonBluetoothStates getBluetoothConnectionStates() {
        try {
            String json = makeRequestAndReturnString(alexaServer + "/api/bluetooth?cached=true");
            return Objects.requireNonNullElse(parseJson(json, JsonBluetoothStates.class), new JsonBluetoothStates());
        } catch (ConnectionException e) {
            logger.debug("failed to get bluetooth state: {}", e.getMessage());
            return new JsonBluetoothStates();
        }
    }

    public @Nullable JsonPlaylists getPlaylists(Device device) throws ConnectionException {
        String json = makeRequestAndReturnString(
                alexaServer + "/api/cloudplayer/playlists?deviceSerialNumber=" + device.serialNumber + "&deviceType="
                        + device.deviceType + "&mediaOwnerCustomerId=" + getCustomerId(device.deviceOwnerCustomerId));
        return parseJson(json, JsonPlaylists.class);
    }

    public void command(Device device, String command) throws ConnectionException {
        String url = alexaServer + "/api/np/command?deviceSerialNumber=" + device.serialNumber + "&deviceType="
                + device.deviceType;
        makeRequest("POST", url, command, true, true, Map.of(), 0);
    }

    public void smartHomeCommand(String entityId, String action) throws IOException, InterruptedException {
        smartHomeCommand(entityId, action, Map.of());
    }

    public void smartHomeCommand(String entityId, String action, Map<String, Object> values)
            throws IOException, InterruptedException {
        String url = alexaServer + "/api/phoenix/state";

        JsonObject json = new JsonObject();
        JsonArray controlRequests = new JsonArray();
        JsonObject controlRequest = new JsonObject();
        controlRequest.addProperty("entityId", entityId);
        controlRequest.addProperty("entityType", "APPLIANCE");
        JsonObject parameters = new JsonObject();
        parameters.addProperty("action", action);
        if (!values.isEmpty()) {
            values.forEach((property, value) -> {
                if (value instanceof QuantityType<?>) {
                    JsonObject propertyObj = new JsonObject();
                    propertyObj.addProperty("value", Double.toString(((QuantityType<?>) value).doubleValue()));
                    propertyObj.addProperty("scale",
                            ((QuantityType<?>) value).getUnit().equals(SIUnits.CELSIUS) ? "celsius" : "fahrenheit");
                    parameters.add(property, propertyObj);
                } else if (value instanceof Boolean) {
                    parameters.addProperty(property, (boolean) value);
                } else if (value instanceof String) {
                    parameters.addProperty(property, (String) value);
                } else if (value instanceof StringType) {
                    JsonObject propertyObj = new JsonObject();
                    propertyObj.addProperty("value", value.toString());
                    parameters.add(property, propertyObj);
                } else if (value instanceof Number) {
                    parameters.addProperty(property, (Number) value);
                } else if (value instanceof Character) {
                    parameters.addProperty(property, (Character) value);
                } else if (value instanceof JsonElement) {
                    parameters.add(property, (JsonElement) value);
                }
            });
        }
        controlRequest.add("parameters", parameters);
        controlRequests.add(controlRequest);
        json.add("controlRequests", controlRequests);

        String requestBody = json.toString();
        try {
            String resultBody = makeRequestAndReturnString("PUT", url, requestBody, true, Map.of());
            logger.trace("Request '{}' resulted in '{}", requestBody, resultBody);
            JsonObject result = parseJson(resultBody, JsonObject.class);
            if (result == null) {
                return;
            }
            JsonElement errors = result.get("errors");
            if (errors != null && errors.isJsonArray()) {
                JsonArray errorList = errors.getAsJsonArray();
                if (errorList.size() > 0) {
                    logger.warn("Smart home device command failed. The request '{}' resulted in error(s): {}",
                            requestBody, StreamSupport.stream(errorList.spliterator(), false).map(JsonElement::toString)
                                    .collect(Collectors.joining(" / ")));
                }
            }
        } catch (ConnectionException e) {
            logger.warn("Request URL '{}' failed '{}': {}", url, requestBody, e.getMessage());
        }
    }

    public void notificationVolume(Device device, int volume) throws ConnectionException {
        String url = alexaServer + "/api/device-notification-state/" + device.deviceType + "/" + device.softwareVersion
                + "/" + device.serialNumber;
        String command = "{\"deviceSerialNumber\":\"" + device.serialNumber + "\",\"deviceType\":\"" + device.deviceType
                + "\",\"softwareVersion\":\"" + device.softwareVersion + "\",\"volumeLevel\":" + volume + "}";
        makeRequest("PUT", url, command, true, true, Map.of(), 0);
    }

    public void ascendingAlarm(Device device, boolean ascendingAlarm) throws ConnectionException {
        String url = alexaServer + "/api/ascending-alarm/" + device.serialNumber;
        String command = "{\"ascendingAlarmEnabled\":" + (ascendingAlarm ? "true" : "false")
                + ",\"deviceSerialNumber\":\"" + device.serialNumber + "\",\"deviceType\":\"" + device.deviceType
                + "\",\"deviceAccountId\":null}";
        makeRequest("PUT", url, command, true, true, Map.of(), 0);
    }

    public void doNotDisturb(Device device, boolean doNotDisturb) throws ConnectionException {
        String url = alexaServer + "/api/dnd/status";
        String command = "{\"enabled\":" + (doNotDisturb ? "true" : "false") + ",\"deviceSerialNumber\":\""
                + device.serialNumber + "\",\"deviceType\":\"" + device.deviceType + "\",\"deviceAccountId\":null}";
        makeRequest("PUT", url, command, true, true, Map.of(), 0);
    }

    public List<DeviceNotificationState> getDeviceNotificationStates() {
        try {
            String json = makeRequestAndReturnString(alexaServer + "/api/device-notification-state");
            JsonDeviceNotificationState result = parseJson(json, JsonDeviceNotificationState.class);
            if (result == null) {
                return List.of();
            }
            return Objects.requireNonNullElse(result.deviceNotificationStates, List.of());
        } catch (ConnectionException e) {
            logger.info("Error getting device notification states", e);
        }
        return List.of();
    }

    public List<AscendingAlarmModel> getAscendingAlarm() {
        try {
            String json = makeRequestAndReturnString(alexaServer + "/api/ascending-alarm");
            JsonAscendingAlarm result = parseJson(json, JsonAscendingAlarm.class);
            if (result == null) {
                return List.of();
            }
            return Objects.requireNonNullElse(result.ascendingAlarmModelList, List.of());
        } catch (ConnectionException e) {
            logger.info("Error getting ascending alarm states", e);
        }
        return List.of();
    }

    public List<DoNotDisturbDeviceStatus> getDoNotDisturb() {
        try {
            String json = makeRequestAndReturnString(alexaServer + "/api/dnd/device-status-list");
            JsonDoNotDisturb result = parseJson(json, JsonDoNotDisturb.class);
            if (result == null) {
                return List.of();
            }
            return Objects.requireNonNullElse(result.doNotDisturbDeviceStatusList, List.of());
        } catch (ConnectionException e) {
            logger.info("Error getting do not disturb status list", e);
        }
        return List.of();
    }

    public void bluetooth(Device device, @Nullable String address) throws ConnectionException {
        if (address == null || address.isEmpty()) {
            // disconnect
            makeRequest("POST",
                    alexaServer + "/api/bluetooth/disconnect-sink/" + device.deviceType + "/" + device.serialNumber, "",
                    true, true, Map.of(), 0);
        } else {
            makeRequest("POST",
                    alexaServer + "/api/bluetooth/pair-sink/" + device.deviceType + "/" + device.serialNumber,
                    "{\"bluetoothDeviceAddress\":\"" + address + "\"}", true, true, Map.of(), 0);
        }
    }

    private @Nullable String getCustomerId(@Nullable String defaultId) {
        String accountCustomerId = this.loginData.accountCustomerId;
        return accountCustomerId == null || accountCustomerId.isEmpty() ? defaultId : accountCustomerId;
    }

    public void playRadio(Device device, @Nullable String stationId) throws ConnectionException {
        if (stationId == null || stationId.isEmpty()) {
            command(device, "{\"type\":\"PauseCommand\"}");
        } else {
            String content = "[\"music/tuneIn/stationId\",\"" + stationId + "\"]|{\"previousPageId\":null}";
            String contentToken = new String(
                    Base64.getEncoder().encode(Base64.getEncoder().encode(content.getBytes(StandardCharsets.UTF_8))));
            String body = "{\"contentToken\":\"music:" + contentToken + "\"}";
            makeRequest("PUT", alexaServer + "/api/entertainment/v1/player/queue?deviceSerialNumber="
                    + device.serialNumber + "&deviceType=" + device.deviceType, body, true, true, Map.of(), 0);
        }
    }

    public void playAmazonMusicTrack(Device device, @Nullable String trackId) throws ConnectionException {
        if (trackId == null || trackId.isEmpty()) {
            command(device, "{\"type\":\"PauseCommand\"}");
        } else {
            String command = "{\"trackId\":\"" + trackId + "\",\"playQueuePrime\":true}";
            makeRequest("POST",
                    alexaServer + "/api/cloudplayer/queue-and-play?deviceSerialNumber=" + device.serialNumber
                            + "&deviceType=" + device.deviceType + "&mediaOwnerCustomerId="
                            + getCustomerId(device.deviceOwnerCustomerId) + "&shuffle=false",
                    command, true, true, Map.of(), 0);
        }
    }

    public void playAmazonMusicPlayList(Device device, @Nullable String playListId) throws ConnectionException {
        if (playListId == null || playListId.isEmpty()) {
            command(device, "{\"type\":\"PauseCommand\"}");
        } else {
            String command = "{\"playlistId\":\"" + playListId + "\",\"playQueuePrime\":true}";
            makeRequest("POST",
                    alexaServer + "/api/cloudplayer/queue-and-play?deviceSerialNumber=" + device.serialNumber
                            + "&deviceType=" + device.deviceType + "&mediaOwnerCustomerId="
                            + getCustomerId(device.deviceOwnerCustomerId) + "&shuffle=false",
                    command, true, true, Map.of(), 0);
        }
    }

    public void announcement(Device device, String speak, String bodyText, @Nullable String title,
            @Nullable Integer ttsVolume, @Nullable Integer standardVolume) {
        String trimmedSpeak = speak.replaceAll("\\s+", " ").trim();
        String trimmedBodyText = bodyText.replaceAll("\\s+", " ").trim();
        String plainSpeak = trimmedSpeak.replaceAll("<.+?>", "").trim();
        String plainBodyText = trimmedBodyText.replaceAll("<.+?>", "").trim();
        if (plainSpeak.isEmpty() && plainBodyText.isEmpty()) {
            return;
        }
        String escapedSpeak = trimmedSpeak.replace(plainSpeak,
                XmlEscape.escapeXml10(plainSpeak, XmlEscapeType.CHARACTER_ENTITY_REFERENCES_DEFAULT_TO_HEXA,
                        XmlEscapeLevel.LEVEL_1_ONLY_MARKUP_SIGNIFICANT));
        // we lock announcements until we have finished adding this one
        Lock lock = Objects.requireNonNull(locks.computeIfAbsent(TimerType.ANNOUNCEMENT, k -> new ReentrantLock()));
        lock.lock();
        try {
            AnnouncementWrapper announcement = Objects
                    .requireNonNull(announcements.computeIfAbsent(Objects.hash(escapedSpeak, plainBodyText, title),
                            k -> new AnnouncementWrapper(escapedSpeak, plainBodyText, title)));
            announcement.add(device, ttsVolume, standardVolume);

            // schedule an announcement only if it has not been scheduled before
            timers.computeIfAbsent(TimerType.ANNOUNCEMENT,
                    k -> scheduler.schedule(this::sendAnnouncement, 500, TimeUnit.MILLISECONDS));
        } finally {
            lock.unlock();
        }
    }

    private void sendAnnouncement() {
        // we lock new announcements until we have dispatched everything
        Lock lock = Objects.requireNonNull(locks.computeIfAbsent(TimerType.ANNOUNCEMENT, k -> new ReentrantLock()));
        lock.lock();
        try {
            Iterator<AnnouncementWrapper> iterator = announcements.values().iterator();
            while (iterator.hasNext()) {
                AnnouncementWrapper announcement = iterator.next();
                try {
                    List<Device> devices = announcement.getDevices();
                    if (!devices.isEmpty()) {
                        JsonAnnouncementContent content = new JsonAnnouncementContent(announcement);

                        Map<String, Object> parameters = new HashMap<>();
                        parameters.put("expireAfter", "PT5S");
                        parameters.put("content", new JsonAnnouncementContent[] { content });
                        parameters.put("target", new JsonAnnouncementTarget(devices));

                        String customerId = getCustomerId(devices.get(0).deviceOwnerCustomerId);
                        if (customerId != null) {
                            parameters.put("customerId", customerId);
                        }
                        executeSequenceCommandWithVolume(devices, "AlexaAnnouncement", parameters,
                                announcement.getTtsVolumes(), announcement.getStandardVolumes());
                    }
                } catch (Exception e) {
                    logger.warn("send announcement fails with unexpected error", e);
                }
                iterator.remove();
            }
        } finally {
            // the timer is done anyway immediately after we unlock
            timers.remove(TimerType.ANNOUNCEMENT);
            lock.unlock();
        }
    }

    public void textToSpeech(Device device, String text, @Nullable Integer ttsVolume,
            @Nullable Integer standardVolume) {
        String trimmedText = text.replaceAll("\\s+", " ").trim();
        String plainText = trimmedText.replaceAll("<.+?>", "").trim();
        if (plainText.isEmpty()) {
            return;
        }
        String escapedText = trimmedText.replace(plainText,
                XmlEscape.escapeXml10(plainText, XmlEscapeType.CHARACTER_ENTITY_REFERENCES_DEFAULT_TO_HEXA,
                        XmlEscapeLevel.LEVEL_1_ONLY_MARKUP_SIGNIFICANT));
        // we lock TTS until we have finished adding this one
        Lock lock = Objects.requireNonNull(locks.computeIfAbsent(TimerType.TTS, k -> new ReentrantLock()));
        lock.lock();
        try {
            TextWrapper textToSpeech = Objects.requireNonNull(
                    textToSpeeches.computeIfAbsent(Objects.hash(escapedText), k -> new TextWrapper(escapedText)));
            textToSpeech.add(device, ttsVolume, standardVolume);
            // schedule a TTS only if it has not been scheduled before
            timers.computeIfAbsent(TimerType.TTS,
                    k -> scheduler.schedule(this::sendTextToSpeech, 500, TimeUnit.MILLISECONDS));
        } finally {
            lock.unlock();
        }
    }

    private void sendTextToSpeech() {
        // we lock new TTS until we have dispatched everything
        Lock lock = Objects.requireNonNull(locks.computeIfAbsent(TimerType.TTS, k -> new ReentrantLock()));
        lock.lock();
        try {
            Iterator<TextWrapper> iterator = textToSpeeches.values().iterator();
            while (iterator.hasNext()) {
                TextWrapper textToSpeech = iterator.next();
                try {
                    List<Device> devices = textToSpeech.getDevices();
                    if (!devices.isEmpty()) {
                        executeSequenceCommandWithVolume(devices, "Alexa.Speak",
                                Map.of("textToSpeak", textToSpeech.getText()), textToSpeech.getTtsVolumes(),
                                textToSpeech.getStandardVolumes());
                    }
                } catch (Exception e) {
                    logger.warn("send textToSpeech fails with unexpected error", e);
                }
                iterator.remove();
            }
        } finally {
            // the timer is done anyway immediately after we unlock
            timers.remove(TimerType.TTS);
            lock.unlock();
        }
    }

    public void textCommand(Device device, String text, @Nullable Integer ttsVolume, @Nullable Integer standardVolume) {
        String trimmedText = text.replaceAll("\\s+", " ").trim();
        String plainText = trimmedText.replaceAll("<.+?>", "").trim();
        if (plainText.isEmpty()) {
            return;
        }
        String escapedText = trimmedText.replace(plainText, JsonEscape.escapeJson(plainText,
                JsonEscapeType.SINGLE_ESCAPE_CHARS_DEFAULT_TO_UHEXA, JsonEscapeLevel.LEVEL_1_BASIC_ESCAPE_SET));

        // we lock TextCommands until we have finished adding this one
        Lock lock = Objects.requireNonNull(locks.computeIfAbsent(TimerType.TEXT_COMMAND, k -> new ReentrantLock()));
        lock.lock();
        try {
            TextWrapper textWrapper = Objects.requireNonNull(
                    textCommands.computeIfAbsent(Objects.hash(escapedText), k -> new TextWrapper(escapedText)));
            textWrapper.add(device, ttsVolume, standardVolume);
            // schedule a TextCommand only if it has not been scheduled before
            timers.computeIfAbsent(TimerType.TEXT_COMMAND,
                    k -> scheduler.schedule(this::sendTextCommand, 500, TimeUnit.MILLISECONDS));
        } finally {
            lock.unlock();
        }
    }

    private synchronized void sendTextCommand() {
        // we lock new TTS until we have dispatched everything
        Lock lock = Objects.requireNonNull(locks.computeIfAbsent(TimerType.TEXT_COMMAND, k -> new ReentrantLock()));
        lock.lock();

        try {
            Iterator<TextWrapper> iterator = textCommands.values().iterator();
            while (iterator.hasNext()) {
                TextWrapper textCommand = iterator.next();
                try {
                    List<Device> devices = textCommand.getDevices();
                    if (!devices.isEmpty()) {
                        executeSequenceCommandWithVolume(devices, "Alexa.TextCommand",
                                Map.of("text", textCommand.getText()), textCommand.getTtsVolumes(),
                                textCommand.getStandardVolumes());
                    }
                } catch (Exception e) {
                    logger.warn("send textCommand fails with unexpected error", e);
                }
                iterator.remove();
            }
        } finally {
            // the timer is done anyway immediately after we unlock
            timers.remove(TimerType.TEXT_COMMAND);
            lock.unlock();
        }
    }

    public void volume(Device device, int vol) {
        // we lock volume until we have finished adding this one
        Lock lock = Objects.requireNonNull(locks.computeIfAbsent(TimerType.VOLUME, k -> new ReentrantLock()));
        lock.lock();
        try {
            Volume volume = Objects.requireNonNull(volumes.computeIfAbsent(vol, k -> new Volume()));
            volume.devices.add(device);
            volume.volumes.add(vol);
            // schedule a TTS only if it has not been scheduled before
            timers.computeIfAbsent(TimerType.VOLUME,
                    k -> scheduler.schedule(this::sendVolume, 500, TimeUnit.MILLISECONDS));
        } finally {
            lock.unlock();
        }
    }

    private void sendVolume() {
        // we lock new volume until we have dispatched everything
        Lock lock = Objects.requireNonNull(locks.computeIfAbsent(TimerType.VOLUME, k -> new ReentrantLock()));
        lock.lock();
        try {
            Iterator<Volume> iterator = volumes.values().iterator();
            while (iterator.hasNext()) {
                Volume volume = iterator.next();
                try {
                    List<Device> devices = volume.devices;
                    if (!devices.isEmpty()) {
                        executeSequenceCommandWithVolume(devices, null, Map.of(), volume.volumes, List.of());
                    }
                } catch (Exception e) {
                    logger.warn("send volume fails with unexpected error", e);
                }
                iterator.remove();
            }
        } finally {
            // the timer is done anyway immediately after we unlock
            timers.remove(TimerType.VOLUME);
            lock.unlock();
        }
    }

    private void executeSequenceCommandWithVolume(List<Device> devices, @Nullable String command,
            Map<String, Object> parameters, List<@Nullable Integer> ttsVolumes,
            List<@Nullable Integer> standardVolumes) {
        JsonArray serialNodesToExecute = new JsonArray();

        JsonArray ttsVolumeNodesToExecute = new JsonArray();
        for (int i = 0; i < devices.size(); i++) {
            Integer ttsVolume = ttsVolumes.size() > i ? ttsVolumes.get(i) : null;
            Integer standardVolume = standardVolumes.size() > i ? standardVolumes.get(i) : null;
            if (ttsVolume != null && !ttsVolume.equals(standardVolume)) {
                ttsVolumeNodesToExecute.add(
                        createExecutionNode(devices.get(i), "Alexa.DeviceControls.Volume", Map.of("value", ttsVolume)));
            }
        }
        if (ttsVolumeNodesToExecute.size() > 0) {
            JsonObject parallelNodesToExecute = new JsonObject();
            parallelNodesToExecute.addProperty("@type", "com.amazon.alexa.behaviors.model.ParallelNode");
            parallelNodesToExecute.add("nodesToExecute", ttsVolumeNodesToExecute);
            serialNodesToExecute.add(parallelNodesToExecute);
        }

        if (command != null && !parameters.isEmpty()) {
            JsonArray commandNodesToExecute = new JsonArray();
            if ("Alexa.Speak".equals(command) || "Alexa.TextCommand".equals(command)) {
                for (Device device : devices) {
                    commandNodesToExecute.add(createExecutionNode(device, command, parameters));
                }
            } else {
                commandNodesToExecute.add(createExecutionNode(devices.get(0), command, parameters));
            }
            if (commandNodesToExecute.size() > 0) {
                JsonObject parallelNodesToExecute = new JsonObject();
                parallelNodesToExecute.addProperty("@type", "com.amazon.alexa.behaviors.model.ParallelNode");
                parallelNodesToExecute.add("nodesToExecute", commandNodesToExecute);
                serialNodesToExecute.add(parallelNodesToExecute);
            }
        }

        JsonArray standardVolumeNodesToExecute = new JsonArray();
        for (int i = 0; i < devices.size(); i++) {
            Integer standardVolume = standardVolumes.size() > i ? standardVolumes.get(i) : null;
            Integer ttsVolume = ttsVolumes.size() > i ? ttsVolumes.get(i) : null;
            if (ttsVolume != null && standardVolume != null && !standardVolume.equals(ttsVolume)) {
                standardVolumeNodesToExecute.add(createExecutionNode(devices.get(i), "Alexa.DeviceControls.Volume",
                        Map.of("value", standardVolume)));
            }
        }
        if (standardVolumeNodesToExecute.size() > 0 && !"AlexaAnnouncement".equals(command)) {
            JsonObject parallelNodesToExecute = new JsonObject();
            parallelNodesToExecute.addProperty("@type", "com.amazon.alexa.behaviors.model.ParallelNode");
            parallelNodesToExecute.add("nodesToExecute", standardVolumeNodesToExecute);
            serialNodesToExecute.add(parallelNodesToExecute);
        }

        if (serialNodesToExecute.size() > 0) {
            executeSequenceNodes(devices, serialNodesToExecute, false);

            if (standardVolumeNodesToExecute.size() > 0 && "AlexaAnnouncement".equals(command)) {
                executeSequenceNodes(devices, standardVolumeNodesToExecute, true);
            }
        }
    }

    // commands: Alexa.Weather.Play, Alexa.Traffic.Play, Alexa.FlashBriefing.Play,
    // Alexa.GoodMorning.Play,
    // Alexa.SingASong.Play, Alexa.TellStory.Play, Alexa.Speak (textToSpeach)
    public void executeSequenceCommand(Device device, String command, Map<String, Object> parameters) {
        JsonObject nodeToExecute = createExecutionNode(device, command, parameters);
        executeSequenceNode(List.of(device), nodeToExecute);
    }

    private void executeSequenceNode(List<Device> devices, JsonObject nodeToExecute) {
        QueueObject queueObject = new QueueObject();
        queueObject.devices = devices;
        queueObject.nodeToExecute = nodeToExecute;
        String serialNumbers = "";
        for (Device device : devices) {
            String serialNumber = device.serialNumber;
            if (serialNumber != null) {
                Objects.requireNonNull(this.devices.computeIfAbsent(serialNumber, k -> new LinkedBlockingQueue<>()))
                        .offer(queueObject);
                serialNumbers = serialNumbers + device.serialNumber + " ";
            }
        }
        logger.debug("added {} device {}", queueObject.hashCode(), serialNumbers);
    }

    private void handleExecuteSequenceNode() {
        Lock lock = Objects.requireNonNull(locks.computeIfAbsent(TimerType.DEVICES, k -> new ReentrantLock()));
        if (lock.tryLock()) {
            try {
                for (String serialNumber : devices.keySet()) {
                    LinkedBlockingQueue<QueueObject> queueObjects = devices.get(serialNumber);
                    if (queueObjects != null) {
                        QueueObject queueObject = queueObjects.peek();
                        if (queueObject != null) {
                            Future<?> future = queueObject.future;
                            if (future == null || future.isDone()) {
                                boolean execute = true;
                                String serial = "";
                                for (Device tmpDevice : queueObject.devices) {
                                    if (!serialNumber.equals(tmpDevice.serialNumber)) {
                                        LinkedBlockingQueue<QueueObject> tmpQueueObjects = devices
                                                .get(tmpDevice.serialNumber);
                                        if (tmpQueueObjects != null) {
                                            QueueObject tmpQueueObject = tmpQueueObjects.peek();
                                            Future<?> tmpFuture = null;
                                            if (tmpQueueObject != null) {
                                                tmpFuture = tmpQueueObject.future;
                                            }
                                            if (!queueObject.equals(tmpQueueObject)
                                                    || (tmpFuture != null && !tmpFuture.isDone())) {
                                                execute = false;
                                                break;
                                            }

                                            serial = serial + tmpDevice.serialNumber + " ";
                                        }
                                    }
                                }
                                if (execute) {
                                    queueObject.future = scheduler.submit(() -> queuedExecuteSequenceNode(queueObject));
                                    logger.debug("thread {} device {}", queueObject.hashCode(), serial);
                                }
                            }
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private void queuedExecuteSequenceNode(QueueObject queueObject) {
        JsonObject nodeToExecute = queueObject.nodeToExecute;
        ExecutionNodeObject executionNodeObject = getExecutionNodeObject(nodeToExecute);
        if (executionNodeObject == null) {
            logger.debug("executionNodeObject empty, removing without execution");
            removeObjectFromQueueAfterExecutionCompletion(queueObject);
            return;
        }
        List<String> types = executionNodeObject.types;
        long delay = types.contains("Alexa.DeviceControls.Volume") ? 2000 : 0;
        delay += types.contains("Announcement") ? 3000 : 2000;

        try {
            JsonObject sequenceJson = new JsonObject();
            sequenceJson.addProperty("@type", "com.amazon.alexa.behaviors.model.Sequence");
            sequenceJson.add("startNode", nodeToExecute);

            JsonStartRoutineRequest request = new JsonStartRoutineRequest();
            request.sequenceJson = gson.toJson(sequenceJson);
            String json = gson.toJson(request);

            String text = executionNodeObject.text;
            if (text != null) {
                text = text.replaceAll("<.+?>", " ").replaceAll("\\s+", " ").trim();
                delay += text.length() * 150;
            }

            makeRequest("POST", alexaServer + "/api/behaviors/preview", json, true, true, Map.of(), 3);

            Thread.sleep(delay);
        } catch (ConnectionException | InterruptedException e) {
            logger.warn("execute sequence node fails with unexpected error", e);
        } finally {
            removeObjectFromQueueAfterExecutionCompletion(queueObject);
        }
    }

    private void removeObjectFromQueueAfterExecutionCompletion(QueueObject queueObject) {
        String serial = "";
        for (Device device : queueObject.devices) {
            String serialNumber = device.serialNumber;
            if (serialNumber != null) {
                LinkedBlockingQueue<?> queue = devices.get(serialNumber);
                if (queue != null) {
                    queue.remove(queueObject);
                }
                serial = serial + serialNumber + " ";
            }
        }
        logger.debug("removed {} device {}", queueObject.hashCode(), serial);
    }

    private void executeSequenceNodes(List<Device> devices, JsonArray nodesToExecute, boolean parallel) {
        JsonObject serialNode = new JsonObject();
        if (parallel) {
            serialNode.addProperty("@type", "com.amazon.alexa.behaviors.model.ParallelNode");
        } else {
            serialNode.addProperty("@type", "com.amazon.alexa.behaviors.model.SerialNode");
        }

        serialNode.add("nodesToExecute", nodesToExecute);

        executeSequenceNode(devices, serialNode);
    }

    private JsonObject createExecutionNode(@Nullable Device device, String command, Map<String, Object> parameters) {
        JsonObject operationPayload = new JsonObject();
        if (device != null) {
            operationPayload.addProperty("deviceType", device.deviceType);
            operationPayload.addProperty("deviceSerialNumber", device.serialNumber);
            operationPayload.addProperty("locale", "");
            operationPayload.addProperty("customerId", getCustomerId(device.deviceOwnerCustomerId));
        }
        for (String key : parameters.keySet()) {
            Object value = parameters.get(key);
            if (value instanceof String) {
                operationPayload.addProperty(key, (String) value);
            } else if (value instanceof Number) {
                operationPayload.addProperty(key, (Number) value);
            } else if (value instanceof Boolean) {
                operationPayload.addProperty(key, (Boolean) value);
            } else if (value instanceof Character) {
                operationPayload.addProperty(key, (Character) value);
            } else {
                operationPayload.add(key, gson.toJsonTree(value));
            }
        }

        JsonObject nodeToExecute = new JsonObject();
        nodeToExecute.addProperty("@type", "com.amazon.alexa.behaviors.model.OpaquePayloadOperationNode");
        nodeToExecute.addProperty("type", command);
        if ("Alexa.TextCommand".equals(command)) {
            nodeToExecute.addProperty("skillId", "amzn1.ask.1p.tellalexa");
        }
        nodeToExecute.add("operationPayload", operationPayload);
        return nodeToExecute;
    }

    @Nullable
    private ExecutionNodeObject getExecutionNodeObject(JsonObject nodeToExecute) {
        ExecutionNodeObject executionNodeObject = new ExecutionNodeObject();
        if (nodeToExecute.has("nodesToExecute")) {
            JsonArray serialNodesToExecute = nodeToExecute.getAsJsonArray("nodesToExecute");
            if (serialNodesToExecute != null && serialNodesToExecute.size() > 0) {
                for (int i = 0; i < serialNodesToExecute.size(); i++) {
                    JsonObject serialNodesToExecuteJsonObject = serialNodesToExecute.get(i).getAsJsonObject();
                    if (serialNodesToExecuteJsonObject.has("nodesToExecute")) {
                        JsonArray parallelNodesToExecute = serialNodesToExecuteJsonObject
                                .getAsJsonArray("nodesToExecute");
                        if (parallelNodesToExecute != null && parallelNodesToExecute.size() > 0) {
                            JsonObject parallelNodesToExecuteJsonObject = parallelNodesToExecute.get(0)
                                    .getAsJsonObject();
                            if (processNodesToExecuteJsonObject(executionNodeObject,
                                    parallelNodesToExecuteJsonObject)) {
                                break;
                            }
                        }
                    } else {
                        if (processNodesToExecuteJsonObject(executionNodeObject, serialNodesToExecuteJsonObject)) {
                            break;
                        }
                    }
                }
            }
        }

        return executionNodeObject;
    }

    private boolean processNodesToExecuteJsonObject(ExecutionNodeObject executionNodeObject,
            JsonObject nodesToExecuteJsonObject) {
        if (nodesToExecuteJsonObject.has("type")) {
            executionNodeObject.types.add(nodesToExecuteJsonObject.get("type").getAsString());
            if (nodesToExecuteJsonObject.has("operationPayload")) {
                JsonObject operationPayload = nodesToExecuteJsonObject.getAsJsonObject("operationPayload");
                if (operationPayload != null) {
                    if (operationPayload.has("textToSpeak")) {
                        executionNodeObject.text = operationPayload.get("textToSpeak").getAsString();
                        return true;
                    } else if (operationPayload.has("text")) {
                        executionNodeObject.text = operationPayload.get("text").getAsString();
                        return true;
                    } else if (operationPayload.has("content")) {
                        JsonArray content = operationPayload.getAsJsonArray("content");
                        if (content != null && content.size() > 0) {
                            JsonObject contentJsonObject = content.get(0).getAsJsonObject();
                            if (contentJsonObject.has("speak")) {
                                JsonObject speak = contentJsonObject.getAsJsonObject("speak");
                                if (speak != null && speak.has("value")) {
                                    executionNodeObject.text = speak.get("value").getAsString();
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public void startRoutine(Device device, String utterance) throws ConnectionException {
        JsonAutomation found = null;
        String deviceLocale = "";
        List<JsonAutomation> routines = getRoutines();

        for (JsonAutomation routine : routines) {
            if (routine.sequence != null) {
                List<JsonAutomation.Trigger> triggers = Objects.requireNonNullElse(routine.triggers, List.of());
                for (JsonAutomation.Trigger trigger : triggers) {
                    Payload payload = trigger.payload;
                    if (payload == null) {
                        continue;
                    }
                    String payloadUtterance = payload.utterance;
                    if (payloadUtterance != null && payloadUtterance.equalsIgnoreCase(utterance)) {
                        found = routine;
                        deviceLocale = payload.locale;
                        break;
                    }
                }
            }
        }
        if (found != null) {
            String sequenceJson = gson.toJson(found.sequence);

            JsonStartRoutineRequest request = new JsonStartRoutineRequest();
            request.behaviorId = found.automationId;

            // replace tokens
            // "deviceType":"ALEXA_CURRENT_DEVICE_TYPE"
            String deviceType = "\"deviceType\":\"ALEXA_CURRENT_DEVICE_TYPE\"";
            String newDeviceType = "\"deviceType\":\"" + device.deviceType + "\"";
            sequenceJson = sequenceJson.replace(deviceType, newDeviceType);

            // "deviceSerialNumber":"ALEXA_CURRENT_DSN"
            String deviceSerial = "\"deviceSerialNumber\":\"ALEXA_CURRENT_DSN\"";
            String newDeviceSerial = "\"deviceSerialNumber\":\"" + device.serialNumber + "\"";
            sequenceJson = sequenceJson.replace(deviceSerial, newDeviceSerial);

            // "customerId": "ALEXA_CUSTOMER_ID"
            String customerId = "\"customerId\":\"ALEXA_CUSTOMER_ID\"";
            String newCustomerId = "\"customerId\":\"" + getCustomerId(device.deviceOwnerCustomerId) + "\"";
            sequenceJson = sequenceJson.replace(customerId, newCustomerId);

            // "locale": "ALEXA_CURRENT_LOCALE"
            String locale = "\"locale\":\"ALEXA_CURRENT_LOCALE\"";
            String newlocale = deviceLocale != null && !deviceLocale.isEmpty() ? "\"locale\":\"" + deviceLocale + "\""
                    : "\"locale\":null";
            sequenceJson = sequenceJson.replace(locale, newlocale);

            request.sequenceJson = sequenceJson;

            String requestJson = gson.toJson(request);
            makeRequest("POST", alexaServer + "/api/behaviors/preview", requestJson, true, true, Map.of(), 3);
        } else {
            logger.warn("Routine {} not found", utterance);
        }
    }

    public List<JsonAutomation> getRoutines() throws ConnectionException {
        String json = makeRequestAndReturnString(alexaServer + "/api/behaviors/v2/automations?limit=2000");
        JsonAutomation[] result = parseJson(json, JsonAutomation[].class);
        if (result == null) {
            return List.of();
        }
        return Arrays.asList(result);
    }

    public List<JsonFeed> getEnabledFlashBriefings() throws ConnectionException {
        String json = makeRequestAndReturnString(alexaServer + "/api/content-skills/enabled-feeds");
        JsonEnabledFeeds result = parseJson(json, JsonEnabledFeeds.class);
        if (result == null) {
            return List.of();
        }
        return Objects.requireNonNullElse(result.enabledFeeds, List.of());
    }

    public void setEnabledFlashBriefings(List<JsonFeed> enabledFlashBriefing) throws ConnectionException {
        JsonEnabledFeeds enabled = new JsonEnabledFeeds();
        enabled.enabledFeeds = enabledFlashBriefing;
        String json = gsonWithNullSerialization.toJson(enabled);
        makeRequest("POST", alexaServer + "/api/content-skills/enabled-feeds", json, true, true, Map.of(), 0);
    }

    public List<JsonNotificationSound> getNotificationSounds(Device device) throws ConnectionException {
        String json = makeRequestAndReturnString(
                alexaServer + "/api/notification/sounds?deviceSerialNumber=" + device.serialNumber + "&deviceType="
                        + device.deviceType + "&softwareVersion=" + device.softwareVersion);
        JsonNotificationSounds result = parseJson(json, JsonNotificationSounds.class);
        if (result == null) {
            return List.of();
        }
        return Objects.requireNonNullElse(result.notificationSounds, List.of());
    }

    public List<JsonNotificationResponse> notifications() throws ConnectionException {
        String response = makeRequestAndReturnString(alexaServer + "/api/notifications");
        JsonNotificationsResponse result = parseJson(response, JsonNotificationsResponse.class);
        if (result == null) {
            return List.of();
        }
        return Objects.requireNonNullElse(result.notifications, List.of());
    }

    public @Nullable JsonNotificationResponse notification(Device device, String type, @Nullable String label,
            @Nullable JsonNotificationSound sound) throws ConnectionException {
        Date date = new Date(new Date().getTime());
        long createdDate = date.getTime();
        Date alarm = new Date(createdDate + 5000); // add 5 seconds, because amazon does not accept calls for times in
        // the past (compared with the server time)
        long alarmTime = alarm.getTime();

        JsonNotificationRequest request = new JsonNotificationRequest();
        request.type = type;
        request.deviceSerialNumber = device.serialNumber;
        request.deviceType = device.deviceType;
        request.createdDate = createdDate;
        request.alarmTime = alarmTime;
        request.reminderLabel = label;
        request.sound = sound;
        request.originalDate = new SimpleDateFormat("yyyy-MM-dd").format(alarm);
        request.originalTime = new SimpleDateFormat("HH:mm:ss.SSSS").format(alarm);
        request.type = type;
        request.id = "create" + type;

        String data = gsonWithNullSerialization.toJson(request);
        String response = makeRequestAndReturnString("PUT", alexaServer + "/api/notifications/createReminder", data,
                true, Map.of());
        JsonNotificationResponse result = parseJson(response, JsonNotificationResponse.class);
        return result;
    }

    public void stopNotification(JsonNotificationResponse notification) throws ConnectionException {
        makeRequestAndReturnString("DELETE", alexaServer + "/api/notifications/" + notification.id, null, true,
                Map.of());
    }

    public @Nullable JsonNotificationResponse getNotificationState(JsonNotificationResponse notification)
            throws ConnectionException {
        String response = makeRequestAndReturnString(alexaServer + "/api/notifications/" + notification.id);
        JsonNotificationResponse result = parseJson(response, JsonNotificationResponse.class);
        return result;
    }

    public List<JsonMusicProvider> getMusicProviders() {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Routines-Version", "1.1.218665");
            String response = makeRequestAndReturnString("GET",
                    alexaServer + "/api/behaviors/entities?skillId=amzn1.ask.1p.music", null, true, headers);
            if (!response.isEmpty()) {
                JsonMusicProvider[] musicProviders = parseJson(response, JsonMusicProvider[].class);
                return Arrays.asList(musicProviders);
            }
        } catch (ConnectionException e) {
            logger.warn("getMusicProviders fails: {}", e.getMessage());
        }
        return List.of();
    }

    public void playMusicVoiceCommand(Device device, String providerId, String voiceCommand)
            throws ConnectionException {
        JsonPlaySearchPhraseOperationPayload payload = new JsonPlaySearchPhraseOperationPayload();
        payload.customerId = getCustomerId(device.deviceOwnerCustomerId);
        payload.locale = "ALEXA_CURRENT_LOCALE";
        payload.musicProviderId = providerId;
        payload.searchPhrase = voiceCommand;

        String playloadString = gson.toJson(payload);

        JsonObject postValidationJson = new JsonObject();

        postValidationJson.addProperty("type", "Alexa.Music.PlaySearchPhrase");
        postValidationJson.addProperty("operationPayload", playloadString);

        String postDataValidate = postValidationJson.toString();

        String validateResultJson = makeRequestAndReturnString("POST",
                alexaServer + "/api/behaviors/operation/validate", postDataValidate, true, Map.of());

        if (!validateResultJson.isEmpty()) {
            JsonPlayValidationResult validationResult = parseJson(validateResultJson, JsonPlayValidationResult.class);
            if (validationResult != null) {
                JsonPlaySearchPhraseOperationPayload validatedOperationPayload = validationResult.operationPayload;
                if (validatedOperationPayload != null) {
                    payload.sanitizedSearchPhrase = validatedOperationPayload.sanitizedSearchPhrase;
                    payload.searchPhrase = validatedOperationPayload.searchPhrase;
                }
            }
        }

        payload.locale = null;
        payload.deviceSerialNumber = device.serialNumber;
        payload.deviceType = device.deviceType;

        JsonObject sequenceJson = new JsonObject();
        sequenceJson.addProperty("@type", "com.amazon.alexa.behaviors.model.Sequence");
        JsonObject startNodeJson = new JsonObject();
        startNodeJson.addProperty("@type", "com.amazon.alexa.behaviors.model.OpaquePayloadOperationNode");
        startNodeJson.addProperty("type", "Alexa.Music.PlaySearchPhrase");
        startNodeJson.add("operationPayload", gson.toJsonTree(payload));
        sequenceJson.add("startNode", startNodeJson);

        JsonStartRoutineRequest startRoutineRequest = new JsonStartRoutineRequest();
        startRoutineRequest.sequenceJson = sequenceJson.toString();
        startRoutineRequest.status = null;

        String postData = gson.toJson(startRoutineRequest);
        makeRequest("POST", alexaServer + "/api/behaviors/preview", postData, true, true, Map.of(), 3);
    }

    public @Nullable JsonEqualizer getEqualizer(Device device) throws ConnectionException {
        String json = makeRequestAndReturnString(
                alexaServer + "/api/equalizer/" + device.serialNumber + "/" + device.deviceType);
        return parseJson(json, JsonEqualizer.class);
    }

    public void setEqualizer(Device device, JsonEqualizer settings) throws ConnectionException {
        String postData = gson.toJson(settings);
        makeRequest("POST", alexaServer + "/api/equalizer/" + device.serialNumber + "/" + device.deviceType, postData,
                true, true, Map.of(), 0);
    }

    private static class Volume {
        public List<Device> devices = new ArrayList<>();
        public List<@Nullable Integer> volumes = new ArrayList<>();
    }

    private static class QueueObject {
        public @Nullable Future<?> future;
        public List<Device> devices = List.of();
        public JsonObject nodeToExecute = new JsonObject();
    }

    private static class ExecutionNodeObject {
        public List<String> types = new ArrayList<>();
        public @Nullable String text;
    }
}
