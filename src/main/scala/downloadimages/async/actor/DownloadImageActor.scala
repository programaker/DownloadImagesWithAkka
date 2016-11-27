package downloadimages.async.actor

import akka.actor.{Actor, ActorLogging}
import downloadimages.async.actor.DownloadImageActor.DownloadImage
import downloadimages.async.actor.ReadFileActor.DownloadCompleted
import downloadimages.core.downloadImage

class DownloadImageActor extends Actor with ActorLogging {
  override def receive: Receive = {
    case DownloadImage(imageUrl, downloadFolder) => doDownloadImage(imageUrl, downloadFolder)
    case x => logUnknownMessage(log, x)
  }

  private def doDownloadImage(imageUrl: String, downloadFolder: String): Unit = {
    println(s". ${self.path.name} is downloading image '$imageUrl'")
    sender ! DownloadCompleted(downloadImage(imageUrl, downloadFolder))
  }
}

object DownloadImageActor {
  case class DownloadImage(imageUrl: String, downloadFolder: String)
}
