package sample.cluster.k8s

import akka.actor.{Actor, ActorLogging, ActorRef, Props, ReceiveTimeout}
import akka.cluster.Cluster
import sample.cluster.k8s.ClusterEventPublisher.Refresh
import sample.cluster.k8s.InboxSimulatorActor.{Refreshed, Refreshing, Resized}
import sample.cluster.k8s.ext.ProviderHubExtension
import sample.cluster.k8s.protocol.{InboxRefreshCompleted, InboxRefreshMessage, InboxRequestMessage, InboxResponseMessage}

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

class InboxSimulatorActor(id:String, topic:String, mediator:ActorRef, passivateAfterIdleTime:FiniteDuration, maxInboxSize: Int) extends Actor with ActorLogging {

  var itemsCount = InboxSimulatorActor.generateRandomInboxSize(maxInboxSize)
  var hasPendingMessages = false
  context.setReceiveTimeout(passivateAfterIdleTime)

  val providerHub = ProviderHubExtension(context.system).router

  override def receive: Receive = {
    case InboxRequestMessage(userId) =>
      itemsCount = InboxSimulatorActor.generateNextInboxSize(itemsCount,maxInboxSize)
      if(itemsCount % 2 == 0) scheduleRefresh()
      sender ! InboxResponseMessage(userId, itemsCount)
      context.parent ! Resized(id,itemsCount)
    case ReceiveTimeout =>
      if(!hasPendingMessages) {
        log.debug("Receive timeout received by inbox actor : [id = {}, ref = {}]. Asking for passivation by parent...", id, self)
        context.parent ! InboxEntitySuperVisor.Passivate
        context.become(purging)
      }
      else {
       log.debug("Received timeout but having pending actions. Skipping request for passivation.")
      }
    case Refresh =>
      log.debug(s"Starting inbox refresh for $id")
      context.parent ! Refreshing(userId = id)
      //context.system.scheduler.scheduleOnce(5.seconds,self,Refreshed(id, InboxSimulatorActor.generateNextInboxSize(itemsCount,50)))(context.system.dispatcher)
      providerHub ! InboxRefreshMessage(id, itemsCount)
      hasPendingMessages = true
    case InboxRefreshCompleted(userId, newSize) =>
      log.debug(s"$userId Received response from provider hub. New inbox size : $newSize")
      itemsCount = newSize
      hasPendingMessages = false
      context.parent ! Refreshed(id,itemsCount)
//    case m @ Refreshed(_,newCount) =>
//      log.debug(s"inbox refresh for $id completed. New count : $newCount")
//      itemsCount = newCount
//      context.parent ! m
//      hasPendingMessages = false
  }

  def scheduleRefresh() = {
    context.system.scheduler.scheduleOnce(1.seconds,self,Refresh)(context.system.dispatcher)
  }


  def purging: Receive = {
    case m =>
      log.debug("Received message while waiting for poison pill from parent. Forwarding back to parent...")
      context.parent.forward(m)
  }
}

object InboxSimulatorActor {

  def props(id:String, topic:String, mediator:ActorRef, passivateAfterIdleTime:FiniteDuration, maxInboxSize:Int) = Props(classOf[InboxSimulatorActor],id, topic,mediator, passivateAfterIdleTime, maxInboxSize)

  val rnd = Random

  def generateRandomInboxSize(maxSize:Int) = math.abs(rnd.nextInt(50))

  def generateNextInboxSize(currentSize:Int, maxSize:Int) = {
    val randomDiff = rnd.nextInt(4)
    if(currentSize < randomDiff) currentSize + randomDiff else if(isEven(randomDiff)) currentSize + randomDiff else currentSize - randomDiff
  }

  def isEven(cnt:Int) = cnt % 2 == 0

  case object Refresh

  case class Refreshed(userId:String, size:Int)

  case class Refreshing(userId:String)

  case class Resized(userId:String, size:Int)

}
