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
package org.smarthomej.commons.itemvalueconverter.converter;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.HSBType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.smarthomej.commons.itemvalueconverter.ItemValueConverterChannelConfig;
import org.smarthomej.commons.transform.ValueTransformation;

/**
 * The {@link ColorItemConverter} implements {@link org.openhab.core.library.items.ColorItem} conversions
 *
 * @author Jan N. Klug - Initial contribution
 */

@NonNullByDefault
public class ColorItemConverter extends AbstractTransformingItemConverter {
    private static final BigDecimal BYTE_FACTOR = BigDecimal.valueOf(2.55);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final Pattern TRIPLE_MATCHER = Pattern.compile("(?<r>\\d+),(?<g>\\d+),(?<b>\\d+)");

    private State state = UnDefType.UNDEF;

    public ColorItemConverter(Consumer<State> updateState, Consumer<Command> postCommand,
            @Nullable Consumer<String> sendValue, ValueTransformation stateTransformations,
            ValueTransformation commandTransformations, ItemValueConverterChannelConfig channelConfig) {
        super(updateState, postCommand, sendValue, stateTransformations, commandTransformations, channelConfig);
    }

    @Override
    protected @Nullable Command toCommand(String value) {
        return null;
    }

    @Override
    public String toString(Command command) {
        String string = channelConfig.commandToFixedValue(command);
        if (string != null) {
            return string;
        }

        if (command instanceof HSBType) {
            HSBType newState = (HSBType) command;
            state = newState;
            return hsbToString(newState);
        } else if (command instanceof PercentType && state instanceof HSBType) {
            HSBType newState = new HSBType(((HSBType) state).getBrightness(), ((HSBType) state).getSaturation(),
                    (PercentType) command);
            state = newState;
            return hsbToString(newState);
        }

        throw new IllegalArgumentException("Command type '" + command.toString() + "' not supported");
    }

    @Override
    public Optional<State> toState(String string) {
        State newState = UnDefType.UNDEF;
        if (string.equals(channelConfig.onValue)) {
            if (state instanceof HSBType) {
                newState = new HSBType(((HSBType) state).getHue(), ((HSBType) state).getSaturation(),
                        PercentType.HUNDRED);
            } else {
                newState = HSBType.WHITE;
            }
        } else if (string.equals(channelConfig.offValue)) {
            if (state instanceof HSBType) {
                newState = new HSBType(((HSBType) state).getHue(), ((HSBType) state).getSaturation(), PercentType.ZERO);
            } else {
                newState = HSBType.BLACK;
            }
        } else if (string.equals(channelConfig.increaseValue) && state instanceof HSBType) {
            BigDecimal newBrightness = ((HSBType) state).getBrightness().toBigDecimal().add(channelConfig.step);
            if (HUNDRED.compareTo(newBrightness) < 0) {
                newBrightness = HUNDRED;
            }
            newState = new HSBType(((HSBType) state).getHue(), ((HSBType) state).getSaturation(),
                    new PercentType(newBrightness));
        } else if (string.equals(channelConfig.decreaseValue) && state instanceof HSBType) {
            BigDecimal newBrightness = ((HSBType) state).getBrightness().toBigDecimal().subtract(channelConfig.step);
            if (BigDecimal.ZERO.compareTo(newBrightness) > 0) {
                newBrightness = BigDecimal.ZERO;
            }
            newState = new HSBType(((HSBType) state).getHue(), ((HSBType) state).getSaturation(),
                    new PercentType(newBrightness));
        } else {
            Matcher matcher = TRIPLE_MATCHER.matcher(string);
            if (matcher.matches()) {
                switch (channelConfig.colorMode) {
                    case RGB:
                        int r = Integer.parseInt(matcher.group("r"));
                        int g = Integer.parseInt(matcher.group("g"));
                        int b = Integer.parseInt(matcher.group("b"));
                        newState = HSBType.fromRGB(r, g, b);
                        break;
                    case HSB:
                        newState = new HSBType(string);
                        break;
                }
            }
        }

        state = newState;
        return Optional.of(newState);
    }

    private String hsbToString(HSBType state) {
        switch (channelConfig.colorMode) {
            case RGB:
                PercentType[] rgb = state.toRGB();
                return String.format("%1$d,%2$d,%3$d", rgb[0].toBigDecimal().multiply(BYTE_FACTOR).intValue(),
                        rgb[1].toBigDecimal().multiply(BYTE_FACTOR).intValue(),
                        rgb[2].toBigDecimal().multiply(BYTE_FACTOR).intValue());
            case HSB:
                return state.toString();
        }
        throw new IllegalStateException("Invalid colorMode setting");
    }

    public enum ColorMode {
        RGB,
        HSB
    }
}
