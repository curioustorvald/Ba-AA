package net.torvald.aa.demoplayer

import net.torvald.aa.AAFrame


/**
 * Created by minjaesong on 16-08-11.
 */
interface FrameFetcher {
    val frameCount: Int

    fun init()
    fun setFrameBuffer(framebuffer: AAFrame, frameNo: Int)
    fun preJob(framebuffer: AAFrame)
    fun preJobDone(): Boolean
}