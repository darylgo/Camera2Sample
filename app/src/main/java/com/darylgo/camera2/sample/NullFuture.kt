package com.darylgo.camera2.sample

/**
 *
 *
 * @author hjd 2019.01.24
 */
class NullFuture<V> : SettableFuture<V>() {
    init {
        set(null)
    }
}