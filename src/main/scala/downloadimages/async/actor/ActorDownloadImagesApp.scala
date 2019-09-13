package downloadimages.async.actor

import akka.actor.{ActorSystem, Props}
import akka.dispatch.ExecutionContexts.global
import akka.pattern.ask
import akka.util.Timeout
import downloadimages.async.actor.ReadImageUrlFileActor.ReadImageUrlFile
import downloadimages.core._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object ActorDownloadImagesApp {
  private implicit val timeout: Timeout = Timeout(1.hour)
  private implicit val executionContext: ExecutionContextExecutor = global()

  def main(args: Array[String]): Unit = {
    val downloadFolder = args(0)
    val numberOfDownloadActors = Integer.parseInt(args(1))

    println(startMessage(getClass, Map(
      "downloadFolder" -> downloadFolder,
      "numberOfDownloadActors" -> numberOfDownloadActors
    )))

    val startTime = System.currentTimeMillis()

    withDownloadFolder(downloadFolder) { folder =>
      val actorSystem = ActorSystem("ActorDownloadImagesApp")
      val readFileActor = actorSystem.actorOf(Props[ReadImageUrlFileActor], "ReadImageUrlFileActor")

      val actorResponse =
        readFileActor ? ReadImageUrlFile(ImagesToDownloadFile, folder, numberOfDownloadActors)

      println("...While the Actors work, the App can go on doing other stuff...")
      println("...Like print these useless messages...")

      val errorOrSummary = actorResponse.mapTo[Either[IOError,Integer]].map {
        case Right(count) => s"$count images downloaded"
        case Left(error) => error.message
      }

      errorOrSummary.onComplete { tryMessage =>
        println(tryMessage match {
          case Success(msg) => msg
          case Failure(f) => s"Error: ${f.getMessage}"
        })

        println(terminate(actorSystem, startTime))
      }
    }
  }

  private def terminate(actorSystem: ActorSystem, startTime: Long): String = {
    actorSystem.terminate()
    finishMessage(System.currentTimeMillis() - startTime)
  }
}
