package downloadimages

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Scanner

import scala.annotation.tailrec

package object core {
  def foldFile[A](filename: String, zero: A)(fn: (A, String) => A): Either[IOError,A] = {
    try {
      val scanner = new Scanner(new FileInputStream(filename), "UTF-8")
      try {
        foldFileLoop(scanner, zero)(fn)
      }
      finally {
        scanner.close()
      }
    }
    catch {
      case e: Exception => Left(IOError(s"Error processing file: '$filename' => '${e.getMessage}'"))
    }
  }

  def withDownloadFolder[A](downloadFolder: String)(fn: String => A): Either[IOError,A] = {
    val df = new File(downloadFolder)

    val deleted = if (df.exists()) {
      val emptyDir = df.listFiles().forall{ file => file.delete() }
      emptyDir && df.delete()
    }
    else {
      true
    }

    if (deleted && df.mkdirs()) {
      Right(fn(downloadFolder))
    }
    else {
      Left(IOError(s"Could not create download folder: '$downloadFolder'"))
    }
  }

  def downloadImage(imageUrl: String, outputDir: String): Either[IOError,File] = {
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
        val buffer = new Array[Byte](2048)

        try {
          LazyList
            .continually(in.read(buffer))
            .takeWhile{ bytesRead => bytesRead != -1 }
            .foreach{ bytesRead => out.write(buffer, 0, bytesRead) }

          Right(new File(outputFilename))
        }
        finally {
          out.close()
          in.close()
        }
      }
      else {
        Left(IOError(s"Error downloading image: '$imageUrl' => response code: $responseCode"))
      }
    }
    catch {
      case e: Exception => Left(IOError(s"Error downloading image: '$imageUrl' => '${e.getMessage}'"))
    }
  }

  def imageUrlFile(clientClass: Class[_]): String = {
    clientClass.getResource("/images-to-download.txt").getFile
  }

  def startMessage(clientClass: Class[_], args: Map[String,_]): String = {
    s">>> Start(${clientClass.getSimpleName}, args:$args)"
  }

  def finishMessage(endTime: Long): String = {
    s"<<< Finished. Total time was: ${endTime}ms"
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
