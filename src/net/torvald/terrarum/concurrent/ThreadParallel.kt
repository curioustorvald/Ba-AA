package net.torvald.terrarum.concurrent

import net.torvald.aa.demoplayer.BaAA

/**
 * Created by minjaesong on 16-05-25.
 */
object ThreadParallel {
    private val pool: Array<Thread?> = Array(BaAA.systemThreads, { null })

    /**
     * Map Runnable object to certain index of the thread pool.
     * @param index of the runnable
     * @param runnable
     * @param prefix Will name each thread like "Foo-1", "Foo-2", etc.
     */
    fun map(index: Int, runnable: Runnable, prefix: String) {
        pool[index] = Thread(runnable, "$prefix-$index")
    }

    /**
     * Start all thread in the pool. If the thread in the pool is NULL, it will simply ignored.
     */
    fun startAll() {
        pool.forEach { it?.start() }
    }

    /**
     * Start all thread in the pool and wait for them to all die. If the thread in the pool is NULL, it will simply ignored.
     */
    fun startAllWaitForDie() {
        pool.forEach { it?.start() }
        pool.forEach { it?.join() }
    }

    /**
     * Primitive locking
     */
    fun allFinished(): Boolean {
        pool.forEach { if (it?.state != Thread.State.TERMINATED) return false }
        return true
    }
}