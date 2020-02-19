package sample.cluster.k8s

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.{Cluster, Member, MemberStatus}
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus._
import akka.cluster.pubsub.DistributedPubSubMediator
import sample.cluster.k8s.conf.K8SMemberShipListenerConfig
import sample.cluster.k8s.protocol._


class ClusterMembershipEventListener(config: K8SMemberShipListenerConfig, mediator:ActorRef) extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  // subscribe to cluster changes, re-subscribe when restart 
  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateAsEvents, classOf[ClusterDomainEvent])
  }
  override def postStop(): Unit = cluster.unsubscribe(self)

  private def notifyStarted(m:Member): Unit = {
    val id = m.address.toString
    publish(MemberStarted(id,id))
  }

//  private def translateToClusterTreeStatus(status:MemberStatus) = status match {
//    case Up => sample.cluster.k8s.model.Up
//    case Down => sample.cluster.k8s.model.Down
//    case Exiting => sample.cluster.k8s.model.Exiting
//    case Leaving => sample.cluster.k8s.model.Leaving
//    case WeaklyUp => sample.cluster.k8s.model.WeaklyUp
//    case Joining -> sample.cluster.k8s.model.Joining
//    case Leaving => sample.cluster.k8s.model.Leaving
//  }

  private def notifyStopped(m:Member): Unit = {
    publish(MemberStopped(m.address.toString))
  }

  def publish(event:ClusterEvent) = {
    mediator ! DistributedPubSubMediator.Publish(config.topic,event)
  }

  def receive = {
    case state: CurrentClusterState =>
      log.info("Current members: {}", state.members.mkString(", "))
    case MemberJoined(member) =>
      log.info("New member joined : {}", member)
    case MemberWeaklyUp(member) =>
      log.info("Member weakly up : {}", member)
    case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)
      notifyStarted(member)
    case MemberLeft(member) =>
      log.info("Member is leaving (gracefully) : {}", member)
    case MemberExited(member) =>
      log.info("Member exited : {}", member)
      notifyStopped(member)
    case MemberDowned(member) =>
      log.info("Member downed : {}", member)
    case MemberRemoved(member, previousStatus) =>
      previousStatus match {
        case Down => log.info("Member removed after being downed : {}", member)
        case Exiting => log.info("Member removed after graceful exit : {}" , member)
        case _ => log.info("Previous State : {}", previousStatus)
      }
      notifyStopped(member)
    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)
    case ReachableMember(member) =>
      log.info("Member detected as reachable: {}", member)
    case _: MemberEvent => // ignore
  }
}

object ClusterMembershipEventListener {

  def props(config:K8SMemberShipListenerConfig, mediator:ActorRef) = Props(classOf[ClusterMembershipEventListener], config, mediator)


}