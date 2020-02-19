package sample.cluster.k8s

import akka.actor.{Actor, ActorLogging, ActorRef, Address, OneForOneStrategy, PoisonPill, Props, SupervisorStrategy, Terminated}
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSubMediator
import sample.cluster.k8s.conf.K8SDemoShardingConfig
import sample.cluster.k8s.model.{InboxService, ServiceType, Sharding}
import sample.cluster.k8s.InboxSimulatorActor._

import scala.concurrent.duration._
import sample.cluster.k8s.protocol._
import sample.cluster.k8s.utils.ShardingFunctions.EntityRequestIdExtractor

class InboxEntitySuperVisor(config: K8SDemoShardingConfig, mediator:ActorRef) extends Actor with ActorLogging {

  override val supervisorStrategy = OneForOneStrategy(
    maxNrOfRetries = 3,
    withinTimeRange = 3.second
  )(SupervisorStrategy.defaultDecider)


  val entities = scala.collection.mutable.Map.empty[String, ActorRef]
  val member: Address = Cluster(context.system).selfAddress
  val serviceType : ServiceType = InboxService(Sharding(config.maxShards))


  override def receive: Receive = {
    case Resized(id,size) =>
      mediator.tell(DistributedPubSubMediator.Publish(config.topic, EntityWeightChanged(member.toString, serviceType ,getShardId(id),id, size)),self)
    case Refreshing(id) =>
      mediator.tell(DistributedPubSubMediator.Publish(config.topic, EntityStateChanged(member.toString, serviceType, getShardId(id),id, List("refreshing"))),self)
    case Refreshed(id,size) =>
      mediator.tell(DistributedPubSubMediator.Publish(config.topic, EntityStateChanged(member.toString, serviceType, getShardId(id),id, List.empty[String])),self)
      mediator.tell(DistributedPubSubMediator.Publish(config.topic, EntityWeightChanged(member.toString, serviceType, getShardId(id),id, size)),self)
    case m:EntityRequestMessage =>
      classify.lift(m) match {
        case Some(id) =>
          val simulator = entities.get(id) match {
            case Some(ref) => ref
            case None => createInstance(id)
          }
          simulator.forward(m)
        case None =>
          log.debug(s"dropping message as it does not have a valid entity id")
          this.unhandled(m)
      }
    case InboxEntitySuperVisor.Passivate =>
      passivate(sender()).foreach { id =>
        sender ! PoisonPill
        log.debug("PoisonPill sent to child actor: (id = [{}], ref = [{}])",id, sender())
      }
    case Terminated(ref) =>
      passivate(ref).foreach { id =>
        log.debug("child actor terminated: [{}], ref = [{}]", id, ref)
      }

  }

  def createInstance(id:String) = {
    val ref = context.actorOf(InboxSimulatorActor.props(id, config.topic, mediator, config.passivateAfter, config.maxInboxSize))
    context.watch(ref)
    mediator.tell(DistributedPubSubMediator.Publish(config.topic, EntityStarted(member.toString, serviceType ,getShardId(id),id,id)),self)
    entities += (id -> ref)
    ref
  }

  def getShardId(id:String) = math.abs(id.hashCode % config.maxShards).toString

  def classify: PartialFunction[EntityRequestMessage,String] = {
    case m: EntityRequestMessage => m.userId
  }

  def passivate(ref:ActorRef) = {
    val keys = entities.collectFirst {
      case(key,`ref` ) => key
    }
    keys.foreach { id =>
      log.debug("Entity actor about to be passivated : (id = [{}], ref = [{}]", id,ref)
      mediator.tell(DistributedPubSubMediator.Publish(config.topic, EntityStopped(member.toString,serviceType,getShardId(id),id)),self)
      entities -= id
    }
    keys
  }

}

object InboxEntitySuperVisor {

  def props(config: K8SDemoShardingConfig, mediator: ActorRef) = Props(classOf[InboxEntitySuperVisor], config, mediator)

  case object Passivate

  val serviceName = "inbox-service"

  val shardTypeName = "InboxService"

  val serviceEntryActor = "shard-region"

  val channel = "k8s"

  val configPath = "sample.cluster.k8s.sharding"
}
