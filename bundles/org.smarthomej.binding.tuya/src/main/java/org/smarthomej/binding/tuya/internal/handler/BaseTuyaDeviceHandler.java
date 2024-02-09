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
package org.smarthomej.binding.tuya.internal.handler;

import static org.smarthomej.binding.tuya.internal.TuyaBindingConstants.CHANNEL_TYPE_UID_COLOR;
import static org.smarthomej.binding.tuya.internal.TuyaBindingConstants.CHANNEL_TYPE_UID_DIMMER;
import static org.smarthomej.binding.tuya.internal.TuyaBindingConstants.CHANNEL_TYPE_UID_NUMBER;
import static org.smarthomej.binding.tuya.internal.TuyaBindingConstants.CHANNEL_TYPE_UID_STRING;
import static org.smarthomej.binding.tuya.internal.TuyaBindingConstants.CHANNEL_TYPE_UID_SWITCH;
import static org.smarthomej.binding.tuya.internal.TuyaBindingConstants.SCHEMAS;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.cache.ExpiringCache;
import org.openhab.core.cache.ExpiringCacheMap;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.HSBType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.CommandOption;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smarthomej.binding.tuya.internal.TuyaBindingConstants;
import org.smarthomej.binding.tuya.internal.config.ChannelConfiguration;
import org.smarthomej.binding.tuya.internal.config.DeviceConfiguration;
import org.smarthomej.binding.tuya.internal.local.DeviceStatusListener;
import org.smarthomej.binding.tuya.internal.local.TuyaDevice;
import org.smarthomej.binding.tuya.internal.local.TuyaDeviceManager;
import org.smarthomej.binding.tuya.internal.util.ConversionUtil;
import org.smarthomej.binding.tuya.internal.util.SchemaDp;
import org.smarthomej.commons.SimpleDynamicCommandDescriptionProvider;

/**
 * The {@link BaseTuyaDeviceHandler} handles commands and state updates for devices with channels
 *
 * @author Vitalii Herhel - Initial contribution
 */
@NonNullByDefault
public class BaseTuyaDeviceHandler extends BaseThingHandler implements DeviceStatusListener {

    private static final List<String> COLOUR_CHANNEL_CODES = List.of("colour_data");
    private static final List<String> DIMMER_CHANNEL_CODES = List.of("bright_value", "bright_value_1", "bright_value_2",
            "temp_value");

    private final Logger logger = LoggerFactory.getLogger(BaseTuyaDeviceHandler.class);

    private final SimpleDynamicCommandDescriptionProvider dynamicCommandDescriptionProvider;
    protected DeviceConfiguration configuration = new DeviceConfiguration();
    protected @Nullable TuyaDeviceManager tuyaDeviceManager;
    private final List<SchemaDp> schemaDps;
    private boolean oldColorMode = false;

    private @Nullable ScheduledFuture<?> pollingJob;

    private final Map<Integer, String> dpToChannelId = new HashMap<>();
    private final Map<Integer, List<String>> dp2ToChannelId = new HashMap<>();
    private final Map<String, ChannelTypeUID> channelIdToChannelTypeUID = new HashMap<>();
    private final Map<String, ChannelConfiguration> channelIdToConfiguration = new HashMap<>();

    private final ExpiringCacheMap<Integer, @Nullable Object> deviceStatusCache = new ExpiringCacheMap<>(
            Duration.ofSeconds(10));
    private final Map<String, State> channelStateCache = new HashMap<>();

    public BaseTuyaDeviceHandler(Thing thing, @Nullable List<SchemaDp> schemaDps,
            SimpleDynamicCommandDescriptionProvider dynamicCommandDescriptionProvider) {
        super(thing);
        this.dynamicCommandDescriptionProvider = dynamicCommandDescriptionProvider;
        this.schemaDps = Objects.requireNonNullElse(schemaDps, List.of());
    }

    private @Nullable TuyaDevice getTuyaDevice() {
        TuyaDeviceManager tuyaDeviceManager = this.tuyaDeviceManager;
        if (tuyaDeviceManager != null) {
            return tuyaDeviceManager.getTuyaDevice();
        } else {
            return null;
        }
    }

    @Override
    public void processDeviceStatus(@Nullable String cid, Map<Integer, Object> deviceStatus) {
        logger.trace("'{}' received status message '{}'", thing.getUID(), deviceStatus);

        if (deviceStatus.isEmpty()) {
            // if status is empty -> need to use control method to request device status
            Map<Integer, @Nullable Object> commandRequest = new HashMap<>();
            dpToChannelId.keySet().forEach(dp -> commandRequest.put(dp, null));
            dp2ToChannelId.keySet().forEach(dp -> commandRequest.put(dp, null));

            TuyaDevice tuyaDevice = getTuyaDevice();
            if (tuyaDevice != null) {
                tuyaDevice.set(commandRequest);
            }

            return;
        }

        deviceStatus.forEach(this::addSingleExpiringCache);
        deviceStatus.forEach(this::processChannelStatus);
    }

