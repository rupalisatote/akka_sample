package sample.cluster.k8s.ext

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider, PoisonPill}
import akka.cluster.{Cluster, pubsub}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import pureconfig.ConfigSource
import sample.cluster.k8s.ProviderHub
import sample.cluster.k8s.conf.K8SProviderHubConfig
import pureconfig.generic.auto._

class ProviderHubExtension(system:ActorSystem) extends Extension  {

  val router = Init()

  def Init()= {

    val config = ConfigSource.default
    val providerConfig = config.at(K8SProviderHubConfig.configPath).loadOrThrow[K8SProviderHubConfig]
    val cluster = Cluster(system)
    val mediator = pubsub.DistributedPubSub(system).mediator


    if (cluster.selfMember.hasRole("backend")) {
      val singleton = system.actorOf(
        ClusterSingletonManager.props(
          singletonProps = ProviderHub.props(providerConfig, mediator),
          terminationMessage = PoisonPill,
          settings = ClusterSingletonManagerSettings(system)
            .withSingletonName(providerConfig.singletonName)
            .withRole("backend")
            .withHandOverRetryInterval(providerConfig.retryInterval)), name = providerConfig.singletonManagerName)
    }
    val entryActor = system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/${providerConfig.singletonManagerName}",
        settings = ClusterSingletonProxySettings(system).withSingletonName(providerConfig.singletonName).withRole("backend")
      ) /*.withDispatcher(serviceConfig.dispatcherString)*/ ,
      name = s"${providerConfig.singletonManagerName}Proxy"
    )

    entryActor

  }

}


object ProviderHubExtension extends ExtensionId[ProviderHubExtension]
  with ExtensionIdProvider {

  val role = "provider-hub"

  override def createExtension(system: ExtendedActorSystem): ProviderHubExtension = new ProviderHubExtension(system)

  override def lookup(): ExtensionId[_ <: Extension] = ProviderHubExtension
}
