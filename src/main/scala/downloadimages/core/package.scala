package downloadimages

import java.io.{File, FileInputStream, FileOutputStream}
import java.net.{HttpURLConnection, URL}
import java.util.Scanner

import scala.annotation.tailrec

package object core {
  def foldFile[A](filename: String, zero: A)(fn: (A, String) => A): Either[IOError,A] = {
    try {
      val sc = new Scanner(new FileInputStream(filename), "UTF-8")
      try {
        foldFileLoop(sc, zero)(fn)
      }
      finally {
        sc.close()
      }
    }
    catch {
      case e: Exception => Left(IOError(e.getMessage))
    }
  }

  def createDownloadFolder(downloadFolder: String): Boolean = {
    val f = new File(downloadFolder)
    f.delete()
    f.mkdirs()
  }

  def downloadImage(imageUrl: String, outputDir: String): Either[IOError,Unit] = {
    try {
      val url = new URL(imageUrl)
      val urlFilename = url.getFile
      val outputFilename = outputDir + "/" + urlFilename.substring(urlFilename.lastIndexOf('/') + 1)

      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      conn.setRequestMethod("GET")

      val responseCode = conn.getResponseCode
      if (responseCode == 200) {
        val in = conn.getInputStream
        val out = new FileOutputStream(outputFilename)
        val buffer = new Array[Byte](1024)

        try {
          Stream
            .continually(in.read(buffer))
            .takeWhile(bytesRead => bytesRead != -1)
            .foreach(bytesRead => out.write(buffer, 0, bytesRead))

          Right()
        }
        finally {
          out.close()
          in.close()
        }
      }
      else {
        Left(IOError(s"Error downloading image; response code = $responseCode"))
      }
    }
    catch {
      case e: Exception => Left(IOError(e.getMessage))
    }
  }

  def logStart(message: String): Long = {
    println(message)
    System.currentTimeMillis()
  }

  def logFinish(startTime: Long): Unit = {
    println(s"<<< Finished. Total time was: ${System.currentTimeMillis() - startTime}ms")
  }


  @tailrec
  private def foldFileLoop[A](sc: Scanner, accumulator: A)(fn: (A, String) => A): Either[IOError,A] = {
    if (sc.ioException != null) {
      Left(IOError(sc.ioException.getMessage))
    }
    else if (!sc.hasNextLine) {
      Right(accumulator)
    }
    else {
      foldFileLoop(sc, fn(accumulator, sc.nextLine()))(fn)
    }
  }
}
