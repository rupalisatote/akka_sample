package sample.cluster.k8s

import akka.actor.{Actor, ActorLogging, ActorRef, Address, Props, Terminated}
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSubMediator
import sample.cluster.k8s.InboxSimulatorActor.{isEven, rnd}
import sample.cluster.k8s.ProviderHub.Tick
import sample.cluster.k8s.conf.K8SProviderHubConfig
import sample.cluster.k8s.model.{ClusterSingleton, ProviderHubService, ServiceType}
import sample.cluster.k8s.protocol._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

class ProviderHub(config:K8SProviderHubConfig, mediator:ActorRef) extends Actor with ActorLogging {

  implicit val ec : ExecutionContext = context.dispatcher
  var itemsProcessed = 0
  val scheduler = context.system.scheduler
  val address =  Cluster(context.system).selfAddress
  val pendingRequests: scala.collection.mutable.Map[String,ActorRef] = scala.collection.mutable.Map.empty[String,ActorRef]

  override def receive: Receive = {
    case Tick =>
      log.info(s"${ProviderHub.serviceType.name} received Tick. Reporting items processed to monitor...")
      mediator ! DistributedPubSubMediator.Publish(config.topic, SingletonWeightChanged(address.toString,ProviderHub.serviceType,itemsProcessed) )
    case InboxRefreshMessage(userId, currentSize) =>
      val processingTime = Duration.fromNanos((config.maxProcessingDelay.toNanos * ProviderHub.rnd.nextFloat()).toLong)
      scheduler.scheduleOnce(processingTime,self, InboxRefreshCompleted(userId,ProviderHub.generateNextInboxSize(currentSize,config.maxInboxSize)))
      pendingRequests += (userId -> sender())
      context.watch(sender())
    case m @ InboxRefreshCompleted(userId, _) =>
      itemsProcessed += 1
      pendingRequests.get(userId).foreach { ref =>
        ref ! m
        pendingRequests.retain((_,v) => v != ref)
      }
    case Terminated(ref) =>
      //context.unwatch(ref)
      pendingRequests.retain((_,v) => v != ref)
    case m => log.info(s"${ProviderHub.serviceType.name} received an unhandled message : $m")
  }

  val member: Address = Cluster(context.system).selfMember.address

  override def preStart() = {
    log.info(s"${ProviderHub.serviceType.name} is starting!")
    context.system.scheduler.schedule(3.seconds, 3.seconds, self, Tick)
    mediator ! DistributedPubSubMediator.Publish(config.topic, ServiceStarted(member.toString,ProviderHub.serviceType))
  }

  def reportItemsProcessed() = {

  }

  override def postStop(): Unit = {
    log.info(s"${ProviderHub.serviceType.name} has been stopped !")
  }

}



object ProviderHub {

  def props(config:K8SProviderHubConfig, mediator:ActorRef) = Props(classOf[ProviderHub],config, mediator)

  val serviceType: ServiceType = ProviderHubService(ClusterSingleton)

  def generateNextInboxSize(currentSize:Int, maxSize:Int) = {
    val randomDiff = rnd.nextInt(10)
    if(currentSize < randomDiff) currentSize + randomDiff else if(isEven(randomDiff))  {
      val increasedCount = currentSize + randomDiff
      if(increasedCount <= maxSize) increasedCount else maxSize
    } else {
      val smallerCount = currentSize - randomDiff
      if(smallerCount >= 0) smallerCount else 0
    }
  }

  val rnd = Random

  case object Tick

}
