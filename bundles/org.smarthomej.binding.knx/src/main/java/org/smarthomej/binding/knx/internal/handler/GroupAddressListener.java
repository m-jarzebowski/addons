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
package org.smarthomej.binding.knx.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.smarthomej.binding.knx.internal.client.BusMessageListener;

import tuwien.auto.calimero.GroupAddress;

/**
 * The {@link GroupAddressListener} is an interface that needs to be
 * implemented by classes that want to listen to Group Addresses
 * on the KNX bus
 *
 * @author Karel Goderis - Initial contribution
 */
@NonNullByDefault
public interface GroupAddressListener extends BusMessageListener {

    /**
     * Called to verify if the GroupAddressListener has an interest in the given GroupAddress
     *
     * @param destination
     */
    public boolean listensTo(GroupAddress destination);
}
