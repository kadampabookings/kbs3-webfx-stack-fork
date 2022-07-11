package dev.webfx.stack.platform.webassembly.spi.impl.teavm.wasm;

import org.teavm.interop.Address;

/**
 * @author Bruno Salmon
 */
public class WasmUtil {

    public static TeaVmWasmMemoryBufferReader getMemoryBufferReader(Address memoryAddress) {
        return new TeaVmWasmMemoryBufferReader(memoryAddress);
    }

}
