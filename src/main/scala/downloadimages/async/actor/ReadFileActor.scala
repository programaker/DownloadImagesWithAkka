package downloadimages.async.actor

import akka.actor.{Actor, ActorLogging, ActorRef}
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
    case ReadFile(filename, downloadFolder, maxDownloaders) =>
      //Store the sender of ReadFile message (the main sender)
      //to give it a response when all downloads have finished
      doReadFile(state.copy(mainSender = Some(sender)), filename, downloadFolder, maxDownloaders)

    case DownloadCompleted(result, downloadActorId) =>
      doDownloadCompleted(state, result, downloadActorId)

    case FinishSuccess(readFileSender) =>
      readFileSender ! Right(state.totalImagesDownloaded)

    case FinishError(readFileSender, error) =>
      readFileSender ! Left(error)

    case x =>
      logUnknownMessage(log, x)
  }

  private def doReadFile(state: State, filename: String, downloadFolder: String, maxDownloaders: Int): Unit = {
    foldFile(filename, initProcessLineStatus(state))(processLine(downloadFolder, maxDownloaders, _, _)) match {
      case Right((newState, _, _)) => context.become(doReceive(newState.copy(eof = true)))
      case Left(error) => self ! FinishError(sender, error)
    }
  }

  private def processLine(downloadFolder: String, maxDownloaders: Int, acc: ProcessLineStatus, imageUrl: String): ProcessLineStatus = {
    if (imageUrl.trim.isEmpty) {
      acc
    }
    else {
      val (state, lineIndex, downloadActorsById) = acc
      val downloadActorId = lineIndex % maxDownloaders

      val (downloadActor, newActorsById) = downloadActorsById.get(downloadActorId) match {
        case Some(actor) =>
          (actor, downloadActorsById)

        case None =>
          val newDownloadActor = context.actorOf(DownloadImageActor.props(downloadActorId), s"DownloadImageActor:$downloadActorId")
          (newDownloadActor, downloadActorsById + (downloadActorId -> newDownloadActor))
      }

      val newState = state.copy(downloadActorsFinished = state.downloadActorsFinished.updated(downloadActorId, false))
      downloadActor ! DownloadImage(imageUrl, downloadFolder)
      (newState, lineIndex + 1, newActorsById)
    }
  }

  private def doDownloadCompleted(state: State, result: Either[IOError,Unit], downloadActorId: Int): Unit = {
    val newState = result match {
      case Right(_) =>
        state.copy(
          totalImagesDownloaded = state.totalImagesDownloaded + 1,
          downloadActorsFinished = state.downloadActorsFinished.updated(downloadActorId, true))

      case Left(error) =>
        log.error(error.message)
        state.copy(downloadActorsFinished = state.downloadActorsFinished.updated(downloadActorId, true))
    }

    context.become(doReceive(newState))

    val allDownloadsFinished = newState.downloadActorsFinished.forall{ case (_, finished) => finished }
    if (allDownloadsFinished && newState.eof) {
      self ! FinishSuccess(newState.mainSender.get)
    }
  }
}

object ReadFileActor {
  private def initProcessLineStatus(state: State): ProcessLineStatus = (state, 0, Map[Int,ActorRef]())

  private type ActorsById = Map[Int,ActorRef]
  private type ActorsFinishedById = Map[Int,Boolean]
  private type ProcessLineStatus = (State,Int,ActorsById)

  private case class State(
    mainSender: Option[ActorRef],
    totalImagesDownloaded: Int,
    downloadActorsFinished: ActorsFinishedById,
    eof: Boolean
  )
  private object State {
    def empty: State = State(None, 0, Map(), eof = false)
  }

  //It's easy to know what messages an Actor can receive if they are
  //declared in it's Companion Object

  //Public messages anyone can send to this Actor
  case class ReadFile(filename: String, downloadFolder: String, maxDownloaders: Int)
  case class DownloadCompleted(result: Either[IOError,Unit], downloadActorId: Int)

  //Private messages only this Actor knows and sends to itself
  //They are like thoughts, if you think about it...
  private case class FinishSuccess(readFileSender: ActorRef)
  private case class FinishError(readFileSender: ActorRef, error: IOError)
}
