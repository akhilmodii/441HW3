package actors

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.matching.Regex
import Actors.{Work, workExecutor, masterActor}

class worker(masterProxy: ActorRef, workerID: String, workerFinger: mutable.TreeMap[String, (Range, String)], numWorkers: Int) extends Actor with ActorLogging with Timers {

  import Actors.protocol._
  import context.dispatcher

  val ID: String = workerID
  var finger: mutable.TreeMap[String, (Range, String)] = workerFinger
  val interval: FiniteDuration = context.system.settings.config.getDuration("distributed-workers.worker-registration-interval").getSeconds.seconds
  val task: Cancellable = context.system.scheduler.schedule(0.seconds, interval, masterProxy, registerWorker(workerID))
  val executor: ActorRef = createExecutor()
  var curWork: Option[(String, Int, ActorRef)] = None
  log.info(f"Worker-$ID, ${self.path},finger: ${finger}")

  def work: (String, Int, ActorRef) = curWork match {
    case Some(work) => work
    case None => throw new IllegalStateException("This is not working")
  }

  def receive: Receive = {
    case workReady =>
      masterProxy ! requestWork(workerID)

    case Work(workID: String, job: Int, frontEnd) =>
      val workIDHash = (workID.toArray.foldLeft(0)(_+_.toInt) % numWorkers).toString
      if (workerID == workIDHash) {
        log.info(f"Worker-$ID, Got the job: $job with workID: $workID, amd worker Hash ID: $workIDHash")
        curWork = Some(workID, job, frontEnd)
        val pattern: Regex = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}".r

        pattern.findFirstMatchIn(workID) match {
          case Some(_) =>
            executor ! workExecutor.doWork(job, frontEnd)
            context.become(working)

          case _ =>
            log.info(f"Worker-$ID completed the work: $work")
            masterProxy ! workDone(workerID, workID, workID, job, frontEnd)
            context.setReceiveTimeout(5.seconds)
            context.become(waitForWork(workID, job, frontEnd))
        }
        masterProxy ! forwardWork(workerID, workID, forward = false)
      }

      else {
        val succ = getSucc(workIDHash, finger)._2
        log.info(f"Worker-$ID forwarded job: $job with workID: $workID and work Hash ID: $workIDHash to the successor: $succ")
        masterProxy ! forwardWork(succ, workID, forward = true)
      }
    }

  def getSucc(toFind: String, fingerTable: mutable.TreeMap[String, (Range, String)]): (Range, String) = {
    val difference = fingerTable.map(x => {
      (x._1, (x._2._2.toInt - toFind.toInt).abs)
    })

    val differenceKey: String = difference.minBy(_._2)._1

    if(toFind.toInt < difference.minBy(_._2)._2) {
      fingerTable.maxBy(_._2._2.toInt)._2
    }
    else{
      fingerTable(differenceKey)
    }
  }


  def waitForWork(result: Any, job: Int, actorRef: ActorRef): Receive = {
    case masterActor.ack(ID) if ID == work._1 =>
      masterProxy ! requestWork(workerID)
      context.setReceiveTimeout(Duration.Undefined)
      context.become(receive)

    case ReceiveTimeout =>
      log.info(f"Workerr-$workerID No ack. Resending workID: $work with result: $result")
      masterProxy ! workDone(workerID, work._1, result, job, actorRef)

  }
  def working: Receive = {
    case workExecutor.complete(result, job, actorRef) =>
      log.info(f"Worker-$workerID completed the work: $work and got the result: $result")
      masterProxy ! workDone(workerID, work._1, result, job, actorRef)

    case _: Work =>
      log.warning(f"Worker-$workerID Oops. Master told me to do work: $work, but currently working on: $curWork")

  }

  def createExecutor(): ActorRef =
    context.watch(context.actorOf(workExecutor.props, name="work-executor"))

}



object worker {

  def props(masterProxy: ActorRef, workerID: String, workerFinger: mutable.TreeMap[String, (Range, String)], numWorkers: Int): Props =
    Props(new worker(masterProxy, workerID.toString, workerFinger, numWorkers))
}