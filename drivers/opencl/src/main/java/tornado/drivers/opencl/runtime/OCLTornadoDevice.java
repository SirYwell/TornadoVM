/*
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.drivers.opencl.runtime;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.api.Event;
import tornado.api.enums.TornadoSchedulingStrategy;
import tornado.api.meta.TaskMetaData;
import tornado.common.*;
import tornado.common.enums.Access;
import tornado.common.exceptions.TornadoOutOfMemoryException;
import tornado.drivers.opencl.OCLDevice;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.drivers.opencl.OCLDriver;
import tornado.drivers.opencl.graal.OCLProviders;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import tornado.drivers.opencl.mm.*;
import tornado.runtime.api.CompilableTask;
import tornado.runtime.api.PrebuiltTask;
import tornado.runtime.sketcher.Sketch;
import tornado.runtime.sketcher.TornadoSketcher;

import static tornado.common.RuntimeUtilities.isPrimitiveArray;
import static tornado.common.Tornado.FORCE_ALL_TO_GPU;
import static tornado.common.exceptions.TornadoInternalError.*;
import static tornado.drivers.opencl.graal.compiler.OCLCompiler.compileSketchForDevice;
import static tornado.runtime.TornadoRuntime.getTornadoRuntime;

public class OCLTornadoDevice implements TornadoDevice {

    private final OCLDevice device;
    private final int deviceIndex;
    private final int platformIndex;
    private static OCLDriver driver = null;

    private static OCLDriver findDriver() {
        if (driver == null) {
            driver = getTornadoRuntime().getDriver(OCLDriver.class);
            guarantee(driver != null, "unable to find OpenCL driver");
        }
        return driver;
    }

    public OCLTornadoDevice(final int platformIndex, final int deviceIndex) {
        this.platformIndex = platformIndex;
        this.deviceIndex = deviceIndex;

        device = findDriver().getPlatformContext(platformIndex).devices()
                .get(deviceIndex);

    }

    @Override
    public void dumpEvents() {
        getDeviceContext().dumpEvents();
    }

    @Override
    public String getDescription() {
        final String availability = (device.isAvailable()) ? "available" : "not available";
        return String.format("%s %s (%s)", device.getName(), device.getDeviceType(), availability);
    }

    public OCLDevice getDevice() {
        return device;
    }

    public int getDeviceIndex() {
        return deviceIndex;
    }

    @Override
    public TornadoMemoryProvider getMemoryProvider() {
        return getDeviceContext().getMemoryManager();
    }

    public int getPlatformIndex() {
        return platformIndex;
    }

    public OCLDeviceContext getDeviceContext() {
        return getBackend().getDeviceContext();
    }

    public OCLBackend getBackend() {
        return findDriver().getBackend(platformIndex, deviceIndex);
    }

    @Override
    public void reset() {
        getBackend().reset();
    }

    @Override
    public String toString() {
        return String.format(device.getName());
    }

    @Override
    public TornadoSchedulingStrategy getPreferedSchedule() {
        if (null != device.getDeviceType()) {

            if (FORCE_ALL_TO_GPU) {
                return TornadoSchedulingStrategy.PER_ITERATION;
            }

            switch (device.getDeviceType()) {
                case CL_DEVICE_TYPE_GPU:
                    return TornadoSchedulingStrategy.PER_ITERATION;
                case CL_DEVICE_TYPE_CPU:
                    return TornadoSchedulingStrategy.PER_BLOCK;
                default:
                    return TornadoSchedulingStrategy.PER_ITERATION;
            }
        }
        shouldNotReachHere();
        return TornadoSchedulingStrategy.PER_ITERATION;
    }

    @Override
    public boolean isDistibutedMemory() {
        return true;
    }

    @Override
    public void ensureLoaded() {
        final OCLBackend backend = getBackend();
        if (!backend.isInitialised()) {
            backend.init();
        }
    }

    @Override
    public CallStack createStack(int numArgs) {
        return getDeviceContext().getMemoryManager().createCallStack(numArgs);
    }

    @Override
    public TornadoInstalledCode installCode(SchedulableTask task) {
        final OCLDeviceContext deviceContext = getDeviceContext();

        if (task instanceof CompilableTask) {
            final CompilableTask executable = (CompilableTask) task;
//			final long t0 = System.nanoTime();
            final ResolvedJavaMethod resolvedMethod = getTornadoRuntime()
                    .resolveMethod(executable.getMethod());

//			final long t1 = System.nanoTime();
            final Sketch sketch = TornadoSketcher.lookup(resolvedMethod);

            // copy meta data into task
            final TaskMetaData sketchMeta = sketch.getMeta();
            final TaskMetaData taskMeta = executable.meta();
            final Access[] sketchAccess = sketchMeta.getArgumentsAccess();
            final Access[] taskAccess = taskMeta.getArgumentsAccess();
            for (int i = 0; i < sketchAccess.length; i++) {
                taskAccess[i] = sketchAccess[i];
            }

            try {
                final OCLCompilationResult result = compileSketchForDevice(
                        sketch, executable,
                        (OCLProviders) getBackend().getProviders(), getBackend());

                if (deviceContext.isCached(task.getId(), resolvedMethod.getName())) {
                    return deviceContext.getCode(task.getId(), resolvedMethod.getName());
                }

//            if (SHOW_OPENCL) {
//                String filename = getFile(executable.getMethodName());
//                // Tornado.info("Generated code for device %s - %s\n",
//                // deviceContext.getDevice().getName(), filename);
//                try {
//                    PrintWriter fileOut = new PrintWriter(filename);
//                    String source = new String(result.getTargetCode(), "ASCII");
//                    fileOut.println(source.trim());
//                    fileOut.close();
//                } catch (UnsupportedEncodingException | FileNotFoundException e) {
//                    e.printStackTrace();
//                }
//            }
                return deviceContext.installCode(result);
            } catch (Exception e) {
                driver.fatal("unable to compile %s for device %s", task.getId(), getDeviceName());
                driver.fatal("exception occured when compiling %s", ((CompilableTask) task).getMethod().getName());
                driver.fatal("exception: %s", e.toString());
//                driver.fatal("cause: %s", e.getCause().toString());
                e.printStackTrace();
            }
            return null;
        } else if (task instanceof PrebuiltTask) {
            final PrebuiltTask executable = (PrebuiltTask) task;
            if (deviceContext.isCached(task.getId(), executable.getEntryPoint())) {
                return deviceContext.getCode(task.getId(), executable.getEntryPoint());
            }

            final Path path = Paths.get(executable.getFilename());
            guarantee(path.toFile().exists(), "file does not exist: %s", executable.getFilename());
            try {
                final byte[] source = Files.readAllBytes(path);
                return deviceContext.installCode(executable.meta(), task.getId(), executable.getEntryPoint(),
                        source);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        shouldNotReachHere("task of unknown type: " + task.getClass().getSimpleName());
        return null;
    }

    private String getFile(String name) {
        return String.format("%s/%s-%s.cl", OCLBackend.OPENCL_PATH.trim(), name.trim(), getDeviceName());
    }

    private ObjectBuffer createDeviceBuffer(Class<?> type, Object arg,
            OCLDeviceContext device) throws TornadoOutOfMemoryException {
//		System.out.printf("creating buffer: type=%s, arg=%s, device=%s\n",type.getSimpleName(),arg,device);
        ObjectBuffer result = null;
        if (type.isArray()) {

            if (!type.getComponentType().isArray()) {
                if (type == int[].class) {
                    result = new OCLIntArrayWrapper(device);
                } else if (type == short[].class) {
                    result = new OCLShortArrayWrapper(device);
                } else if (type == byte[].class) {
                    result = new OCLByteArrayWrapper(device);
                } else if (type == float[].class) {
                    result = new OCLFloatArrayWrapper(device);
                } else if (type == double[].class) {
                    result = new OCLDoubleArrayWrapper(device);
                } else if (type == long[].class) {
                    result = new OCLLongArrayWrapper(device);
                } else {
                    unimplemented("array of type %s", type.getName());
                }
            } else {
                final Class<?> componentType = type.getComponentType();
                if (isPrimitiveArray(componentType)) {

                    if (componentType == int[].class) {
                        result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLIntArrayWrapper(context));
                    } else if (componentType == short[].class) {
                        result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLShortArrayWrapper(context));
                    } else if (componentType == byte[].class) {
                        result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLByteArrayWrapper(context));
                    } else if (componentType == float[].class) {
                        result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLFloatArrayWrapper(context));
                    } else if (componentType == double[].class) {
                        result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLDoubleArrayWrapper(context));
                    } else if (componentType == long[].class) {
                        result = new OCLMultiDimArrayWrapper<>(device, (OCLDeviceContext context) -> new OCLLongArrayWrapper(context));
                    } else {
                        unimplemented("array of type %s", type.getName());
                    }
                } else {
                    unimplemented("multi-dimensional array of type %s", type.getName());
                }
            }

        } else if (!type.isPrimitive()
                && !type.isArray()) {
//			System.out.println("creating object wrapper...good");
            result = new OCLObjectWrapper(device, arg);
        }

        guarantee(result
                != null,
                "Unable to create buffer for object: " + type);
        return result;
    }

    @Override
    public int ensureAllocated(Object object, DeviceObjectState state
    ) {
        if (!state.hasBuffer()) {
            try {
                final ObjectBuffer buffer = createDeviceBuffer(
                        object.getClass(), object, getDeviceContext());
                buffer.allocate(object);
                state.setBuffer(buffer);

                final Class<?> type = object.getClass();
                if (!type.isArray()) {
                    buffer.write(object);
                }

                state.setValid(true);
            } catch (TornadoOutOfMemoryException e) {
                e.printStackTrace();
            }
        }

        if (!state.isValid()) {
            try {
                state.getBuffer().allocate(object);
                final Class<?> type = object.getClass();
                if (!type.isArray()) {
                    state.getBuffer().write(object);
                }
                state.setValid(true);
            } catch (TornadoOutOfMemoryException e) {
                e.printStackTrace();
            }
        }
        return -1;
    }

    @Override
    public int ensurePresent(Object object, DeviceObjectState state
    ) {
        ensurePresent(object, state, null);
        return -1;
    }

    @Override
    public int ensurePresent(Object object, DeviceObjectState state,
            int[] events
    ) {
        if (!state.isValid()) {
            ensureAllocated(object, state);
        }

        if (!state.hasContents()) {
            state.setContents(true);
            return state.getBuffer().enqueueWrite(object, events, events == null);
        }
        return -1;
    }

    @Override
    public int streamIn(Object object, DeviceObjectState state
    ) {
        streamIn(object, state, null);
        return -1;
    }

    @Override
    public int streamIn(Object object, DeviceObjectState state,
            int[] events
    ) {
        if (!state.isValid()) {
            ensureAllocated(object, state);
        }

        state.setContents(true);
        return state.getBuffer().enqueueWrite(object, events, events == null);

    }

    @Override
    public int streamOut(Object object, DeviceObjectState state
    ) {
        streamOut(object, state, null);
        return -1;
    }

    @Override
    public int streamOut(Object object, DeviceObjectState state,
            int[] list
    ) {
        guarantee(state.isValid(), "invalid variable");

        return state.getBuffer().enqueueRead(object, list, list == null);
    }

    @Override
    public void streamOutBlocking(Object object, DeviceObjectState state
    ) {
        streamOutBlocking(object, state, null);
    }

    @Override
    public void streamOutBlocking(Object object, DeviceObjectState state,
            int[] events
    ) {
        guarantee(state.isValid(), "invalid variable");

        state.getBuffer().read(object, events, events == null);
    }

    public void sync(Object... objects) {
        for (Object obj : objects) {
            sync(obj);
        }
    }

    public void sync(Object object) {
        final DeviceObjectState state = getTornadoRuntime().resolveObject(object).getDeviceState(this);
        resolveEvent(streamOut(object, state)).waitOn();
    }

    @Override
    public void flush() {
        this.getDeviceContext().flush();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof OCLTornadoDevice) {
            final OCLTornadoDevice other = (OCLTornadoDevice) obj;
            return (other.deviceIndex == deviceIndex && other.platformIndex == platformIndex);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + this.deviceIndex;
        hash = 89 * hash + this.platformIndex;
        return hash;
    }

    @Override
    public void sync() {
        getDeviceContext().sync();
    }

    @Override
    public int enqueueBarrier() {
        return getDeviceContext().enqueueBarrier();
    }

    @Override
    public int enqueueBarrier(int[] events) {
        return getDeviceContext().enqueueBarrier(events);
    }

    @Override
    public int enqueueMarker() {
        return getDeviceContext().enqueueMarker();
    }

    @Override
    public int enqueueMarker(int[] events) {
        return getDeviceContext().enqueueMarker(events);
    }

    @Override
    public void dumpMemory(String file) {
        final OCLMemoryManager mm = getDeviceContext().getMemoryManager();
        final OCLByteBuffer buffer = mm.getSubBuffer(0, (int) mm.getHeapSize());
        buffer.read();

        try (FileOutputStream fos = new FileOutputStream(file); FileChannel channel = fos.getChannel()) {
            channel.write(buffer.buffer());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public Event resolveEvent(int event) {
        return getDeviceContext().resolveEvent(event);
    }

    @Override
    public void flushEvents() {
        getDeviceContext().flushEvents();
    }

    @Override
    public void markEvent() {
        getDeviceContext().markEvent();
    }

    @Override
    public String getDeviceName() {
        return String.format("opencl-%d-%d", platformIndex, deviceIndex);
    }

}
