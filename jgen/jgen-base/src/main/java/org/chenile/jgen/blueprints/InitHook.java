package org.chenile.jgen.blueprints;

/**
 * This class allows initialization of the blueprint by doing custom setup and injecting
 * code into the blueprint config if necessary
 */
public interface InitHook {
    public void init(BlueprintConfig blueprintConfig);
}
