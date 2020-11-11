package Actors

import Actors.masterActor.{Busy, Idle, cleaningTick, workerState}
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}
import akka.cluster.pubsub.DistributedPubSub
import Actors.workState
import Actors.workState.{workStart, workerFailed}
import akka.cluster.pubsub.DistributedPubSubMediator.Send
import Actors.Work

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.{Deadline, DurationLong, FiniteDuration}


class masterActor(workTimeout: FiniteDuration) extends Actor with Timers with ActorLogging {

  import workState._

  val considerWorkerDead: FiniteDuration =
    context.system.settings.config.getDuration("distributed-workers.consider-worker-dead-after").getSeconds.seconds

  def newStaleWorker(): Deadline = considerWorkerDead.fromNow
  timers.startPeriodicTimer("cleaning", cleaningTick, workTimeout/2)

  val mediatorActor: ActorRef = DistributedPubSub(context.system).mediator

  private var workers = Map[String, workerState]()
  private var state = workState.empty
  private var chainforward = mutable.HashMap[(String, String), ListBuffer[String]]()


  Await.result(context.system.terminate(), 5.seconds)



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

    case protocol.unregisterWorker(workerID) =>
      workers.get(workerID) match {
        case Some(workerState(_, Busy(workID, _), _)) =>
          log.debug(f"wworker-$workerID unregistered")
          val x = workerFailed(workID)
          state = state.update(x)
          notifyWorkers()
        case Some(_) =>
          log.debug(f"Worker-$workerID unregistered")
        case _ =>
      }
      workers = workers - workerID

    case protocol.forwardWork(workerID, workID, forward) =>
      val k = (workID, workIDHash(workID))
      var v = chainforward(k)
      workers.get(workerID) match {
        case Some(workerState @ workerState(_, _, _)) =>
          if(!forward) {
            state = state.update(workStart(workID))
            log.debug(f"workID: $workID, workIDHash: ${workIDHash(workID)} to the worker-$workerID")
            val newWorkerState = workerState.copy(
              status = Busy(workID, Deadline.now + workTimeout),
              staleWorker = newStaleWorker()
            )
            workers = workers + (workerID -> newWorkerState)
            log.info(f"Forwarded chain for workIDHash: ${workIDHash(workID)} is ${chainforward(k)}")
          }

          else if (forward && state.allWork.exists(_._1.workID == workID)) {
            v += workerID
            log.debug(f"Forwarded workID: $workID, workIDHash: ${workIDHash(workID)} to the worker-$workerID")
            chainforward(k)
            workerState.actorRef ! state.allWork.filter(_._1.workID==workID).head._1
            state = state.update(workForward(workID))
          }
      }


    case protocol.requestWork(workerID) =>
      if(state.hasWork) {
        workers.get(workerID) match {
          case Some(workst @ workerState(_, Idle, _)) =>
            val freshWork = state.allWork.filter(_._2 == false).head._1
            if(!chainforward.exists(_._1 == (freshWork.workID, workIDHash(freshWork.workID)))){
              log.debug(s"New Fresh Work: $freshWork sent to the worker-$workerID")
              chainforward((freshWork.workID, workIDHash(freshWork.workID))) = ListBuffer(workerID)
              sender() ! freshWork
            }

            else{
              log.debug(s"No new work for the worker-$workerID")
            }

          case _ =>
        }
      }

    case protocol.workDone(workerID, workID, result, job, actorRef) =>
      if(state.isDone(workID)) {
        sender() ! protocol.act(workID)
      }

      else if(!state.isInProgress(workID)) {
        log.debug(f"workID: $workID, workIDHash: ${workIDHash(workID)} reported as done by the worker-$workerID")
      }

      else{
        log.debug(f"workID: $workID, workIDHash: ${workIDHash(workID)} reported as done by the worker-$workerID")
        makeWorkerIdle(workerID, workID)
        val x = workComplete(workID, result)
        state = state.update(x)
        mediatorActor ! Send(s"/user/${actorRef.path.name}", workResult(workID, job, workIDHash(workID), result, sender), localAffinity = true)
        sender ! protocol.act(workID)
      }


    case protocol.failed(workerID, workID) =>
      if (state.isInProgress(workID)) {
        log.debug(f"workID: $workID, workIDHash: ${workIDHash(workID)} reported as failed by the worker-$workerID")
        makeWorkerIdle(workerID, workID)
        val x = workerFailed(workID)
        state = state.update(x)
        notifyWorkers()
      }

    case work: Work =>
      if (state.isAccepted(work.workID)) {
        sender() ! masterActor.ack(work.workID)
      }

      else{
        log.debug(f"workID: ${work.workID} accepted successfully")
        val x = workAccept(work)
        sender() ! masterActor.ack(work.workID)
        state = state.update(x)
        notifyWorkers()
      }

    case cleaningTick =>
      workers.foreach {
        case(workerID, workerState(_, Busy(workID, timeout), _)) if timeout.isOverdue() =>
          log.debug(f"workID: $workID, workIDHash: ${workIDHash(workID)} has been timed out.")
          workers = workers - workerID
          val x = workTimed(workID)
          state = state.update(x)
          notifyWorkers()

        case (workerID, workerState(_, Idle, lastHeard)) if lastHeard.isOverdue() =>
          log.debug(f"Too long since last heard from the worker-$workerID")
          workers = workers - workerID

        case _ =>

      }

  }

  def makeWorkerIdle(workerID: String, workID: String): Unit =
    workers.get(workerID) match {
      case Some(workerSt @ workerState(_, Busy(`workID`, _), _)) =>
        val newState = workerSt.copy(
          status = Idle,
          staleWorker = newStaleWorker()
        )
        workers = workers + (workerID -> newState)
      case _ =>
    }

  def workIDHash(workID: String): String = {
    (workID.toArray.foldLeft(0)(_ + _.toInt) % workers.size).toString
  }

  def notifyWorkers(): Unit =
    if (state.hasWork) {
      workers.foreach {
        case(_, workerState(ref, Idle, _)) => ref ! protocol.workReady
        case _ =>
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

