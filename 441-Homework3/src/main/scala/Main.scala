
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

import Actors.{Work, masterActor, masterSingleton, workResult}
import Main.conf
import Actors.worker
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Timers}
import akka.cluster.pubsub.DistributedPubSubMediator.Put
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import akka.management.scaladsl.AkkaManagement
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import chordAlgo.FTable
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration.DurationInt
import scala.io.{BufferedSource, Source}

// For the Front end class that periodically sends the workload.






// ------------------ MAIN ---------------------

object Main {
  val backEndPortRange: Range.Inclusive = 2000 to 2999
  val frontEndPortRange: Range.Inclusive = 3000 to 3999
  val conf: Config = ConfigFactory.load("application.conf")

  def main(args: Array[String]): Unit = {
    args.headOption match {
      case Some("alternateWork") =>
        startCluster(alternateWork = true)

      case None =>
        startCluster(alternateWork = false)

      case Some(portString) if portString.matches("""\d+""") =>
        val portNumber = portString.toInt
        if (backEndPortRange.contains(portNumber)){
          backEndStart(portNumber)
        }
        else if (frontEndPortRange.contains(portNumber)){
          frontEndStart(portNumber, alternateWork = false)
        }
        else {
          startWorker(portNumber, args.lift(1).map(_.toInt).getOrElse(1))
        }
    }
  }

  def backEndStart(portNumber: Int): Unit = {
    val system: ActorSystem = ActorSystem("Cluster", config(portNumber, "back-end"))
    masterSingleton.startMasterSingleton(system)
    AkkaManagement(system).start()
  }

  def frontEndStart(portNumber: Int, alternateWork: Boolean): Unit = {
    val port = portNumber-3000+1
    val system: ActorSystem = ActorSystem("Cluster", config(portNumber, "front-end"))
    system.actorOf(FrontEnd.props(port.toString, alternateWork), s"front-end-$port")

  }

//  def config(portNumber: Int, actorRole: String): Config = {
//    ConfigFactory.parseString(s"""akka.remote.netty.tcp.port= $portNumber akka.cluster.roles= $actorRole""").withFallback(ConfigFactory.load())
//  }

  def config(port: Int, role: String): Config =
    ConfigFactory.parseString(s"""
      akka.remote.netty.tcp.port=$port
      akka.cluster.roles=[$role]
    """).withFallback(ConfigFactory.load())


  def startWorker(portNumber: Int, numWorkers: Int): Unit = {
    val system: ActorSystem = ActorSystem("Cluster", config(portNumber, "workers"))
    val masterProxy = system.actorOf(masterSingleton.proxyProps(system), name="masterProxy")
    (1 until numWorkers).foreach(n => {
      val fingerTableEntry = new FTable(n, numWorkers)
      system.actorOf(worker.props(masterProxy, n.toString, fingerTableEntry.finger, numWorkers), s"worker-$n")
    })
  }



  def startCluster(alternateWork: Boolean): Unit= {
    backEndStart(2551)
    startWorker(5001, Math.pow(2, conf.getInt("numPositions")).toInt)
    val numUsers = conf.getInt("numUsers")
    require(numUsers < 2000, "Due to port range Limitation use different number of users.")
    for(i<-3000 until 3000+numUsers) {
      frontEndStart(i, alternateWork)
    }
  }

}   // end of class
