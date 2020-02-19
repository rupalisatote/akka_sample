package sample.cluster.k8s

import java.net.InetSocketAddress

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Status}
import akka.cluster.Cluster
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Path.~
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.pattern.pipe
import sample.cluster.k8s.model.NodeSerializers._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import sample.cluster.k8s.model.{ClusterNode, HttpServer, LoadBalancer}
import sample.cluster.k8s.protocol._
import io.circe.syntax._
import sample.cluster.k8s.ClusterEventPublisher.Refresh
import sample.cluster.k8s.conf.K8SDemoPublisherConfig

import scala.util.{Failure, Success}

class ClusterEventPublisher(config: K8SDemoPublisherConfig) extends Actor with ActorLogging {

  val port = config.httpPort
  val interface = "0.0.0.0"

  private var tree = ClusterNode("ncf-cluster","cluster-node")
  implicit val system: ActorSystem = context.system
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec : ExecutionContext = system.dispatcher
  val cluster: Cluster = Cluster(context.system)
  val mediator = DistributedPubSub(system).mediator

  var binding:Option[Http.ServerBinding] = None

  override def receive: Receive = {
    case evt @ EntityStarted(memberId,serviceType,shardId,entityId, entityName) =>
      log.info(s"Received entity started event : [member : $memberId,service: ${serviceType.name},strategy ${serviceType.strategy.toString},shard: $shardId, entity: $entityId, entity name : ${entityName}, service type : ${serviceType}")
      updateTree(evt)
    case evt @ EntityStopped(memberId,serviceType,shardId,entityId) =>
      log.info(s"Received entity stopped event : [member : $memberId,service: ${serviceType.name},strategy: ${serviceType.strategy.toString},shard: $shardId, entity: $entityId")
      updateTree(evt)
    case evt @ ServiceStarted(memberId, serviceType) =>
      log.info(s"Received service started event : [member: $memberId, service: ${serviceType.name}, service name : ${serviceType.name}, type: $serviceType")
      updateTree(evt)
    case evt @ HttpServiceStarted(memberId, serviceId) =>
      log.info(s"Received http service alive event : [member : $memberId, service : $serviceId]")
      updateTree(evt)
    case evt @ MemberStarted(memberId, memberName) =>
      log.info(s"Received member started event : [address : $memberId]")
      updateTree(evt)
    case evt @ MemberStopped(memberId) =>
      log.info(s"Received member stopped event: [address : $memberId]")
      updateTree(evt)
    case evt @ EntityWeightChanged(_,_,_,entityId,_) =>
      log.info(s"Received entity weight change notification for $entityId.")
      updateTree(evt)
    case evt @ EntityStateChanged(_,_,_,entityId,_) =>
      log.info(s"Received entity state change notification for $entityId")
      updateTree(evt)
    case evt @ SingletonWeightChanged(_,svcType,size) =>
      log.info(s"Received singleton weight change for ${svcType.name}. New size : $size")
      updateTree(evt)
    case evt @ RouteeWeightChanged(memberId,serviceType,routeeId,totalRequests) =>
      log.info(s"Received routee activation notification for : [member : $memberId, service: ${serviceType.name}, strategy: ${serviceType.strategy.toString}, routee: $routeeId]. Messages processed : ${totalRequests.toString}")
      updateTree(evt)
    case b @ Http.ServerBinding(address) =>
      binding = Some(b)
      OK(address)
    case Status.Failure(cause)       => KO(cause)
    case Refresh => notifyUpState()
  }

  def notifyUpState(): Unit = {
    mediator ! DistributedPubSubMediator.Publish(config.topic,ServiceStarted(cluster.selfAddress.toString, HttpServer(LoadBalancer)))
  }

  def updateTree(event: ClusterEvent):Unit = {
    val (clusterNode, updateResult) = ClusterNode.handleClusterEvent(event).run(tree).value
    tree = clusterNode
    log.info("update result : {}", updateResult)
    log.info(tree.asJson.toString)
  }

  override def preStart(): Unit = {
    startHttpServer()
    context.system.scheduler.schedule(15.seconds, 60.seconds, self, Refresh)
  }

  def startHttpServer() = {
      Http().bindAndHandle(route,interface, port).pipeTo(self)
  }

  private def OK(address: InetSocketAddress) = {
    log.info(s"Websocket Server is up at $address", address)
    notifyUpState()
  }

  override def postStop(): Unit = binding.foreach(_.unbind().onComplete {
    case Success(Done) => log.info("WS server is down.")
    case Failure(err) => log.debug("unbind http server failed : {}", err)
  })

  private def KO(cause: Throwable) = {
    log.error(cause, s"WebSocket Server start failed at $interface:$port!")
    context.stop(self)
  }

  def getClusterTreeAsJson() = {
    tree.asJson.toString
  }

  val wsHandler : Flow[Message,Message,Any] = Flow[Message]
    .collect {
      case TextMessage.Strict(_) =>
        TextMessage(getClusterTreeAsJson())
      case _ =>
        TextMessage("Only strict messages are supported by this server.")
    }

  def route : Route = {
    import akka.http.scaladsl.server.Directives._

    get {
      pathEndOrSingleSlash {
        complete("The NCF Cluster Tree Websocket Server is alive, but you need to go for /cluster-events to get started...")
      }
      pathPrefix("web") {
            getFromResourceDirectory("web")
      }
    } ~
    path(pm = "cluster-events"){
      get {
        handleWebSocketMessages(wsHandler)
      }
    }
  }
}

object ClusterEventPublisher {

  def props(config: K8SDemoPublisherConfig) = Props(classOf[ClusterEventPublisher],config)

  case object Refresh

}
