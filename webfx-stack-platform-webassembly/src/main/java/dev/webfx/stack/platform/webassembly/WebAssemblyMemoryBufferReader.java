package dev.webfx.stack.platform.webassembly;

/**
 * @author Bruno Salmon
 */
public interface WebAssemblyMemoryBufferReader extends WebAssemblyMemoryBufferHolder {

    byte[] readByteArray(int length);

    int[] readIntArray(int length);

    float[] readFloatArray(int length);

    double[] readDoubleArray(int length);

}
