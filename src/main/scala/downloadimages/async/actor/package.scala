package downloadimages.async

import akka.event.LoggingAdapter

package object actor {
  def logUnknownMessage(log: LoggingAdapter, unknownMessage: Any): Unit = {
    log.error("Unknown message: '{}'", unknownMessage)
  }
}
