package sample.cluster.k8s

import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.routing.{ClusterRouterGroup, ClusterRouterGroupSettings}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.routing.RoundRobinGroup
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import pureconfig.ConfigSource
import sample.cluster.k8s.K8SBackendNode.{akka_role, config, listenerConfig, log, routeeConfig, shardingConfig, system}
import sample.cluster.k8s.K8SFrontendNode.{commandActorConfig, config, publisherConfig, routeeConfig, system}
import sample.cluster.k8s.conf.{K8SCommandActorConfig, K8SDemoPublisherConfig, K8SDemoShardingConfig, K8SMemberShipListenerConfig, K8SRouteeConfig}
import sample.cluster.k8s.ext.ProviderHubExtension
import sample.cluster.k8s.utils.ShardingFunctions.EntityRequestIdExtractor
import pureconfig.generic.auto._

object Runner extends App {

  val log = LoggerFactory.getLogger(this.getClass)
  log.info("Starting node ...")

  val config = ConfigSource.default
  val akkaConfig : Config = config.loadOrThrow[Config]


  val system: ActorSystem = ActorSystem("k8s-viz", akkaConfig)
  // Akka Management hosts the HTTP routes used by bootstrap
  AkkaManagement(system).start()
  // Starting the bootstrap process needs to be done explicitly
  ClusterBootstrap(system).start()
  val shutdown = CoordinatedShutdown(system)


  Cluster(system).registerOnMemberUp {

    val cluster = Cluster(system)
    log.info(s"node [${cluster.selfAddress}] has joined the cluster [${cluster.system.name}]. Launching services ...")

    val providerHub = ProviderHubExtension(system)


    val extractors = EntityRequestIdExtractor(system, InboxEntitySuperVisor.configPath)

    if(cluster.selfMember.hasRole("backend")) {

      val shardingConfig: K8SDemoShardingConfig = config.at(K8SDemoShardingConfig.configPath).loadOrThrow[K8SDemoShardingConfig]
      val listenerConfig: K8SMemberShipListenerConfig = config.at(K8SMemberShipListenerConfig.configPath).loadOrThrow[K8SMemberShipListenerConfig]
      val routeeConfig: K8SRouteeConfig = config.at(K8SRouteeConfig.configPath).loadOrThrow[K8SRouteeConfig]

      val mediator = DistributedPubSub(system).mediator
      val membershipListener = system.actorOf(ClusterMembershipEventListener.props(listenerConfig, mediator), name = listenerConfig.actorName)

      val settings: ClusterShardingSettings = ClusterShardingSettings(system).withRole(Some("backend")).withRememberEntities(false)

      val shardRegion = ClusterSharding(system).start(
        typeName = InboxEntitySuperVisor.shardTypeName,
        entityProps = InboxEntitySuperVisor.props(shardingConfig, mediator),
        settings = settings,
        extractEntityId = extractors.extractEntityId ,
        extractShardId = extractors.extractShardId
      )

      val routeeNames = (0 until routeeConfig.maxInstancesPerNode).map(i => s"${routeeConfig.actorName}$$$i")
      routeeNames.foreach { name =>
        val ref = system.actorOf(RouteeActor.props(routeeConfig,mediator),name)
        log.info(s"Started routee instance with name : $name")
      }
      val router = system.actorOf(ClusterRouterGroup(RoundRobinGroup(Nil), ClusterRouterGroupSettings(
        totalInstances = routeeConfig.maxInstances,
        routeesPaths = routeeNames.map(x => s"/user/$x"),
        allowLocalRoutees = true,
        useRoles = "backend")
      ).props(),routeeConfig.actorName)

    }

    if(cluster.selfMember.hasRole("frontend")) {

      val publisherConfig: K8SDemoPublisherConfig = config.at(K8SDemoPublisherConfig.configPath).loadOrThrow[K8SDemoPublisherConfig]
      val commandActorConfig: K8SCommandActorConfig = config.at(K8SCommandActorConfig.configPath).loadOrThrow[K8SCommandActorConfig]
      val routeeConfig: K8SRouteeConfig = config.at(K8SRouteeConfig.configPath).loadOrThrow[K8SRouteeConfig]

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

      val commandActor = system.actorOf(CommandActor.props(commandActorConfig,shardRegion,router),"command-actor")

    }
  }


}
