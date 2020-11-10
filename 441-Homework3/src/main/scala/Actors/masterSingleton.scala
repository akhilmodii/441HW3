package Actors

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}

import scala.concurrent.duration.DurationLong

object masterSingleton {
  private val name = "masterWorker"
  private val role = "backEnd"

  def startMasterSingleton(system: ActorSystem): ActorRef = {
    val timeout = system.settings.config.getDuration("distributed-workers.work-timeout").getSeconds.seconds

    system.actorOf(
      ClusterSingletonManager.props(masterActor.props(timeout), PoisonPill, ClusterSingletonManagerSettings(system).withRole(role)), name)
  }

  def proxyProps(system: ActorSystem): Props = ClusterSingletonProxy.props(
    settings = ClusterSingletonProxySettings(system).withRole(role), singletonManagerPath = s"/user/$name")
}