    private void processChannelStatus(Integer dp, Object value) {
        String channelId = dpToChannelId.get(dp);
        if (channelId != null) {

            ChannelConfiguration configuration = channelIdToConfiguration.get(channelId);
            ChannelTypeUID channelTypeUID = channelIdToChannelTypeUID.get(channelId);

            if (configuration == null || channelTypeUID == null) {
                logger.warn("Could not find configuration or type for channel '{}' in thing '{}'", channelId,
                        thing.getUID());
                return;
            }

            ChannelConfiguration channelConfiguration = channelIdToConfiguration.get(channelId);
            if (channelConfiguration != null && Boolean.FALSE.equals(deviceStatusCache.get(channelConfiguration.dp2))) {
                // skip update if the channel is off!
                return;
            }

            if (value instanceof String && CHANNEL_TYPE_UID_COLOR.equals(channelTypeUID)) {
                oldColorMode = ((String) value).length() == 14;
                updateState(channelId, ConversionUtil.hexColorDecode((String) value));
                return;
            } else if (value instanceof String && CHANNEL_TYPE_UID_STRING.equals(channelTypeUID)) {
                updateState(channelId, new StringType((String) value));
                return;
            } else if (Double.class.isAssignableFrom(value.getClass())
                    && CHANNEL_TYPE_UID_DIMMER.equals(channelTypeUID)) {
                updateState(channelId, ConversionUtil.brightnessDecode((double) value, 0, configuration.max));
                return;
            } else if (Double.class.isAssignableFrom(value.getClass())
                    && CHANNEL_TYPE_UID_NUMBER.equals(channelTypeUID)) {
                updateState(channelId, new DecimalType((double) value));
                return;
            } else if (Boolean.class.isAssignableFrom(value.getClass())
                    && CHANNEL_TYPE_UID_SWITCH.equals(channelTypeUID)) {
                updateState(channelId, OnOffType.from((boolean) value));
                return;
            }
            logger.warn("Could not update channel '{}' of thing '{}' with value '{}'. Datatype incompatible.",
                    channelId, getThing().getUID(), value);
        } else {
            // try additional channelDps, only OnOffType
            List<String> channelIds = dp2ToChannelId.get(dp);
            if (channelIds == null) {
                logger.debug("Could not find channel for dp '{}' in thing '{}'", dp, thing.getUID());
            } else {
                if (Boolean.class.isAssignableFrom(value.getClass())) {
                    OnOffType state = OnOffType.from((boolean) value);
                    channelIds.forEach(ch -> updateState(ch, state));
                    return;
                }
                logger.warn("Could not update channel '{}' of thing '{}' with value {}. Datatype incompatible.",
                        channelIds, getThing().getUID(), value);
            }
        }
    }

    @Override
    public void onConnected() {
        updateStatus(ThingStatus.ONLINE);
        int pollingInterval = configuration.pollingInterval;
        TuyaDevice tuyaDevice = getTuyaDevice();
        if (tuyaDevice != null && pollingInterval > 0) {
            pollingJob = scheduler.scheduleWithFixedDelay(tuyaDevice::refreshStatus, pollingInterval, pollingInterval,
                    TimeUnit.SECONDS);
        }
    }

