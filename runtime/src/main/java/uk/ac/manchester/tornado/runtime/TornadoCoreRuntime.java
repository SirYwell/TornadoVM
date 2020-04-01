/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.runtime;

import static org.graalvm.compiler.debug.GraalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.runtime.common.Tornado.SHOULD_LOAD_RMI;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.hotspot.HotSpotGraalOptionValues;
import org.graalvm.compiler.lir.constopt.ConstantLoadOptimization;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationStage;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCIBackend;
import uk.ac.manchester.tornado.api.TornadoDriver;
import uk.ac.manchester.tornado.api.TornadoRuntimeCI;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSnippetReflectionProvider;
import uk.ac.manchester.tornado.runtime.tasks.GlobalObjectState;

public class TornadoCoreRuntime extends TornadoLogger implements TornadoRuntimeCI {

    private static final OptionValues options;
    static {
        EconomicMap<OptionKey<?>, Object> opts = OptionValues.newOptionMap();
        opts.putAll(HotSpotGraalOptionValues.defaultOptions().getMap());

        opts.put(GraalOptions.OmitHotExceptionStacktrace, false);

        opts.put(GraalOptions.MatchExpressions, true);
        opts.put(GraalOptions.RemoveNeverExecutedCode, false);
        opts.put(ConstantLoadOptimization.Options.LIROptConstantLoadOptimization, false);
        opts.put(PostAllocationOptimizationStage.Options.LIROptRedundantMoveElimination, false);

        options = new OptionValues(opts);
    }

    private static final Executor EXECUTOR = Executors.newCachedThreadPool();
    private static final TornadoCoreRuntime runtime = new TornadoCoreRuntime();
    private static final JVMMapping JVM = new JVMMapping();

    public static TornadoCoreRuntime getTornadoRuntime() {
        return runtime;
    }

    private static DebugContext debugContext = null;
    public static DebugContext getDebugContext() {
        if (debugContext == null) {
            debugContext = DebugContext.create(getOptions(), new GraalDebugHandlersFactory(new TornadoSnippetReflectionProvider()));
        }
        return debugContext;
    }

    public static Executor getTornadoExecutor() {
        return EXECUTOR;
    }

    public static JVMCIBackend getVMBackend() {
        return runtime.vmBackend;
    }

    public static HotSpotJVMCIRuntime getVMRuntime() {
        return runtime.vmRuntime;
    }

    public static TornadoVMConfig getVMConfig() {
        return runtime.vmConfig;
    }

    private final Map<Object, GlobalObjectState> objectMappings;
    private TornadoAcceleratorDriver[] drivers;
    private int driverCount;
    private final JVMCIBackend vmBackend;
    private final HotSpotJVMCIRuntime vmRuntime;
    private final TornadoVMConfig vmConfig;

    private static final int DEFAULT_DRIVER = 0;

    // @formatter:off
    public enum TORNADO_DRIVERS_DESCRIPTION {
        OPENCL("implemented"),
        PTX("unsupported");

        String status;

        TORNADO_DRIVERS_DESCRIPTION(String status) {
            this.status = status;
        }

        String getStatus() {
            return status;
        }
    }
    // @formatter:on

    private TornadoCoreRuntime() {
        objectMappings = new WeakHashMap<>();

        guarantee(!GraalOptions.OmitHotExceptionStacktrace.getValue(options), "error");

        if (!(JVMCI.getRuntime() instanceof HotSpotJVMCIRuntime)) {
            shouldNotReachHere("Unsupported JVMCIRuntime: ", JVMCI.getRuntime().getClass().getName());
        }
        vmRuntime = (HotSpotJVMCIRuntime) JVMCI.getRuntime();
        vmBackend = vmRuntime.getHostJVMCIBackend();
        vmConfig = new TornadoVMConfig(vmRuntime.getConfigStore());
        drivers = loadDrivers();
    }

    public void clearObjectState() {
        for (GlobalObjectState gs : objectMappings.values()) {
            gs.clear();
        }
        objectMappings.clear();
    }

    private TornadoAcceleratorDriver[] loadDrivers() {
        ServiceLoader<TornadoDriverProvider> loader = ServiceLoader.load(TornadoDriverProvider.class);
        List<TornadoDriverProvider> providerList = StreamSupport.stream(loader.spliterator(), false).sorted().collect(Collectors.toList());
        drivers = new TornadoAcceleratorDriver[TORNADO_DRIVERS_DESCRIPTION.values().length];
        int index = 0;
        for (TornadoDriverProvider provider : providerList) {
            boolean isRMI = provider.getName().equalsIgnoreCase("RMI Driver");
            if ((!isRMI) || (isRMI && SHOULD_LOAD_RMI)) {
                drivers[index] = provider.createDriver(options, vmRuntime, vmConfig);
                if (drivers[index] != null) {
                    index++;
                }
            }
        }
        driverCount = index;
        return drivers;
    }

    public static OptionValues getOptions() {
        return options;
    }

    public GlobalObjectState resolveObject(Object object) {
        if (!objectMappings.containsKey(object)) {
            final GlobalObjectState state = new GlobalObjectState();
            objectMappings.put(object, state);
        }
        return objectMappings.get(object);
    }

    @Override
    public <D extends TornadoDriver> int getDriverIndex(Class<D> driverClass) {
        for (int driverIndex = 0; driverIndex < drivers.length; driverIndex++) {
            if (drivers[driverIndex].getClass() == driverClass) {
                return driverIndex;
            }
        }
        shouldNotReachHere("Could not find index for driver: " + driverClass);
        return -1;
    }

    public MetaAccessProvider getMetaAccess() {
        return vmBackend.getMetaAccess();
    }

    public ResolvedJavaMethod resolveMethod(final Method method) {
        return getMetaAccess().lookupJavaMethod(method);
    }

    @Override
    public TornadoAcceleratorDriver getDriver(int index) {
        return drivers[index];
    }

    @SuppressWarnings("unchecked")
    @Override
    public <D extends TornadoDriver> D getDriver(Class<D> type) {
        for (TornadoAcceleratorDriver driver : drivers) {
            if (driver.getClass() == type) {
                return (D) driver;
            }
        }
        return null;
    }

    @Override
    public int getNumDrivers() {
        return driverCount;
    }

    @Override
    public TornadoAcceleratorDevice getDefaultDevice() {
        return (drivers == null || drivers[DEFAULT_DRIVER] == null) ? JVM : (TornadoAcceleratorDevice) drivers[DEFAULT_DRIVER].getDefaultDevice();
    }

    @Override
    public TornadoRuntimeCI callRuntime() {
        return runtime;
    }
}
