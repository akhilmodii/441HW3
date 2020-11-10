package Actors

import akka.actor.{Actor, ActorLogging, Props}


class frontEnd(ID: String, alternateWork: Boolean) extends Actor with ActorLogging {
  def receive: Receive = {

  }
}

object Frontend {
  def props(ID: String, alternateWork: Boolean): Props =
    Props(new frontEnd(ID.toString, alternateWork))

}