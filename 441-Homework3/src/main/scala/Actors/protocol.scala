package Actors

import akka.actor.ActorRef

object protocol {

  // These are the messages from the actors
  case class registerWorker(workerID: String)

  case class unregisterWorker(workerID: String)

  case class requestWork(workerID: String)

  case class forwardWork(workerID: String, workID: String, forward: Boolean)

  case class failed(workerID: String, workId: String) //, job: Int, actorRef: ActorRef)

  case class workDone(workerID: String, workID: String, result: Any, job: Int, actorRef: ActorRef)

  // This are the messages to the actors
  case object workReady
  case class act(id: String)
}

