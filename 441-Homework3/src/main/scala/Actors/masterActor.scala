package Actors

import Actors.masterActor.{Busy, Idle, cleaningTick, workerState}
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}
import akka.cluster.pubsub.DistributedPubSub
import Actors.workState

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.{Deadline, DurationLong, FiniteDuration}



class masterActor(workTimeout: FiniteDuration) extends Actor with Timers with ActorLogging {

  val considerWorkerDead: FiniteDuration =
    context.system.settings.config.getDuration("distributed-workers.consider-worker-dead-after").getSeconds.seconds

  def newStaleWorker(): Deadline = considerWorkerDead.fromNow
  timers.startPeriodicTimer("cleaning", cleaningTick, workTimeout/2)

  val mediatorActor: ActorRef = DistributedPubSub(context.system).mediator

  private var workers = Map[String, workerState]()
  private var state = workState.empty
  private var chainforward = mutable.HashMap[(String, String), ListBuffer[String]]()




  def receive: Receive = {
    case protocol.registerWorker(workerID) =>
      if(workers.contains(workerID)) {
        workers = workers + (workerID -> workers(workerID).copy(actorRef = sender(), staleWorker = newStaleWorker()))

      }

      else{
        log.debug(f"worker-$workerID registered successfully")
        val initWorkerState = workerState(actorRef = sender(), status = Idle,  staleWorker = newStaleWorker())
        workers = workers + (workerID -> initWorkerState)

        if (state.hasWork){
          sender() ! protocol.workReady
        }
      }


    case protocol.requestWork(workerID) =>
      workers.get(workerID) match {
        case Some(workerState(_, Busy(workID, _), _)) =>
          log.debug(f"Worker-$workerID was busy")
          val fail = WorkerFailed(workID)
          workState = workState.update(fail)

      }
  }
}

object masterActor {

  def props(workTimeout: FiniteDuration): Props =
    Props(new masterActor(workTimeout))

  case class ack(workID: String)

  private sealed trait workerStatus
  private case object Idle extends workerStatus
  private case object cleaningTick
  private case class Busy(workID: String, deadline: Deadline) extends workerStatus
  private case class workerState(actorRef: ActorRef, status: workerStatus, staleWorker: Deadline)

}