    @Override
    public void onDisconnected(ThingStatusDetail thingStatusDetail, @Nullable String reason) {
        updateStatus(ThingStatus.OFFLINE, thingStatusDetail, reason);
        ScheduledFuture<?> pollingJob = this.pollingJob;
        if (pollingJob != null) {
            pollingJob.cancel(true);
            this.pollingJob = null;
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (getThing().getStatus() != ThingStatus.ONLINE) {
            logger.warn("Channel '{}' received a command but device is not ONLINE. Discarding command.", channelUID);
            return;
        }

        Map<Integer, @Nullable Object> commandRequest = new HashMap<>();

        ChannelTypeUID channelTypeUID = channelIdToChannelTypeUID.get(channelUID.getId());
        ChannelConfiguration configuration = channelIdToConfiguration.get(channelUID.getId());
        if (channelTypeUID == null || configuration == null) {
            logger.warn("Could not determine channel type or configuration for channel '{}'. Discarding command.",
                    channelUID);
            return;
        }

        if (CHANNEL_TYPE_UID_COLOR.equals(channelTypeUID)) {
            if (command instanceof HSBType) {
                commandRequest.put(configuration.dp, ConversionUtil.hexColorEncode((HSBType) command, oldColorMode));
                ChannelConfiguration workModeConfig = channelIdToConfiguration.get("work_mode");
                if (workModeConfig != null) {
                    commandRequest.put(workModeConfig.dp, "colour");
                }
                if (configuration.dp2 != 0) {
                    commandRequest.put(configuration.dp2, ((HSBType) command).getBrightness().doubleValue() > 0.0);
                }
            } else if (command instanceof PercentType) {
                State oldState = channelStateCache.get(channelUID.getId());
                if (!(oldState instanceof HSBType)) {
                    logger.debug("Discarding command '{}' to channel '{}', cannot determine old state", command,
                            channelUID);
                    return;
                }
                HSBType newState = new HSBType(((HSBType) oldState).getHue(), ((HSBType) oldState).getSaturation(),
                        (PercentType) command);
                commandRequest.put(configuration.dp, ConversionUtil.hexColorEncode(newState, oldColorMode));
                ChannelConfiguration workModeConfig = channelIdToConfiguration.get("work_mode");
                if (workModeConfig != null) {
                    commandRequest.put(workModeConfig.dp, "colour");
                }
                if (configuration.dp2 != 0) {
                    commandRequest.put(configuration.dp2, ((PercentType) command).doubleValue() > 0.0);
                }
            } else if (command instanceof OnOffType) {
                if (configuration.dp2 != 0) {
                    commandRequest.put(configuration.dp2, OnOffType.ON.equals(command));
                }
            }
        } else if (CHANNEL_TYPE_UID_DIMMER.equals(channelTypeUID)) {
            if (command instanceof PercentType) {
                int value = ConversionUtil.brightnessEncode((PercentType) command, 0, configuration.max);
                if (value >= configuration.min) {
                    commandRequest.put(configuration.dp, value);
                }
                if (configuration.dp2 != 0) {
                    commandRequest.put(configuration.dp2, value >= configuration.min);
                }
                ChannelConfiguration workModeConfig = channelIdToConfiguration.get("work_mode");
                if (workModeConfig != null) {
                    commandRequest.put(workModeConfig.dp, "white");
                }
            } else if (command instanceof OnOffType) {
                if (configuration.dp2 != 0) {
                    commandRequest.put(configuration.dp2, OnOffType.ON.equals(command));
                }
            }
        } else if (CHANNEL_TYPE_UID_STRING.equals(channelTypeUID)) {
            if (command instanceof StringType) {
                commandRequest.put(configuration.dp, command.toString());
            }
        } else if (CHANNEL_TYPE_UID_NUMBER.equals(channelTypeUID)) {
            if (command instanceof DecimalType) {
                commandRequest.put(configuration.dp, ((DecimalType) command).intValue());
            }
        } else if (CHANNEL_TYPE_UID_SWITCH.equals(channelTypeUID)) {
            if (command instanceof OnOffType) {
                commandRequest.put(configuration.dp, OnOffType.ON.equals(command));
            }
        }

        Optional<String> subDeviceId = Optional
                .ofNullable(thing.getConfiguration().get(TuyaBindingConstants.CONFIG_DEVICE_UUID))
                .map(Objects::toString);

        TuyaDevice tuyaDevice = getTuyaDevice();
        if (!commandRequest.isEmpty() && tuyaDevice != null) {
            if (subDeviceId.isPresent()) {
                tuyaDevice.set(commandRequest, subDeviceId.get());
            } else {
                tuyaDevice.set(commandRequest);
            }
        }
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> future = this.pollingJob;
        if (future != null) {
            future.cancel(true);
        }
    }

    @Override
    public void initialize() {
        // clear all maps
        dpToChannelId.clear();
        dp2ToChannelId.clear();
        channelIdToChannelTypeUID.clear();
        channelIdToConfiguration.clear();

        configuration = getConfigAs(DeviceConfiguration.class);

        // check if we have channels and add them if available
        if (thing.getChannels().isEmpty()) {
            // stored schemas are usually more complete
            Map<String, SchemaDp> schema = SCHEMAS.get(configuration.productId);
            if (schema == null) {
                if (!schemaDps.isEmpty()) {
                    // fallback to retrieved schema
                    schema = schemaDps.stream().collect(Collectors.toMap(s -> s.code, s -> s));
                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                            "No channels added and schema not found.");
                    return;
                }
            }

            addChannels(schema);
        }

        thing.getChannels().forEach(this::configureChannel);
    }

