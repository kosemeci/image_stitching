
package com.yariksoffice.javaopencvplaygroung

import android.net.Uri
import io.reactivex.Single
import org.bytedeco.javacpp.opencv_core.*
import org.bytedeco.javacpp.opencv_imgproc.*
import org.bytedeco.javacpp.opencv_stitching.Stitcher
import org.bytedeco.javacpp.opencv_stitching.Stitcher.ERR_CAMERA_PARAMS_ADJUST_FAIL
import org.bytedeco.javacpp.opencv_stitching.Stitcher.ERR_HOMOGRAPHY_EST_FAIL
import org.bytedeco.javacpp.opencv_stitching.Stitcher.ERR_NEED_MORE_IMGS
import java.io.File
import java.lang.Exception
import org.bytedeco.javacpp.opencv_imgcodecs.imread
import org.bytedeco.javacpp.opencv_imgcodecs.imwrite

class StitcherInput(val uris: List<Uri>, val stitchMode: Int)

sealed class StitcherOutput {
    class Success(val file: File) : StitcherOutput()
    class Failure(val e: Exception) : StitcherOutput()
}

class ImageStitcher(private val fileUtil: FileUtil) {

    fun stitchImages(input: StitcherInput): Single<StitcherOutput> {
        return Single.fromCallable {
            val files = fileUtil.urisToFiles(input.uris)
            val vector = filesToMatVector(files)
            stitch(vector, input.stitchMode)
        }
    }

    private fun stitch(vector: MatVector, stitchMode: Int): StitcherOutput {
        val sharpenedVector = MatVector(vector.size().toLong())
        for (i in 0L until vector.size().toLong()) {
            val sharpened = increaseSharpness(vector.get(i))
            if (sharpened.channels() == 1) {
                cvtColor(sharpened, sharpened, COLOR_GRAY2BGR)  // Gri tonlamalı ise renkli hale dönüştür
            }
            sharpenedVector.put(i, sharpened)
        }
        val result = Mat()
        val stitcher = Stitcher.create(stitchMode)
        val status = stitcher.stitch(sharpenedVector, result)
        fileUtil.cleanUpWorkingDirectory()
        return if (status == Stitcher.OK) {
            val resultFile = fileUtil.createResultFile()
            imwrite(resultFile.absolutePath, result)
            StitcherOutput.Success(resultFile)
        } else {
            val e = RuntimeException("Can't stitch images: " + getStatusDescription(status))
            StitcherOutput.Failure(e)
        }
    }

    @Suppress("SpellCheckingInspection")
    private fun getStatusDescription(status: Int): String {
        return when (status) {
            ERR_NEED_MORE_IMGS -> "ERR_NEED_MORE_IMGS"
            ERR_HOMOGRAPHY_EST_FAIL -> "ERR_HOMOGRAPHY_EST_FAIL"
            ERR_CAMERA_PARAMS_ADJUST_FAIL -> "ERR_CAMERA_PARAMS_ADJUST_FAIL"
            else -> "UNKNOWN"
        }
    }

    private fun filesToMatVector(files: List<File>): MatVector {
        val images = MatVector(files.size.toLong())
        for (i in files.indices) {
            images.put(i.toLong(), imread(files[i].absolutePath))
        }
        return images
    }

    private fun increaseSharpness(inputMat: Mat): Mat {

        val blurred = Mat()
        GaussianBlur(inputMat, blurred, Size(0, 0), 1.0)

        val unsharpMask = Mat()
        subtract(inputMat, blurred, unsharpMask)

        val sharpened = Mat()
        addWeighted(inputMat, 1.8, unsharpMask, -1.3, 0.0, sharpened)

        return sharpened
    }
}