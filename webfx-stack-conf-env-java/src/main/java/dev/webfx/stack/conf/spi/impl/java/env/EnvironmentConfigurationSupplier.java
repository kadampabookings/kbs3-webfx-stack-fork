package dev.webfx.stack.conf.spi.impl.java.env;

import dev.webfx.platform.async.Future;
import dev.webfx.platform.console.Console;
import dev.webfx.platform.ast.ReadOnlyAstObject;
import dev.webfx.stack.conf.spi.ConfigurationSupplier;
import dev.webfx.stack.conf.spi.HasConfigurationLogInfo;

import java.util.Optional;

/**
 * @author Bruno Salmon
 */
public class EnvironmentConfigurationSupplier implements ConfigurationSupplier, HasConfigurationLogInfo {

    @Override
    public String getLogInfo() {
        return "Environment variables can be used for configuration resolution";
    }

    @Override
    public Optional<String> resolveVariable(String variableName) {
        String value = System.getenv(variableName);
        if (value != null)
            Console.log("INFO: " + variableName + " was resolved from environment variables");
        return value != null ? Optional.of(value) : Optional.empty();
    }

    @Override
    public boolean canReadConfiguration(String configName) {
        return false;
    }

    @Override
    public ReadOnlyAstObject readConfiguration(String configName, boolean resolveVariables) {
        throw new IllegalArgumentException("No configuration found for " + configName);
    }

    @Override
    public boolean canWriteConfiguration(String configName) {
        return false;
    }

    @Override
    public Future<Void> writeConfiguration(String configName, ReadOnlyAstObject config) {
        throw new IllegalArgumentException("No configuration found for " + configName);
    }
}
