package com.darylgo.camera2.sample

import java.util.concurrent.*
import java.util.concurrent.locks.AbstractQueuedSynchronizer

/**
 * 支持通过 [set] 方法设置结果，并且解除线程等待的 [Future]。
 *
 * @author hjd 2018.07.26
 */
open class SettableFuture<V> : Future<V> {

    private val sync: Sync<V> = Sync()

    /**
     * 设置结果，解除线程同步等待。
     */
    fun set(value: V?): Boolean {
        return sync.set(value)
    }

    override fun isDone(): Boolean {
        return sync.isDone()
    }

    /**
     * 获取异步结果，如果结果还没有计算出来，则进入同步等待状态。
     */
    override fun get(): V? {
        return sync.get()
    }

    /**
     * 获取异步结果，如果结果还没有计算出来，则进入同步等待状态，一段时间之后超时。
     */
    override fun get(timeout: Long, unit: TimeUnit): V? {
        return sync[unit.toNanos(timeout)]
    }

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        return sync.cancel(mayInterruptIfRunning)
    }

    override fun isCancelled(): Boolean {
        return sync.isCancelled()
    }

    private class Sync<V> : AbstractQueuedSynchronizer() {

        private var value: V? = null
        private var exception: Throwable? = null

        /**
         * Checks if the state is [.COMPLETED], [.CANCELLED], or [ ].
         */
        fun isDone(): Boolean {
            return state and (COMPLETED or CANCELLED or INTERRUPTED) != 0
        }

        /**
         * Checks if the state is [.CANCELLED] or [.INTERRUPTED].
         */
        fun isCancelled(): Boolean {
            return state and (CANCELLED or INTERRUPTED) != 0
        }

        /**
         * Acquisition succeeds if the future is done, otherwise it fails.
         */
        override fun tryAcquireShared(ignored: Int): Int {
            return if (isDone()) {
                1
            } else -1
        }

        /**
         * We always allow a release to go through, this means the state has been
         * successfully changed and the result is available.
         */
        override fun tryReleaseShared(finalState: Int): Boolean {
            state = finalState
            return true
        }

        /**
         * Blocks until the task is complete or the timeout expires.  Throws a
         * [TimeoutException] if the timer expires, otherwise behaves like
         * [.get].
         */
        operator fun get(nanos: Long): V? {
            // Attempt to acquire the shared lock with a timeout.
            if (!tryAcquireSharedNanos(-1, nanos)) {
                throw TimeoutException("Timeout waiting for task.")
            }
            return getValue()
        }

        /**
         * Blocks until [.complete] has been successfully called.  Throws a [CancellationException]
         * if the task was cancelled, or a [ExecutionException] if the task completed with an error.
         */
        fun get(): V? {
            // Acquire the shared lock allowing interruption.
            acquireSharedInterruptibly(-1)
            return getValue()
        }

        /**
         * Implementation of the actual value retrieval.  Will return the value
         * on success, an exception on failure, a cancellation on cancellation, or
         * an illegal state if the synchronizer is in an invalid state.
         */
        private fun getValue(): V? {
            val state = state
            when (state) {
                COMPLETED -> return if (exception != null) {
                    throw ExecutionException(exception)
                } else {
                    value
                }
                CANCELLED, INTERRUPTED -> throw cancellationExceptionWithCause("Task was cancelled.", exception)
                else -> throw IllegalStateException("Error, synchronizer in invalid state: $state")
            }
        }

        private fun cancellationExceptionWithCause(message: String?, cause: Throwable?): CancellationException {
            val exception = CancellationException(message)
            exception.initCause(cause)
            return exception
        }

        /**
         * Checks if the state is [.INTERRUPTED].
         */
        fun wasInterrupted(): Boolean {
            return state == INTERRUPTED
        }

        /**
         * Transition to the COMPLETED state and set the value.
         */
        fun set(v: V?): Boolean {
            return complete(v, null, COMPLETED)
        }

        /**
         * Transition to the COMPLETED state and set the exception.
         */
        fun setException(t: Throwable): Boolean {
            return complete(null, t, COMPLETED)
        }

        /**
         * Transition to the CANCELLED or INTERRUPTED state.
         */
        fun cancel(interrupt: Boolean): Boolean {
            return complete(null, null, if (interrupt) INTERRUPTED else CANCELLED)
        }

        /**
         * Implementation of completing a task.  Either `v` or `t` will
         * be set but not both.  The `finalState` is the state to change to
         * from [.RUNNING].  If the state is not in the RUNNING state we
         * return `false` after waiting for the state to be set to a valid
         * final state ([.COMPLETED], [.CANCELLED], or [ ][.INTERRUPTED]).
         *
         * @param v          the value to set as the result of the computation.
         * @param t          the exception to set as the result of the computation.
         * @param finalState the state to transition to.
         */
        private fun complete(v: V?, t: Throwable?, finalState: Int): Boolean {
            val doCompletion = compareAndSetState(RUNNING, COMPLETING)
            if (doCompletion) {
                // If this thread successfully transitioned to COMPLETING, set the value
                // and exception and then release to the final state.
                this.value = v
                // Don't actually construct a CancellationException until necessary.
                this.exception = if (finalState and (CANCELLED or INTERRUPTED) != 0) {
                    CancellationException("Future.cancel() was called.")
                } else {
                    t
                }
                releaseShared(finalState)
            } else if (state == COMPLETING) {
                // If some other thread is currently completing the future, block until
                // they are done so we can guarantee completion.
                acquireShared(-1)
            }
            return doCompletion
        }

        companion object {
            private const val RUNNING: Int = 0
            private const val COMPLETING: Int = 1
            private const val COMPLETED: Int = 2
            private const val CANCELLED: Int = 4
            private const val INTERRUPTED: Int = 8
        }
    }

}