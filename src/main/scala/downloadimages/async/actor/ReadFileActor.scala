package downloadimages.async.actor

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, Terminated}
import akka.routing.{Broadcast, RoundRobinPool}
import downloadimages.async.actor.DownloadImageActor.DownloadImage
import downloadimages.async.actor.ReadFileActor._
import downloadimages.core.{IOError, foldFile}

class ReadFileActor extends Actor with ActorLogging {
  //type Receive = PartialFunction[Any,Unit]
  override def receive: Receive = doReceive(State.empty)

  //The doReceive() method with its State argument, combined with context.become(),
  //eliminates the need for mutable state in the Actor
  //
  //This way, our Actor becomes purely functional!
  private def doReceive(state: State): Receive = {
    case ReadFile(filename, downloadFolder, maxDownloadActors) => doReadFile(state, filename, downloadFolder, maxDownloadActors)
    case DownloadCompleted(result) => doDownloadCompleted(state, result)
    case Terminated(terminatedActor) => doTerminate(state, terminatedActor)
    case FinishError(readFileSender, error) => readFileSender ! Left(error)
    case x => logUnknownMessage(log, x)
  }

  private def doReadFile(state: State, filename: String, downloadFolder: String, maxDownloadActors: Int): Unit = {
    val newState = state.copy(
      //Store the sender of ReadFile message (the application itself),
      //which is the entry point of the Actor's workflow,
      //to give it a response when all downloads have finished
      application = Some(sender),

      //Store the Router Actor that will distribute messages among
      //the download Actors in a pool
      router = createRouter(maxDownloadActors))

    context.become(doReceive(newState))

    foldFile(filename, ())(processLine(newState.router, downloadFolder, _, _)) match {
      case Right(_) =>
        //At this point, we know the file was completely read and all Actors managed
        //by the Router already have their queue of Messages to process.
        //
        //So, we broadcast a PoisonPill message to the Router here to tell it
        //and its Actors to terminate after they finish all their Messages
        //
        //When this happens, a Terminated(actor) message will be sent and we will
        //catch it, because we've told the context to watch the Router
        newState.router.foreach{ r => r ! Broadcast(PoisonPill) }

      case Left(error) =>
        self ! FinishError(sender, error)
    }

    println(">=> EOF")
  }

  private def processLine(router: Option[ActorRef], downloadFolder: String, acc: Unit, imageUrl: String): Unit = {
    if (!imageUrl.trim.isEmpty) {
      //Sending a message to the Router Actor will make it choose one Actor
      //in its pool (based on the strategy given in its creation) to perform it
      router.foreach{ r => r ! DownloadImage(imageUrl, downloadFolder) }
    }
  }

  private def doDownloadCompleted(state: State, result: Either[IOError,Unit]): Unit = {
    val upImagesDownloaded = result match {
      case Right(_) =>
        state.imagesDownloaded + 1

      case Left(error) =>
        log.error(error.message)
        state.imagesDownloaded
    }

    context.become(doReceive(state.copy(imagesDownloaded = upImagesDownloaded)))
  }

  private def doTerminate(state: State, terminatedActor: ActorRef): Unit = {
    //If terminatedActor is the Router Actor, it means that all download actors it
    //supervises have terminated and we can send the final response to the application
    state.router
      .filter(r => r == terminatedActor)
      .flatMap(_ => state.application)
      .foreach(application => application ! Right(state.imagesDownloaded))
  }

  private def createRouter(maxDownloadActors: Int): Option[ActorRef] = {
    //Create a Router Actor that uses a Pool with Round Robin strategy
    //to distribute messages among its $maxDownloadActors Actors
    val router = context.actorOf(RoundRobinPool(maxDownloadActors).props(Props[DownloadImageActor]))

    //Watch the Router Actor to intercept the Terminated message
    //
    //This is the signal that all Actors in the pool have finished their tasks
    //and we can send the result back to the application
    context.watch(router)

    Some(router)
  }
}

object ReadFileActor {
  private case class State(application: Option[ActorRef], router: Option[ActorRef], imagesDownloaded: Int)
  private object State {
    def empty: State = State(None, None, 0)
  }


  /* === Messages === */
  //It's easy to know what messages an Actor can receive if they are
  //declared in it's Companion Object

  //Public messages anyone can send to this Actor
  case class ReadFile(filename: String, downloadFolder: String, maxDownloadActors: Int)
  case class DownloadCompleted(result: Either[IOError,Unit])

  //Private messages only this Actor knows and sends to itself
  //They are like thoughts, if you think about it...
  private case class FinishSuccess(readFileSender: ActorRef)
  private case class FinishError(readFileSender: ActorRef, error: IOError)
}
