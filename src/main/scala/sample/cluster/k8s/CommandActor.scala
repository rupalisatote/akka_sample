package sample.cluster.k8s

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import sample.cluster.k8s.CommandActor.Tick
import sample.cluster.k8s.RouteeActor.{SourcesRequest, SourcesResponse}
import sample.cluster.k8s.conf.K8SCommandActorConfig
import sample.cluster.k8s.protocol._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

class CommandActor(config: K8SCommandActorConfig, facadeActor:ActorRef, routerActor:ActorRef) extends Actor with ActorLogging {

  implicit val ec : ExecutionContext = context.system.dispatcher

  val rnd = Random

  override def preStart(): Unit = context.system.scheduler.schedule(config.initialDelay, config.callEvery, self,Tick)

  private def generateRandomUser(): String = {
    val number = 100 + rnd.nextInt(110)
    s"A000$number"
  }

  override def receive: Receive = {
    case Tick =>
      val userId = generateRandomUser()
      log.info(s"deciding on request for $userId")
      if(!userId.contains("6"))  {
        log.info("will send a inbox size request...")
        facadeActor ! InboxRequestMessage(userId)
      } else {
        log.info(s"skipping request for $userId....will send a sources request instead.")
        routerActor ! SourcesRequest(userId)
      }
    case InboxResponseMessage(userId,taskCount) =>
      log.info(s"inbox size $userId: $taskCount tasks")
    case SourcesResponse(sources) =>
      log.info(s"received sources list : ${sources.mkString(",")}")
  }

}

object CommandActor {

  case object Tick

  def props(config: K8SCommandActorConfig, facadeActor:ActorRef, routerActor:ActorRef)  = Props(classOf[CommandActor], config, facadeActor, routerActor)

}