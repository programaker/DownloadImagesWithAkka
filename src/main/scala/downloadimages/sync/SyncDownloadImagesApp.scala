package downloadimages.sync

import downloadimages.core._

object SyncDownloadImagesApp {
  def main(args: Array[String]): Unit = {
    val downloadFolder = args(0)
    val start = logStart(s">>> Start(downloadFolder:'$downloadFolder')")

    withDownloadFolder(downloadFolder) {
      val imageUrlFile = getClass.getResource("/images-to-download.txt").getFile

      val imageCount = foldFile(imageUrlFile, 0) {(count, imageUrl) =>
        if (imageUrl.trim.isEmpty) {
          count
        }
        else {
          println(s". Downloading image '$imageUrl'")

          downloadImage(imageUrl, downloadFolder) match {
            case Right(_) =>
              count + 1
            case Left(error) =>
              println(error.message)
              count
          }
        }
      }

      println(imageCount match {
        case Right(count) => s"$count images downloaded"
        case Left(error) => error.message
      })
    }

    logFinish(start)
  }
}
