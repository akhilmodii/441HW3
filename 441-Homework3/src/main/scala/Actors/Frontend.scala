import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

import Actors.{Work, masterActor, masterSingleton, workResult}
import Main.conf
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import akka.cluster.pubsub.DistributedPubSubMediator.Put
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.io.{BufferedSource, Source}

object FrontEnd {

  var check_work = false
  var id = 3000
  def props(id: String, check_work: Boolean): Props = Props(new FrontEnd(id.toString, check_work))

  private case object Not_Ok
  private case object Tick
  private case object Retry

}

class FrontEnd(id: String, check_work: Boolean) extends Actor with ActorLogging with Timers {
  import FrontEnd._
  import context.dispatcher

  val mediator: ActorRef = DistributedPubSub(context.system).mediator

  mediator ! Put(self)

  val front_end_id: String = id

  val masterProxy: ActorRef = context.actorOf(
    masterSingleton.proxyProps(context.system), name = "masterProxy"

  )

  val movie_file: BufferedSource = Source.fromFile("movies.txt")
  val movie_line_iterator: Iterator[String] = movie_file.getLines.toArray.iterator
  movie_file.close()

  var counter_work = 0

  def nextwork_ID(): String = {

    if(check_work)
      movie_line_iterator.next()

    else
      UUID.randomUUID().toString
  }

  def work_send(work: Work): Unit = {

    implicit val timeout: Timeout = Timeout(conf.getInt("requestTimeout").seconds)

    (masterProxy ? work).recover {
      case _ => Not_Ok
    } pipeTo self
  }


  def busy(work_in_progress: Work): Receive = {
    work_send(work_in_progress)

    {
      case masterActor.ack(workId) =>
        log.info(f"[front-end-$front_end_id] Got ack for workId: $workId")
        val nextTick = ThreadLocalRandom.current.nextInt(
          conf.getInt("minRequest"),
          conf.getInt("maxRequest")).seconds
        timers.startSingleTimer(s"tick", Tick, nextTick)
        context.become(idle)

      case Not_Ok =>
        log.info(f"[front-end-$front_end_id] Work with workId: ${work_in_progress.workID} not accepted, retry later")
        timers.startSingleTimer("retry", Retry, conf.getInt("retryRequest").seconds)

      case Retry =>
        log.info(f"[front-end-$front_end_id] Retrying workId: ${work_in_progress.workID}")
        work_send(work_in_progress)
    }
  }

  override  def preStart(): Unit= {
    timers.startSingleTimer("tick", Tick, 10.seconds)
  }
  def receive: Receive = idle

  def idle: Receive = {

    case Tick =>
      counter_work += 1

      val work_id = nextwork_ID()

      log.info(f"[front-end-$front_end_id] Produced work_id: $work_id with counter_work: $counter_work")

      val working = Work(work_id, counter_work, self)
      context.become(busy(working))

    case workResult (workerID, job, hash, result, actorRef) =>
      log.info(f"[front-end-$front_end_id] Consumed result: $result for job: $job, workerID: $workerID, hash: $hash from [worker-${actorRef.path.name}]")
    case _: DistributedPubSubMediator.SubscribeAck =>
  }


  //    override def receive: Receive = ???
}


