package sample.cluster.sse

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck}
import com.typesafe.config.ConfigFactory
import sample.cluster.sse.TransformationBackend.PubSubEnvelope
import sample.cluster.sse.TransformationPublisher.{AddListener, ListenerEnvelope, RemoveListener}

import collection.mutable.{HashMap, MultiMap, Set}
import scala.collection.mutable


class TransformationPublisher extends Actor with ActorLogging {
  val mediator = DistributedPubSub(context.system).mediator

  val topicSubscriptions =  new HashMap[String, Set[ActorRef]] with mutable.MultiMap[String,ActorRef]

  override def preStart(): Unit =  {
    super.preStart()
    // subscribe to the topic named "content"
    mediator ! Subscribe("backend", self)
  }

  def receive = {
    case PubSubEnvelope(topic, inboxId, msg:String) =>
      log.info(s"Got message with text ${msg} from topic ${topic} for inbox $inboxId")
      topicSubscriptions.get(topic).foreach(_.foreach(subscriber => subscriber ! ListenerEnvelope(topic,msg) ))
    case SubscribeAck(Subscribe("content", None, `self`)) =>
      log.info("subscription acked !")
    case AddListener(topic, rcv) =>
      topicSubscriptions.addBinding(topic, rcv)
      context.watch(rcv)
    case RemoveListener(topic, rcv) =>
      topicSubscriptions.removeBinding(topic,rcv)
    case Terminated(ref) =>
      topicSubscriptions.keys.foreach(key => topicSubscriptions.removeBinding(key,ref))
  }

}


object TransformationPublisher {

  case class AddListener(topic: String, subscribe: ActorRef)

  case class RemoveListener(topic:String, unsubscribe: ActorRef)

  case class ListenerEnvelope(topic:String, msg: String)

  def main(args: Array[String]): Unit = {
    // Override the configuration of the port when specified as program argument
    // To use artery instead of netty, change to "akka.remote.artery.canonical.port"
    // See https://doc.akka.io/docs/akka/current/remoting-artery.html for details
    val port = if (args.isEmpty) "0" else args(0)
    val config = ConfigFactory.parseString(s"""
        akka.remote.netty.tcp.port=$port
        """)
      .withFallback(ConfigFactory.parseString("akka.cluster.roles = [publisher]"))
      .withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)
    system.actorOf(Props[TransformationPublisher], name = "publisher")
  }

}
