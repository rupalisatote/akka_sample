package sample.cluster.k8s

import akka.actor.{Actor, ActorLogging, ActorRef, Address, Props}
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSubMediator
import sample.cluster.k8s.RouteeActor.{SourcesRequest, SourcesResponse}
import sample.cluster.k8s.conf.K8SRouteeConfig
import sample.cluster.k8s.model.{Replication, SourceService}
import sample.cluster.k8s.protocol.{RouteeWeightChanged, EntityWeightChanged}

class RouteeActor(config: K8SRouteeConfig, mediator:ActorRef) extends Actor with ActorLogging {

  val sources: List[String] = List("SCAOD","SF","EXP", "LV", "PU", "PA", "EC")
  val cluster: Cluster = Cluster(context.system)
  val address: Address = cluster.selfAddress
  val serviceType = SourceService(model.Replication(config.maxInstances,config.maxInstancesPerNode))
  var totalRequestsHandled: Int = 0

  override def receive: Receive =  {
    case SourcesRequest(userId) =>
      log.info(s"received sources request for $userId")
      totalRequestsHandled = totalRequestsHandled + 1
     mediator.tell(
        DistributedPubSubMediator.Publish(config.topic, RouteeWeightChanged(address.toString, serviceType, s"${self.path.name}@${address.toString}", totalRequestsHandled)),
        self)
      sender ! SourcesResponse(sources)
  }

}

object RouteeActor {

  def props(config:K8SRouteeConfig, mediator:ActorRef) = Props(classOf[RouteeActor], config, mediator)

  case class SourcesRequest(userId:String)

  case class SourcesResponse(sources: List[String])

  val serviceName = "source-service"

  val serviceEntryActor = "router"



}
