package Actors

import Actors.workState.{workAccept, workComplete, workDomainEve, workForward, workStart, workTimed, workerFailed}

import scala.collection.immutable.Queue



case class workState private (
  private val pendingWork: Queue[(Work, Boolean)],
  private val workInProgress: Map[String, Work],
  private val acceptedWork: Set[String],
  private val doneWork: Set[String]) {


  def hasWork: Boolean = pendingWork.exists(_._2 == false)
  def allWork: Queue[(Work, Boolean)] = pendingWork
  def isAccepted(workID: String): Boolean = acceptedWork.contains(workID)
  def isInProgress(workID: String): Boolean = workInProgress.contains(workID)
  def isDone(workID: String): Boolean = doneWork.contains(workID)


  def update(eve: workDomainEve): workState = eve match {
    case workAccept(work) =>
      copy(
        pendingWork = pendingWork enqueue (work, false),
        acceptedWork = acceptedWork + work.workID
      )

    case workStart(workID) =>
      val ((work, _), rest) = pendingWork.filter(_._1.workID==workID).dequeue
      require(workID == work.workID, s"WorkStarted. Expected workID $workID == ${work.workID}")
      copy (
        pendingWork = rest,
        workInProgress = workInProgress + (workID -> work)
      )

    case workForward(workID) =>
      val ((work, _), rest) = pendingWork.filter(_._1.workID==workID).dequeue
      copy(pendingWork = rest enqueue(work, true))

    case workComplete(workID, result) =>
      copy(
        workInProgress = workInProgress - workID,
        doneWork = doneWork + workID
      )

    case workerFailed(workID) =>
      copy(
        pendingWork = pendingWork enqueue (workInProgress(workID), false),
        workInProgress = workInProgress - workID
      )

    case workTimed(workID) =>
      copy(
        pendingWork = pendingWork enqueue (workInProgress(workID), false),
        workInProgress = workInProgress - workID
      )
  }
}


object workState {

  def empty: workState = workState(pendingWork = Queue.empty, workInProgress = Map.empty, acceptedWork = Set.empty, doneWork = Set.empty)

  trait workDomainEve
  case class workAccept(work: Work) extends workDomainEve
  case class workStart(workID :String) extends workDomainEve
  case class workForward(workID: String) extends workDomainEve
  case class workComplete(workID: String, result: Any) extends workDomainEve
  case class workerFailed(workID: String) extends workDomainEve
  case class workTimed(workID: String) extends workDomainEve


}
