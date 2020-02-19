package sample.cluster.k8s

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.routing.{ClusterRouterGroup, ClusterRouterGroupSettings}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.management.scaladsl.AkkaManagement
import akka.routing.RoundRobinGroup
import com.typesafe.config.Config
import org.apache.logging.log4j.core.async.AsyncLoggerContextSelector
import org.slf4j.LoggerFactory
import pureconfig.generic.auto._

import scala.concurrent.duration._
import sample.cluster.k8s.utils.ShardingFunctions._
import pureconfig._
import sample.cluster.k8s.conf._
import sample.cluster.k8s.ext.ProviderHubExtension

object K8SBackendNode extends App {
  //sys.props += "log4j2.contextSelector" -> classOf[AsyncLoggerContextSelector].getName // activate async logging
  val log = LoggerFactory.getLogger(this.getClass)

  log.info("Starting node ...")
  val akka_role = "backend"
  val port = if (args.isEmpty) "0" else args(0)

  val config = ConfigSource.string(s"""
        akka.remote.netty.tcp.port=$port
        """)
      .withFallback(ConfigSource.string("akka.cluster.roles = [backend,provider-hub]"))
      .withFallback(ConfigSource.default)


  val shardingConfig: K8SDemoShardingConfig = config.at(K8SDemoShardingConfig.configPath).loadOrThrow[K8SDemoShardingConfig]
  val publisherConfig: K8SDemoPublisherConfig = config.at(K8SDemoPublisherConfig.configPath).loadOrThrow[K8SDemoPublisherConfig]
  val listenerConfig: K8SMemberShipListenerConfig = config.at(K8SMemberShipListenerConfig.configPath).loadOrThrow[K8SMemberShipListenerConfig]
  val routeeConfig: K8SRouteeConfig = config.at(K8SRouteeConfig.configPath).loadOrThrow[K8SRouteeConfig]
  val akkaConfig : Config = config.loadOrThrow[Config]
  val system: ActorSystem = ActorSystem("k8s-viz", akkaConfig)

  if(port == "2551") {
    AkkaManagement(system).start()
  }
  // Waiting for the node to join the cluster
  Cluster(system).registerOnMemberUp {
    val cluster = Cluster(system)

    log.info(s"node [${cluster.selfAddress}] has joined the cluster [${cluster.system.name}]. Launching services ...")
    val mediator = DistributedPubSub(system).mediator
    val membershipListener = system.actorOf(ClusterMembershipEventListener.props(listenerConfig, mediator), name = listenerConfig.actorName)
    val settings: ClusterShardingSettings = ClusterShardingSettings(system).withRole(Some(akka_role)).withRememberEntities(false)
    val extractors = EntityRequestIdExtractor(system, InboxEntitySuperVisor.configPath)
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
    val providerHub = ProviderHubExtension(system)
    val shardRegion = ClusterSharding(system).start(
      typeName = InboxEntitySuperVisor.shardTypeName,
      entityProps = InboxEntitySuperVisor.props(shardingConfig, mediator),
      settings = settings,
      extractEntityId = extractors.extractEntityId ,
      extractShardId = extractors.extractShardId
    )

  }


}
