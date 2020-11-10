package Actors

import akka.actor.ActorRef

case class Work(workID: String, job: Any, actorRef: ActorRef)
case class result(workID: String, job: Any, workIDHash: String, result: Any, actorRef: ActorRef)