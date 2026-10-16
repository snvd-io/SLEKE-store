package com.sleke.library.runtime

interface LoggingRegistry {
    fun registerOwned(debug: Boolean)
    fun isOwnedRegistered(): Boolean
}

class RuntimeInitializer(private val loggingRegistry: LoggingRegistry) {
    @Synchronized
    fun initialize(config: RuntimeConfig) {
        if (!loggingRegistry.isOwnedRegistered()) loggingRegistry.registerOwned(config.debug)
    }
}
