package downloadimages.async.actor

import akka.actor.{Actor, ActorLogging, Props}
import downloadimages.async.actor.DownloadImageActor.DownloadImage
import downloadimages.async.actor.ReadFileActor.DownloadCompleted
import downloadimages.core.downloadImage

class DownloadImageActor(id: Int) extends Actor with ActorLogging {
  override def receive: Receive = {
    case DownloadImage(imageUrl, downloadFolder) => doDownloadImage(imageUrl, downloadFolder, id)
    case x => logUnknownMessage(log, x)
  }

  private def doDownloadImage(imageUrl: String, downloadFolder: String, id: Int): Unit = {
    println(s". ${self.path.name} is downloading image '$imageUrl'")
    sender ! DownloadCompleted(downloadImage(imageUrl, downloadFolder), id)
  }
}

object DownloadImageActor {
  def props(id: Int): Props = Props(new DownloadImageActor(id))
  case class DownloadImage(imageUrl: String, downloadFolder: String)
}
