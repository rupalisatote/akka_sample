package sample.cluster.k8s

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash, Terminated}
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import sample.cluster.k8s.protocol.ClusterEvent


class ClusterEventListener(listensTo:List[String], publisher:ActorRef) extends Actor with ActorLogging with Stash {

  private val activationsAcknowledged = scala.collection.mutable.Set.empty[String]
  private val cancellationsAcknowledged = scala.collection.mutable.Set.empty[String]

  val mediator = DistributedPubSub(context.system).mediator

  def subscribe() {
    activationsAcknowledged.clear()
    if (listensTo.nonEmpty) {
      listensTo.foreach { topic =>
        mediator.tell(DistributedPubSubMediator.Subscribe(topic, self), self)
        activationsAcknowledged.add(topic)
      }
    }
  }

  def unSubscribe() {
    cancellationsAcknowledged.clear()
    if (listensTo.nonEmpty) {
      listensTo.foreach { topic =>
        mediator.tell(DistributedPubSubMediator.Unsubscribe(topic, self), self)
        cancellationsAcknowledged.add(topic)
      }
    }
  }

  final def delayUntilSubscriptionsConfirmed: Receive = {
    case DistributedPubSubMediator.SubscribeAck(DistributedPubSubMediator.Subscribe(topicname, _, `self`)) if activationsAcknowledged.contains(topicname) =>
      log.debug("Topic subscription confirmed [{}]", topicname)
      activationsAcknowledged.remove(topicname)
      if (activationsAcknowledged.isEmpty) {
        log.debug("All topic subscriptions confirmed, unstashing messages")
        this.unstashAll()
        context.become(receive)
      }

    case m if activationsAcknowledged.nonEmpty =>
      log.debug("Received a message while not all subscription confirmations have been received, the message will be stashed: {}", m)
      this.stash()

  }

  def delayUntilUnsubscriptionsConfirmed: Receive = {
    case DistributedPubSubMediator.UnsubscribeAck(DistributedPubSubMediator.Unsubscribe(topicname, _, `self`)) if cancellationsAcknowledged.contains(topicname) =>
      log.debug("Received a unsusbscription confirmation to topic [{}]", topicname)
      cancellationsAcknowledged.remove(topicname)
      if (cancellationsAcknowledged.isEmpty) {
        log.debug("All unsubscriptions have been confirmed, unstashing messages")
        this.unstashAll()
      } else {
        log.debug("Waiting for {} more unsubscription confirmations")
      }

    case m if cancellationsAcknowledged.nonEmpty =>
      log.debug("Received a message while not all unsubscription confirmations have been received, the message will be stashed: {}", m)
      this.stash()
  }


  override def preStart() {
    super.preStart()
    this.subscribe()
    context.become(this.delayUntilSubscriptionsConfirmed)
    log.debug("Topics Listener Actor has Started.")
  }

  override def postStop() {
    this.unSubscribe()
    super.postStop()
    log.debug("Topics Listener Actor has Stopped.")
  }




  override def receive: Receive =  {
    case m: ClusterEvent =>
      publisher ! m
  }
}

object ClusterEventListener {

  def props(listensTo:Seq[String],publisher:ActorRef) = Props(classOf[ClusterEventListener],listensTo,publisher)

  case class AddSubscription(topic: String, subscribe: ActorRef)

  case class RemoveSubscription(topic:String, unsubscribe: ActorRef)

}
