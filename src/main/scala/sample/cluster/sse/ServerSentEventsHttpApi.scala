package sample.cluster.sse

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Status}
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{BroadcastHub, Keep, Source}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import scala.language.implicitConversions
import scala.concurrent.duration._
import akka.pattern.pipe
import com.typesafe.config.ConfigFactory
import sample.cluster.sse.TransformationBackend.PubSubEnvelope

import scala.reflect.ClassTag

final class ServerSentEventsHttpApi(address: String, port: Int, bufferSize: Int, heartbeat: FiniteDuration, mediator: ActorRef)(implicit mat: Materializer) extends Actor with ActorLogging {

  import ServerSentEventsHttpApi._
  import context.dispatcher

  Http(context.system)
    .bindAndHandle(route(mediator, heartbeat),
      address,
      port)
    .pipeTo(self)

  override def receive = {
    case Http.ServerBinding(address) => OK(address)
    case Status.Failure(cause)       => KO(cause)
  }

  private def OK(address: InetSocketAddress) = {
    log.info(s"SSE Server Up at $address", address)
    context.become(Actor.emptyBehavior)
  }

  private def KO(cause: Throwable) = {
    log.error(cause, s"SSE Server start failed at $address:$port!")
    context.stop(self)
  }

}

object ServerSentEventsHttpApi {

    import akka.http.scaladsl.server.Directives._
    import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._

    def main(args: Array[String]): Unit = {

      val port = if (args.isEmpty) "0" else args(0)
      val config = ConfigFactory.parseString(s"""
        akka.remote.netty.tcp.port=$port
        """)
        .withFallback(ConfigFactory.parseString("akka.cluster.roles = [api_server]"))
        .withFallback(ConfigFactory.load())

      implicit val system = ActorSystem("ClusterSystem", config)
      implicit val mat = ActorMaterializer()
      val mediator = DistributedPubSub(system).mediator
      val cluster = Cluster(system)
      implicit def toFiniteDuration(d: java.time.Duration): FiniteDuration = Duration.fromNanos(d.toNanos)
      cluster.registerOnMemberUp {
        ServerSentEventsHttpApi(config.getString("sse.address"), config.getInt("sse.port"), config.getInt("sse.bufferSize"), config.getDuration("sse.heartbeat") , mediator)
      }
    }


    def apply(address: String, port: Int , bufferSize: Int, heartbeat:FiniteDuration, mediator: ActorRef)(implicit mat: Materializer, sys: ActorSystem) = {
      sys.actorOf(Props(new ServerSentEventsHttpApi(address, port, bufferSize, heartbeat, mediator)))
    }

    def route(mediator: ActorRef, heartbeat:FiniteDuration)(implicit mat: Materializer) : Route = {

      def toServerSentEvent[A](event: PubSubEnvelope[A]) = event.payload match {
        case s:String => ServerSentEvent(s, "result")
      }

      def filterByInboxId[A](envelope:PubSubEnvelope[A], inboxId: Int) = envelope.payload match {
        case _ => envelope.inboxId == inboxId
      }

      def events[A: ClassTag]() =
        Source
          .actorRef[PubSubEnvelope[A]](100, OverflowStrategy.dropHead)
          //.map(toServerSentEvent)
          //.keepAlive(heartbeat, () => ServerSentEvent.heartbeat)
          .mapMaterializedValue(source => mediator ! Subscribe("backend", source))
          .toMat(BroadcastHub.sink[PubSubEnvelope[A]])(Keep.both).run

      path("events" / IntNumber) { inboxId => {
        get {
          complete {
            events[String]()._2.filter(filterByInboxId(_,inboxId)).map(env => toServerSentEvent(env))
          }
      }

        }
      }
    }
}
