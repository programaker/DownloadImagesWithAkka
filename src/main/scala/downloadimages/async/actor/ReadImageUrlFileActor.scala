package downloadimages.async.actor

import java.io.File

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, Terminated}
import akka.routing.{Broadcast, RoundRobinPool}
import downloadimages.async.actor.DownloadImageActor.DownloadImage
import downloadimages.async.actor.ReadImageUrlFileActor._
import downloadimages.core.{IOError, foldFile}

class ReadImageUrlFileActor extends Actor with ActorLogging {
  //type Receive = PartialFunction[Any,Unit]
  override def receive: Receive = doReceive(State.empty)

  //The doReceive() method with its State argument, combined with context.become(),
  //eliminates the need for mutable state in the Actor
  //
  //This way, our Actor becomes purely functional!
  private def doReceive(state: State): Receive = {
    case ReadImageUrlFile(filename, downloadFolder, numberOfDownloadActors) =>
      doReadFile(state, filename, downloadFolder, numberOfDownloadActors)

    case DownloadCompleted(errorOrImageFile) =>
      doDownloadCompleted(state, errorOrImageFile)

    case Terminated(terminatedActor) =>
      doTerminate(state, terminatedActor)

    case x =>
      logUnknownMessage(log, x)
  }

  private def doReadFile(
    state: State,
    filename: String,
    downloadFolder: String,
    numberOfDownloadActors: Int
  ): Unit = {

    val newState = state.copy(
      //Store the sender of ReadImageUrlFile message (the application itself),
      //which is the entry point of the Actor's workflow,
      //to give it a response when all downloads have finished
      application = Some(sender),

      //Store the Router Actor that will distribute messages among
      //the download Actors in a pool
      router = createRouter(numberOfDownloadActors)
    )

    context.become(doReceive(newState))

    foldFile(filename,())(processLine(newState.router, downloadFolder, _, _)) match {
      //If an error happened while reading the url file, tell the bad news to the application
      case error: Left[_,_] => sender ! error
      case _ => ()
    }

    //At this point, we know the file was completely read and all Actors managed
    //by the Router already have their queue of Messages to process.
    //
    //So, we broadcast a PoisonPill message to the Router here to tell it
    //and its Actors to terminate after they finish all their Messages
    //
    //When this happens, a Terminated(actor) message will be sent and we will
    //catch it, because we've told the context to watch the Router in createRouter()
    newState.router.foreach{ _ ! Broadcast(PoisonPill) }

    println(">=> EOF")
  }

  private def processLine(router: Option[ActorRef], downloadFolder: String, acc: Unit, imageUrl: String): Unit = {
    if (!imageUrl.trim.isEmpty) {
      //Sending a message to the Router Actor will make it choose one Actor
      //in its pool (based on the strategy given in its creation) to perform it
      router.foreach{ _ ! DownloadImage(imageUrl, downloadFolder) }
    }
  }

  private def doDownloadCompleted(state: State, errorOrImageFile: Either[IOError,File]): Unit = {
    val newState = errorOrImageFile match {
      case Right(imageFile) =>
        println(s"< Image file: $imageFile")
        state.copy(imagesDownloaded = state.imagesDownloaded + 1)

      case Left(error) =>
        log.error(error.message)
        state
    }

    context.become(doReceive(newState))
  }

  private def doTerminate(state: State, terminatedActor: ActorRef): Unit = {
    //If terminatedActor is the Router Actor, it means that all download actors it
    //supervises have terminated and we can send the final response to the application
    state.router
      .filter{ _ == terminatedActor }
      .flatMap{ _ => state.application }
      .foreach{ application => application ! Right(state.imagesDownloaded) }
  }

  private def createRouter(numberOfDownloadActors: Int): Option[ActorRef] = {
    //Create a Router Actor that uses a Pool with Round Robin strategy
    //to distribute messages among its n Actors
    //
    //But BEWARE! The Pool will create ALL those n Actors at once! If you pass a sufficiently large
    //number, you might get an OutOfMemoryError (I've tested myself, passing Int.MaxValue to the Pool)
    val router = context.actorOf(RoundRobinPool(numberOfDownloadActors).props(Props[DownloadImageActor]))

    //Watch the Router Actor to intercept the Terminated message
    //
    //This is the signal that all Actors in the pool have finished their tasks
    //and we can send the result back to the application
    context.watch(router)

    Some(router)
  }
}

object ReadImageUrlFileActor {
  private case class State(application: Option[ActorRef], router: Option[ActorRef], imagesDownloaded: Int)
  private object State {
    def empty: State = State(None, None, 0)
  }


  /* === Messages === */
  //It's easy to know what messages an Actor can receive if they are
  //declared in it's Companion Object
  //
  //Public messages anyone can send to this Actor
  case class ReadImageUrlFile(filename: String, downloadFolder: String, numberOfDownloadActors: Int)
  case class DownloadCompleted(errorOrImageFile: Either[IOError,File])
}
