package sample.cluster.k8s.conf


import akka.actor.ActorRef
import pureconfig.generic.auto._

import scala.concurrent.duration.FiniteDuration

case class K8SDemoShardingConfig(maxShards:Int, topic:String, passivateAfter:FiniteDuration, maxInboxSize:Int)

object K8SDemoShardingConfig {

  val configPath = "sample.cluster.k8s.sharding"
}

case class K8SDemoPublisherConfig(httpPort:Int, httpServiceName:String, topic:String)

object K8SDemoPublisherConfig {

  val configPath = "sample.cluster.k8s.ws"

}

case class K8SProviderHubConfig(topic:String,
                                maxProcessingDelay:FiniteDuration,
                                singletonName:String,
                                singletonManagerName:String,
                                maxHandOverRetries:Int,
                                maxTakeOverRetries:Int,
                                retryInterval:FiniteDuration,
                                maxInboxSize: Int
                               )

object K8SProviderHubConfig {

  val configPath = "sample.cluster.k8s.provider-hub"

}

case class K8SRouteeConfig(topic:String, maxInstances: Int, maxInstancesPerNode: Int, actorName:String)



object K8SRouteeConfig {

  val configPath = "sample.cluster.k8s.routees"

}

//object K8SNodeConfig {
//  val configPath = "sample.cluster.k8s.node"
//}
//
//case class K8SNodeConfig(roles: Set[String])

case class K8SMemberShipListenerConfig(topic: String, actorName:String)

object K8SMemberShipListenerConfig {

  val configPath = "sample.cluster.k8s.gossip"

}

case class K8SCommandActorConfig(actorName:String, initialDelay: FiniteDuration, callEvery: FiniteDuration)

object K8SCommandActorConfig {

  val configPath = "sample.cluster.k8s.cmd"

}


