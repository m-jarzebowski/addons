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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StopMoveType;
import org.openhab.core.library.types.UpDownType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.smarthomej.commons.itemvalueconverter.ItemValueConverterChannelConfig;
import org.smarthomej.commons.transform.ValueTransformation;

/**
 * The {@link RollershutterItemConverter} implements {@link org.openhab.core.library.items.RollershutterItem}
 * conversions
 *
 * @author Jan N. Klug - Initial contribution
 */

@NonNullByDefault
public class RollershutterItemConverter extends AbstractTransformingItemConverter {

    public RollershutterItemConverter(Consumer<State> updateState, Consumer<Command> postCommand,
            @Nullable Consumer<String> sendValue, ValueTransformation stateTransformations,
            ValueTransformation commandTransformations, ItemValueConverterChannelConfig channelConfig) {
        super(updateState, postCommand, sendValue, stateTransformations, commandTransformations, channelConfig);
    }

    @Override
    public String toString(Command command) {
        String string = channelConfig.commandToFixedValue(command);
        if (string != null) {
            return string;
        }

        if (command instanceof PercentType) {
            final String downValue = channelConfig.downValue;
            final String upValue = channelConfig.upValue;
            if (command.equals(PercentType.HUNDRED) && downValue != null) {
                return downValue;
            } else if (command.equals(PercentType.ZERO) && upValue != null) {
                return upValue;
            } else {
                return ((PercentType) command).toString();
            }
        }

        throw new IllegalArgumentException("Command type '" + command.toString() + "' not supported");
    }

    @Override
    protected @Nullable Command toCommand(String string) {
        if (string.equals(channelConfig.upValue)) {
            return UpDownType.UP;
        } else if (string.equals(channelConfig.downValue)) {
            return UpDownType.DOWN;
        } else if (string.equals(channelConfig.moveValue)) {
            return StopMoveType.MOVE;
        } else if (string.equals(channelConfig.stopValue)) {
            return StopMoveType.STOP;
        }

        return null;
    }

    @Override
    public Optional<State> toState(String string) {
        State newState = UnDefType.UNDEF;
        try {
            BigDecimal value = new BigDecimal(string);
            if (value.compareTo(PercentType.HUNDRED.toBigDecimal()) > 0) {
                value = PercentType.HUNDRED.toBigDecimal();
            }
            if (value.compareTo(PercentType.ZERO.toBigDecimal()) < 0) {
                value = PercentType.ZERO.toBigDecimal();
            }
            newState = new PercentType(value);
        } catch (NumberFormatException e) {
            // ignore
        }

        return Optional.of(newState);
    }
}
