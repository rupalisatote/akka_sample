package sample.cluster.sse

import language.postfixOps
import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.RootActorPath
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.Member
import akka.cluster.MemberStatus
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.http.scaladsl.model.DateTime
import akka.management.scaladsl.AkkaManagement
import com.typesafe.config.ConfigFactory
import sample.cluster.k8s.ClusterMembershipEventListener
import sample.cluster.sse.TransformationBackend.PubSubEnvelope

//#backend
class TransformationBackend extends Actor {

  val cluster = Cluster(context.system)

  // subscribe to cluster changes, MemberUp
  // re-subscribe when restart
  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])
  override def postStop(): Unit = cluster.unsubscribe(self)

  // activate Ditributed PubSub
  // activate the extension
  val mediator = DistributedPubSub(context.system).mediator


  def chooseInboxId: Int = {
    DateTime.now.second % 3
  }

  def receive = {
    case TransformationJob(text) =>
      val result = text.toUpperCase
      sender() ! TransformationResult(result)
      val inboxId = chooseInboxId
      mediator ! Publish("backend", PubSubEnvelope("backend", inboxId ,s"result for inbox with id ${inboxId}: $result"))
    case state: CurrentClusterState =>
      state.members.filter(_.status == MemberStatus.Up) foreach register
    case MemberUp(m) => register(m)
  }

  def register(member: Member): Unit =
    if (member.hasRole("frontend"))
      context.actorSelection(RootActorPath(member.address) / "user" / "frontend") !
        BackendRegistration
}
//#backend

object TransformationBackend {

  case class PubSubEnvelope[A](topic: String, inboxId: Int, payload: A)

  def main(args: Array[String]): Unit = {
    // Override the configuration of the port when specified as program argument
    // To use artery instead of netty, change to "akka.remote.artery.canonical.port"
    // See https://doc.akka.io/docs/akka/current/remoting-artery.html for details
    val port = if (args.isEmpty) "0" else args(0)
    val config = ConfigFactory.parseString(s"""
        akka.remote.netty.tcp.port=$port
        """)
      .withFallback(ConfigFactory.parseString("akka.cluster.roles = [backend]"))
      .withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)
    // Automatically loads Cluster Http Routes
    if(port == "2551") {
      AkkaManagement(system).start()
      system.actorOf(Props[ClusterMembershipEventListener], name = "event-listener")
    }
    system.actorOf(Props[TransformationBackend], name = "backend")
  }
}