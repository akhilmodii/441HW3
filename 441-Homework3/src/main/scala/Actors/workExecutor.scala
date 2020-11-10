package Actors

import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, Props, ActorRef}
import com.typesafe.config.{Config, ConfigFactory}
import scala.concurrent.duration._

object workExecutor {
  def props = Props(new workExecutor)
  case class doWork(n: Int, actorRef: ActorRef)
  case class complete(result: String, job: Int, actorRef: ActorRef)
  val conf: Config = ConfigFactory.load("application.conf")
}

class workExecutor extends Actor {
  import workExecutor._
  import context.dispatcher

  def receive: PartialFunction[Any, Unit] = {
    case doWork(n: Int, actorRef: ActorRef) =>
      val addn = n + n
      val result = s"$n+$n=$addn"

      val randProcessTime = ThreadLocalRandom.current().nextInt(
        conf.getInt("minProcessTime"),
        conf.getInt("maxProcessTime")).seconds
      context.system.scheduler.scheduleOnce(randProcessTime, sender(), complete(result, n, actorRef))
  }

}