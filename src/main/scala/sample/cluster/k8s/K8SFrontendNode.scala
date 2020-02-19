package sample.cluster.k8s

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.routing.{ClusterRouterGroup, ClusterRouterGroupSettings}
import akka.cluster.sharding.ClusterSharding
import akka.routing.RoundRobinGroup
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import pureconfig.ConfigSource
import sample.cluster.k8s.conf.{K8SCommandActorConfig, K8SDemoPublisherConfig, K8SRouteeConfig}
import sample.cluster.k8s.utils.ShardingFunctions.EntityRequestIdExtractor
import pureconfig.generic.auto._
import sample.cluster.k8s.K8SBackendNode.{routeeConfig, system}
import sample.cluster.k8s.ext.ProviderHubExtension

object K8SFrontendNode extends App {

  val log = LoggerFactory.getLogger(this.getClass)

  log.info("Starting node ...")

  val port = if (args.isEmpty) "0" else args(0)
  val config = ConfigSource.string(s"""
        akka.remote.netty.tcp.port=$port
        """)
    .withFallback(ConfigSource.string("akka.cluster.roles = [frontend]"))
    .withFallback(ConfigSource.default)

  val publisherConfig: K8SDemoPublisherConfig = config.at(K8SDemoPublisherConfig.configPath).loadOrThrow[K8SDemoPublisherConfig]
  val commandActorConfig: K8SCommandActorConfig = config.at(K8SCommandActorConfig.configPath).loadOrThrow[K8SCommandActorConfig]
  val routeeConfig: K8SRouteeConfig = config.at(K8SRouteeConfig.configPath).loadOrThrow[K8SRouteeConfig]

  val akkaConfig :Config = config.loadOrThrow[Config]
  val system: ActorSystem = ActorSystem("k8s-viz", akkaConfig)

  // Waiting for the node to join the cluster
  Cluster(system).registerOnMemberUp {
    val cluster = Cluster(system)
    val providerHub = ProviderHubExtension(system)
    val extractors = EntityRequestIdExtractor(system, InboxEntitySuperVisor.configPath)
    val shardRegion = ClusterSharding(system).startProxy(
      typeName = InboxEntitySuperVisor.shardTypeName,
      role = Some("backend"),
      extractEntityId = extractors.extractEntityId ,
      extractShardId = extractors.extractShardId
    )
    val publisher = system.actorOf(ClusterEventPublisher.props(publisherConfig), "publisher")
    val listener = system.actorOf(ClusterEventListener.props(List("k8s"), publisher), "listener")
    val routeeNames = (0 until routeeConfig.maxInstancesPerNode).map(i => s"${routeeConfig.actorName}$$$i")
    val router = system.actorOf(ClusterRouterGroup(RoundRobinGroup(Nil), ClusterRouterGroupSettings(
      totalInstances = routeeConfig.maxInstances,
      routeesPaths = routeeNames.map(x => s"/user/$x"),
      allowLocalRoutees = false,
      useRoles = "backend")
    ).props(),routeeConfig.actorName)
    if(port == "2552") {
      val commandActor = system.actorOf(CommandActor.props(commandActorConfig,shardRegion,router),"command-actor")
    }
  }

}
