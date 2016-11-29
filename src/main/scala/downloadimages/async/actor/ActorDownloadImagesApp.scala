package downloadimages.async.actor

import akka.actor.{ActorSystem, Props}
import akka.dispatch.ExecutionContexts.global
import akka.pattern.ask
import akka.util.Timeout
import downloadimages.async.actor.ReadImageUrlFileActor.ReadImageUrlFile
import downloadimages.core._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object ActorDownloadImagesApp {
  private implicit val timeout = Timeout(1.hour)
  private implicit val executionContext = global()

  def main(args: Array[String]): Unit = {
    val downloadFolder = args(0)
    val nrOfDownloadActors = Integer.parseInt(args(1))

    println(startMessage(getClass, Map("downloadFolder" -> downloadFolder, "nrOfDownloadActors" -> nrOfDownloadActors)))
    val startTime = System.currentTimeMillis()

    withDownloadFolder(downloadFolder) { folder =>
      val actorSystem = ActorSystem("ActorDownloadImagesApp")
      val readFileActor = actorSystem.actorOf(Props[ReadImageUrlFileActor], "ReadImageUrlFileActor")
      val actorResponse = readFileActor ? ReadImageUrlFile(imageUrlFile(getClass), folder, nrOfDownloadActors)

      println("...While the Actors work, the App can go on doing other stuff...")
      println("...Like print these useless messages...")

      val fMessage = actorResponse.mapTo[Either[IOError,Integer]].map {
        case Right(count) => s"$count images downloaded"
        case Left(error) => error.message
      }

      fMessage.onComplete {
        case Success(msg) =>
          println(msg)
          println(terminate(actorSystem, startTime))
        case Failure(f) =>
          println(s"Error: ${f.getMessage}")
          println(terminate(actorSystem, startTime))
      }
    }
  }

  private def terminate(actorSystem: ActorSystem, startTime: Long): String = {
    actorSystem.terminate()
    finishMessage(System.currentTimeMillis() - startTime)
  }
}
