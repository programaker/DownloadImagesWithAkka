package downloadimages.async.actor

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import downloadimages.async.actor.DownloadImageActor.DownloadImage
import downloadimages.async.actor.ReadFileActor._
import downloadimages.core.{IOError, foldFile}

class ReadFileActor extends Actor with ActorLogging {
  //type Receive = PartialFunction[Any,Unit]
  override def receive: Receive = doReceive(State.empty)

  //The doReceive() method with its totalWords argument, combined with context.become(), eliminates
  //the need for mutable state in the Actor to accumulate counted words
  //
  //This way, our Actor becomes purely functional!
  private def doReceive(state: State): Receive = {
    case ReadFile(filename, downloadFolder) =>
      //Store the sender of ReadFile message (the main sender, the application itself)
      //to give it a response when all downloads have finished
      doReadFile(state.copy(mainSender = Some(sender)), filename, downloadFolder)

    case DownloadCompleted(result) =>
      doDownloadCompleted(state, result)

    case FinishSuccess(readFileSender) =>
      readFileSender ! Right(state.imagesDownloaded)

    case FinishError(readFileSender, error) =>
      readFileSender ! Left(error)

    case x =>
      logUnknownMessage(log, x)
  }

  private def doReadFile(state: State, filename: String, downloadFolder: String): Unit = {
    foldFile(filename, initProcessLineStatus(state))(processLine(downloadFolder, _, _)) match {
      case Right((_, downloadActorsCreated)) =>
        context.become(doReceive(state.copy(downloadActorsCreated = downloadActorsCreated)))

      case Left(error) =>
        self ! FinishError(sender, error)
    }

    println(">=> EOF")
  }

  private def processLine(downloadFolder: String, pls: ProcessLineStatus, imageUrl: String): ProcessLineStatus = {
    if (imageUrl.trim.isEmpty) {
      pls
    }
    else {
      val (lineNumber, downloadActorsCreated) = pls
      val downloadActor = context.actorOf(Props[DownloadImageActor], s"DownloadImageActor:$lineNumber")
      downloadActor ! DownloadImage(imageUrl, downloadFolder)
      (lineNumber + 1, downloadActorsCreated + 1)
    }
  }

  private def doDownloadCompleted(state: State, result: Either[IOError,Unit]): Unit = {
    val (upImagesDownloaded, upDownloadActorsFinished) = result match {
      case Right(_) =>
        (state.imagesDownloaded + 1, state.downloadActorsFinished + 1)

      case Left(error) =>
        log.error(error.message)
        (state.imagesDownloaded, state.downloadActorsFinished + 1)
    }

    //Kill the download actor that just completed it's job
    sender ! PoisonPill

    val newState = state.copy(imagesDownloaded = upImagesDownloaded, downloadActorsFinished = upDownloadActorsFinished)
    context.become(doReceive(newState))

    if (newState.downloadActorsCreated == newState.downloadActorsFinished) {
      self ! FinishSuccess(newState.mainSender.get)
    }
  }
}

object ReadFileActor {
  private def initProcessLineStatus(state: State): ProcessLineStatus = (1, 0)

  private case class State(
    mainSender: Option[ActorRef],
    imagesDownloaded: Int,
    downloadActorsCreated: Int,
    downloadActorsFinished: Int
  )
  private object State {
    def empty: State = State(None, 0, 0, 0)
  }

  private type ProcessLineStatus = (LineNumber,DownloadActorsCreated)
  private type LineNumber = Int
  private type DownloadActorsCreated = Int


  /* === Messages === */
  //It's easy to know what messages an Actor can receive if they are
  //declared in it's Companion Object

  //Public messages anyone can send to this Actor
  case class ReadFile(filename: String, downloadFolder: String)
  case class DownloadCompleted(result: Either[IOError,Unit])

  //Private messages only this Actor knows and sends to itself
  //They are like thoughts, if you think about it...
  private case class FinishSuccess(readFileSender: ActorRef)
  private case class FinishError(readFileSender: ActorRef, error: IOError)
}
