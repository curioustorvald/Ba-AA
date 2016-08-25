package net.torvald.aa.demoplayer

import net.torvald.aa.AAFrame
import net.torvald.aa.ImageToAA
import org.newdawn.slick.Image
import java.io.File

/**
 * Created by minjaesong on 16-08-11.
 */
class FrameStreamer(
        private val ref: String,
        private val w: Int,
        private val h: Int,
        private val prefix: String) : FrameFetcher {


    private val framesList: Array<File>
    override val frameCount: Int

    init {
        val f = File(ref)
        framesList = f.listFiles { dir, name ->
            name.startsWith(prefix) && (name.endsWith(".jpg") || name.endsWith(".bmp")
                    || name.endsWith("" + ".png") || name.endsWith("" + ".tga"))
        }

        frameCount = framesList.size
    }

    override fun init() {
    }

    override fun setFrameBuffer(framebuffer: AAFrame, frameNo: Int) {
        if (frameNo < frameCount) {
            val frameInput = Image(framesList[frameNo].absolutePath)
            BaAA.imageToAA.toAscii(frameInput, framebuffer)
        }
    }

    override fun preJob(framebuffer: AAFrame) {
    }

    override fun preJobDone(): Boolean = true
}