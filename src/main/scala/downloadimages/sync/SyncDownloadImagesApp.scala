package downloadimages.sync

import downloadimages.core.{downloadImage, imageUrlFile, withDownloadFolder, startMessage, finishMessage, foldFile}

object SyncDownloadImagesApp {
  def main(args: Array[String]): Unit = {
    val downloadFolder = args(0)

    println(startMessage(getClass, Map("downloadFolder" -> downloadFolder)))
    val startTime = System.currentTimeMillis()

    val errorOrSummaryMessage = withDownloadFolder(downloadFolder) { folder =>
      foldFile(imageUrlFile(getClass), 0)(processLine(folder, _, _)) match {
        case Right(count) => s"$count images downloaded"
        case Left(error) => error.message
      }
    }

    val msg = errorOrSummaryMessage match {
      case Right(message) => message
      case Left(error) => error.message
    }

    println(msg)
    println(finishMessage(System.currentTimeMillis() - startTime))
  }

  private def processLine(folder: String, count: Int, imageUrl: String): Int = {
    if (imageUrl.trim.isEmpty) {
      count
    }
    else {
      println(s"> Downloading image '$imageUrl'")

      downloadImage(imageUrl, folder) match {
        case Right(imageFile) =>
          println(s"< Image file: $imageFile")
          count + 1
        case Left(error) =>
          println(error.message)
          count
      }
    }
  }
}
