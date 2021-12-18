/**
 * Copyright (c) 2021 Contributors to the SmartHome/J project
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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.smarthomej.binding.amazonechocontrol.internal.jsons.JsonDevices;

/**
 * The {@link TextWrapper} is a wrapper class for text or TTS instructions
 *
 * @author Jan N. Klug - Initial contribution
 */
@NonNullByDefault
public class TextWrapper {
    private final List<JsonDevices.Device> devices = new ArrayList<>();
    private final String text;
    private final List<@Nullable Integer> ttsVolumes = new ArrayList<>();
    private final List<@Nullable Integer> standardVolumes = new ArrayList<>();

    public TextWrapper(String text) {
        this.text = text;
    }

    public void add(JsonDevices.Device device, @Nullable Integer ttsVolume, @Nullable Integer standardVolume) {
        devices.add(device);
        ttsVolumes.add(ttsVolume);
        standardVolumes.add(standardVolume);
    }

    public List<JsonDevices.Device> getDevices() {
        return devices;
    }

    public List<@Nullable Integer> getTtsVolumes() {
        return ttsVolumes;
    }

    public List<@Nullable Integer> getStandardVolumes() {
        return standardVolumes;
    }

    public String getText() {
        return text;
    }
}
