package downloadimages.sync

import downloadimages.core._

object SyncDownloadImagesApp {
  def main(args: Array[String]): Unit = {
    val downloadFolder = args(0)
    val start = logStart(s">>> Start(downloadFolder:'$downloadFolder')")

    if (createDownloadFolder(downloadFolder)) {
      val imageUrlFile = getClass.getResource("/images-to-download.txt").getFile

      val imageCount = foldFile(imageUrlFile, 0) {(count, imageUrl) =>
        println(s". Downloading image '$imageUrl'")

        downloadImage(imageUrl, downloadFolder) match {
          case Right(_) =>
            count + 1
          case Left(error) =>
            println(s"- Error downloading '$imageUrl' => '${error.message}'")
            count
        }
      }

      println(imageCount match {
        case Right(count) => s"- $count images downloaded"
        case Left(error) => s"- Could not read file => '${error.message}'"
      })
    }
    else {
      println(s"- Could not create download folder: '$downloadFolder'")
    }

    logFinish(start)
  }
}
