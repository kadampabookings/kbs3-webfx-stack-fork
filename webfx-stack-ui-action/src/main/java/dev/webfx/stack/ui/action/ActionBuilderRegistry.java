package dev.webfx.stack.ui.action;

import dev.webfx.stack.ui.action.impl.ActionBuilderRegistryImpl;

/**
 * @author Bruno Salmon
 */
public interface ActionBuilderRegistry extends ActionFactory {

    @Override
    ActionBuilder newActionBuilder(Object actionKey);

    void registerActionBuilder(ActionBuilder actionBuilder);

    static ActionBuilderRegistry get() {
        return ActionBuilderRegistryImpl.get();
    }
}
