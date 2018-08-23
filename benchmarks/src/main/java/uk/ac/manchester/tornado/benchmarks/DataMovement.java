/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.benchmarks;

import uk.ac.manchester.tornado.api.TornadoDeviceObjectState;
import uk.ac.manchester.tornado.api.TornadoObjectState;
import uk.ac.manchester.tornado.api.common.GenericDevice;
import uk.ac.manchester.tornado.api.runtinface.TornadoGenericDriver;
import uk.ac.manchester.tornado.api.runtinface.TornadoRuntime;
import uk.ac.manchester.tornado.api.runtinface.TornadoRuntimeCI;

public class DataMovement {

    public static Object createArray(String type, int size) {

        switch (type) {
            case "i8":
                return new byte[size];
            case "i32":
                return new int[size];
            case "i64":
                return new long[size];
            case "f32":
                return new float[size];
            case "f64":
                return new double[size];

            default:
                System.err.printf("type %s is not supported", type);
                System.exit(-1);
        }
        return null;
    }

    private static GenericDevice resolveDevice(TornadoRuntimeCI runtime, String device) {
        final String[] ids = device.split(":");
        final TornadoGenericDriver driver = runtime.getDriver(Integer.parseInt(ids[0]));
        return driver.getDevice(Integer.parseInt(ids[1]));
    }

    public static void main(String args[]) {
        final int startSize = Integer.parseInt(System.getProperty("startsize", "2"));
        final int endSize = Integer.parseInt(System.getProperty("endsize", "8192"));
        final int iterations = Integer.parseInt(System.getProperty("iterations", "100"));
        final String[] types = System.getProperty("types", "i8,i32,i64,f32,f64").split(",");

        final String[] devices = System.getProperty("devices", "0:0").split(",");

        System.out.println("device,type,numelements,numbytes,iterations,streamInElapsed,streamOutElapsed");

        for (final String deviceStr : devices) {
            TornadoRuntimeCI runtime = TornadoRuntime.getTornadoRuntime();
            final GenericDevice device = resolveDevice(runtime, deviceStr);

            for (final String type : types) {
                for (int size = startSize; size <= endSize; size <<= 1) {

                    final Object array = createArray(type, size);
                    final TornadoObjectState globalState = runtime.resolveObject(array);
                    final TornadoDeviceObjectState deviceState = globalState.getDeviceState(device);

                    device.ensureAllocated(array, deviceState);

                    final long t0 = System.nanoTime();
                    for (int i = 0; i < iterations; i++) {
                        device.streamIn(array, deviceState);
                    }
                    device.sync();
                    final long t1 = System.nanoTime();
                    final double streamInElapsed = (t1 - t0) * 1e-9;

                    final long t2 = System.nanoTime();
                    for (int i = 0; i < iterations; i++) {
                        device.streamOut(array, deviceState, null);
                    }
                    device.sync();
                    final long t3 = System.nanoTime();
                    final double streamOutElapsed = (t3 - t2) * 1e-9;

                    final long numBytes = size * (Integer.parseInt(type.substring(1)) / 8);

                    System.out.printf("%s,%s,%d,%d,%d,%.9f,%.9f\n", device.getDeviceName(), type, size, numBytes, iterations, streamInElapsed, streamOutElapsed);
                    runtime.clearObjectState();
                    device.reset();

                    if (size == 0) {
                        size++;
                    }
                }
            }
        }
    }

}
