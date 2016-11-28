package downloadimages.async.actor

import akka.actor.{ActorSystem, Props}
import akka.dispatch.ExecutionContexts.global
import akka.pattern.ask
import akka.util.Timeout
import downloadimages.async.actor.ReadFileActor.ReadFile
import downloadimages.core.{IOError, logFinish, logStart, withDownloadFolder}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object ActorDownloadImagesApp {
  private implicit val timeout = Timeout(1.hour)
  private implicit val executionContext = global()

  def main(args: Array[String]): Unit = {
    val downloadFolder = args(0)
    val maxDownloadActors = Integer.parseInt(args(1))
    val start = logStart(s">>> Start(${getClass.getSimpleName}, downloadFolder:'$downloadFolder', maxDownloadActors:$maxDownloadActors)")

    withDownloadFolder(downloadFolder) {
      val imageUrlFile = getClass.getResource("/images-to-download.txt").getFile

      val actorSystem = ActorSystem("ActorDownloadImagesApp")
      val fileReaderActor = actorSystem.actorOf(Props[ReadFileActor], "ReadFileActor")

      val result = (fileReaderActor ? ReadFile(imageUrlFile, downloadFolder, maxDownloadActors)).mapTo[Either[IOError,Integer]]

      val message = result.map {
        case Right(count) => s"$count images downloaded"
        case Left(error) => error.message
      }

      message.onComplete {
        case Success(msg) =>
          println(msg)
          terminate(start, actorSystem)
        case Failure(f) =>
          println(s"Error: ${f.getMessage}")
          terminate(start, actorSystem)
      }

      println("...While the Actors work, the App can go on doing other stuff...")
      println("...Like print these useless messages...")
    }
  }

  def terminate(startTime: Long, actorSystem: ActorSystem): Unit = {
    logFinish(startTime)
    actorSystem.terminate()
  }
}