    private void addChannels(Map<String, SchemaDp> schema) {
        ThingBuilder thingBuilder = editThing();
        ThingUID thingUID = thing.getUID();
        ThingHandlerCallback callback = getCallback();

        if (callback == null) {
            logger.warn("Thing callback not found. Cannot auto-detect thing '{}' channels.", thingUID);
            return;
        }

        Map<String, Channel> channels = new HashMap<>(schema.entrySet().stream().map(e -> {
            String channelId = e.getKey();
            SchemaDp schemaDp = e.getValue();

            ChannelUID channelUID = new ChannelUID(thingUID, channelId);
            Map<String, @Nullable Object> configuration = new HashMap<>();
            configuration.put("dp", schemaDp.id);

            ChannelTypeUID channeltypeUID;
            if (COLOUR_CHANNEL_CODES.contains(channelId)) {
                channeltypeUID = CHANNEL_TYPE_UID_COLOR;
            } else if (DIMMER_CHANNEL_CODES.contains(channelId)) {
                channeltypeUID = CHANNEL_TYPE_UID_DIMMER;
                configuration.put("min", schemaDp.min);
                configuration.put("max", schemaDp.max);
            } else if ("bool".equals(schemaDp.type)) {
                channeltypeUID = CHANNEL_TYPE_UID_SWITCH;
            } else if ("enum".equals(schemaDp.type)) {
                channeltypeUID = CHANNEL_TYPE_UID_STRING;
                List<String> range = Objects.requireNonNullElse(schemaDp.range, List.of());
                configuration.put("range", String.join(",", range));
            } else if ("string".equals(schemaDp.type)) {
                channeltypeUID = CHANNEL_TYPE_UID_STRING;
            } else if ("value".equals(schemaDp.type)) {
                channeltypeUID = CHANNEL_TYPE_UID_NUMBER;
                configuration.put("min", schemaDp.min);
                configuration.put("max", schemaDp.max);
            } else {
                // e.g. type "raw", add empty channel
                return Map.entry("", ChannelBuilder.create(channelUID).build());
            }

            return Map.entry(channelId, callback.createChannelBuilder(channelUID, channeltypeUID).withLabel(channelId)
                    .withConfiguration(new Configuration(configuration)).build());
        }).filter(c -> !c.getKey().isEmpty()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

        List<String> channelSuffixes = List.of("", "_1", "_2");
        List<String> switchChannels = List.of("switch_led", "led_switch");
        channelSuffixes.forEach(suffix -> switchChannels.forEach(channel -> {
            Channel switchChannel = channels.get(channel + suffix);
            if (switchChannel != null) {
                // remove switch channel if brightness or color is present and add to dp2 instead
                ChannelConfiguration config = switchChannel.getConfiguration().as(ChannelConfiguration.class);
                Channel colourChannel = channels.get("colour_data" + suffix);
                Channel brightChannel = channels.get("bright_value" + suffix);
                boolean remove = false;

                if (colourChannel != null) {
                    colourChannel.getConfiguration().put("dp2", config.dp);
                    remove = true;
                }
                if (brightChannel != null) {
                    brightChannel.getConfiguration().put("dp2", config.dp);
                    remove = true;
                }

                if (remove) {
                    channels.remove(channel + suffix);
                }
            }
        }));

        channels.values().forEach(thingBuilder::withChannel);

        updateThing(thingBuilder.build());
    }

    private void configureChannel(Channel channel) {
        ChannelConfiguration configuration = channel.getConfiguration().as(ChannelConfiguration.class);
        ChannelTypeUID channelTypeUID = channel.getChannelTypeUID();

        if (channelTypeUID == null) {
            logger.warn("Could not determine ChannelTypeUID for '{}'", channel.getUID());
            return;
        }

        String channelId = channel.getUID().getId();

        if (!configuration.range.isEmpty()) {
            List<CommandOption> commandOptions = toCommandOptionList(
                    Arrays.stream(configuration.range.split(",")).collect(Collectors.toList()));
            dynamicCommandDescriptionProvider.setCommandOptions(channel.getUID(), commandOptions);
        }

        dpToChannelId.put(configuration.dp, channelId);
        channelIdToConfiguration.put(channelId, configuration);
        channelIdToChannelTypeUID.put(channelId, channelTypeUID);

        // check if we have additional DPs (these are switch DP for color or brightness only)
        if (configuration.dp2 != 0) {
            List<String> list = Objects
                    .requireNonNull(dp2ToChannelId.computeIfAbsent(configuration.dp2, ArrayList::new));
            list.add(channelId);
        }
    }

    private List<CommandOption> toCommandOptionList(List<String> options) {
        return options.stream().map(c -> new CommandOption(c, c)).collect(Collectors.toList());
    }

    private void addSingleExpiringCache(Integer key, Object value) {
        ExpiringCache<@Nullable Object> expiringCache = new ExpiringCache<>(Duration.ofSeconds(10), () -> null);
        expiringCache.putValue(value);
        deviceStatusCache.put(key, expiringCache);
    }

    @Override
    protected void updateState(String channelId, State state) {
        channelStateCache.put(channelId, state);
        super.updateState(channelId, state);
    }
}
