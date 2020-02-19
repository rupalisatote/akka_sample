package sample.cluster.k8s.model
import cats.data.State
import io.circe.{Encoder, Json}
import io.circe.syntax._
import monocle.{Lens, unsafe}
import monocle.macros.GenLens
import sample.cluster.k8s.protocol
import sample.cluster.k8s.protocol._


// sum type representing the type of nodes that can exist in the cluster tree
// K8S LEVELs
// 1. Cluster = ROOT
// 2. Member = JVM Node in the K8S orchestration
// 3. Service = Akka Roles active on a member
// AKKA ACTOR LEVELS
// 1. EntryActor = The entry actor for a given service
// Depending on the reactive strategy applied this could be
// 1.1 The actual singleton actor representing a service with strategy Cluster Singleton
// 1.2 The router actor acting as a facade in front of a configurable number of routees / worker actors : when Cluster aware routing is used
// 1.3 A shard region actor when Cluster Sharding is used.
// 2. Shard Actors : actor managing various shards assigned to the member by the Shard Coordinator. These are the parent actors of the actual
//    entity actors
// 3. Entity Actors : the actors representing individual inboxes in this demo

sealed trait NodeType

case object Cluster extends NodeType
case object Member extends NodeType
case object Service extends NodeType
case object EntryActor extends NodeType
case object Actor extends NodeType
case object Void extends NodeType

sealed trait ActorType

case object Shard extends ActorType
case object Router extends ActorType
case object Routee extends ActorType
case object Entity extends ActorType
case object Facade extends ActorType
case object Singleton extends ActorType

sealed trait MemberState

case object Joining extends MemberState // initial state
case object Up extends MemberState // node has joined
case object WeaklyUp extends MemberState // when active, members can join in weakly up state when the cluster is still converging
case object Leaving extends MemberState // transient state
case object Exiting extends MemberState // transient state (following when the node is executing its exit after being asked to leave
case object Down extends MemberState // node was downed after having been unreachable for some time
case object Removed extends MemberState // final state applied by leader after either graceful or ungraceful exit

sealed trait ReactiveStrategy

// Singleton
case object ClusterSingleton extends ReactiveStrategy {
  override def toString: String = "singleton"
}

// Reverse Proxy : applies to client api and receiver api which are
// load balanced at the level of K8S or F5 but represent instances that are completely unaware of one another's existence
case object LoadBalancer extends ReactiveStrategy {
  override def toString: String = "http-service"
}

// Cluster Routing aka Replication (a configurable number of actors behind an akka cluster router actor
case class Replication(maxSize: Int, maxPerNode: Int) extends ReactiveStrategy {
  override def toString: String = s"router"
}

// Sharding for entity services
case class Sharding(shards: Int) extends ReactiveStrategy {
  override def toString: String = s"shard-region"
}




// Various NCF service typesS with a configurable reactive (akka cluster) strategy
// every service has a configured reactive strategy
// SINGLETON vs REPLICATED vs SHARDED
// we pass into the constructor
sealed trait ServiceType extends Product with Serializable {
  def name: String
  def strategy: ReactiveStrategy
  def withStrategy(strategy:ReactiveStrategy):ServiceType
}

case class InboxService(strategy: ReactiveStrategy) extends ServiceType {
  val name: String = "inbox-service"
  override def toString: String = s"$name [${strategy.toString}]"

  override def withStrategy(strategy:ReactiveStrategy) = this.copy(strategy = strategy)
}

case class InboxPollingService(strategy: ReactiveStrategy) extends ServiceType {
  val name: String = "inbox-polling-service"
  override def toString: String = s"$name [${strategy.toString}]"

  override def withStrategy(strategy:ReactiveStrategy) = this.copy(strategy = strategy)

}

case class InboxSourceService(strategy: ReactiveStrategy) extends ServiceType {
  val name: String = "inbox-source-service"
  override def toString: String = s"$name [${strategy.toString}]"

  override def withStrategy(strategy:ReactiveStrategy) = this.copy(strategy = strategy)
}

case class SourceService(strategy: ReactiveStrategy) extends ServiceType {
  val name: String = "source-service"
  override def toString: String = s"$name [${strategy.toString}]"

  override def withStrategy(strategy:ReactiveStrategy) = this.copy(strategy = strategy)
}

case class BusinessScenarioService(strategy: ReactiveStrategy) extends ServiceType {
  val name: String = "business-scenario-service"
  override def toString: String = s"$name [${strategy.toString}]"

  override def withStrategy(strategy:ReactiveStrategy) = this.copy(strategy = strategy)
}

case class AsyncActionService(strategy: ReactiveStrategy) extends ServiceType {
  val name: String = "async-actions-service"
  override def toString: String = s"$name [${strategy.toString}]"

  override def withStrategy(strategy:ReactiveStrategy) = this.copy(strategy = strategy)
}

case class AsyncActionPollingService(strategy: ReactiveStrategy) extends ServiceType {
  val name: String = "async-actions-polling-service"
  override def toString: String = s"$name [${strategy.toString}]"

  override def withStrategy(strategy:ReactiveStrategy) = this.copy(strategy = strategy)
}

case class ProviderHubService(strategy: ReactiveStrategy) extends ServiceType {
  val name: String = "provider-hub"
  override def toString: String = s"$name [${strategy.toString}]"

  override def withStrategy(strategy:ReactiveStrategy) = this.copy(strategy = strategy)
}

case class ClientRegistrationService(strategy: ReactiveStrategy) extends ServiceType {
  val name: String = "client-registration-service"
  override def toString: String = s"$name [${strategy.toString}]"

  override def withStrategy(strategy:ReactiveStrategy) = this.copy(strategy = strategy)
}

case class PublisherService(strategy: ReactiveStrategy) extends ServiceType {
  val name: String = "publisher"
  override def toString: String = s"$name [${strategy.toString}]"

  override def withStrategy(strategy:ReactiveStrategy) = this.copy(strategy = strategy)
}

case class HttpServer(strategy: ReactiveStrategy) extends ServiceType {
  val name: String = "http-server"
  override def toString: String = s"$name [${strategy.toString}]"

  override def withStrategy(strategy:ReactiveStrategy) = this.copy(strategy = strategy)
}

case class NodeRef(uniqueId: String, nodeType: NodeType)


sealed trait TreeNode[A <: TreeNode[_]] extends Product with Serializable {
  def uniqueId: String
  def name: String
  def nodeType: NodeType
  def children: List[A]
  def parent: NodeRef
  def getRef: NodeRef = NodeRef(uniqueId, nodeType)
  def size:Int = children.size
  def hasChildWithId(id: String): Boolean = children.exists(child => child.uniqueId == id)
  def states = List.empty[String]
  def findChild(id: String): Option[A] = children.find(child => child.uniqueId == id)
  def hasSameChildren(otherChildren: List[A]): Boolean = otherChildren.length != children.length || otherChildren.toSet != children.toSet
}

case class ClusterNode(uniqueId: String, name: String, children: List[MemberNode] = List.empty[MemberNode]) extends TreeNode[MemberNode] {

  val nodeType: NodeType = Cluster
  val parent: NodeRef = NodeRef("void", Void)


  // copy method
  def withMember(id: String, name: String, state: MemberState, children: List[ServiceNode] = List.empty[ServiceNode]): ClusterNode = {
    findChild(id) match {
      case Some(child) => if (child.name != name || !child.hasSameChildren(children)) {
        val updatedMember = child.copy(children = children, name = name)
        this.copy(children = updatedMember :: this.children.filterNot(m => m.uniqueId == id))
      } else this
      case None => this.copy(children = MemberNode(id, name, this.getRef, state, children) :: this.children)
    }
  }

}

case class MemberNode(uniqueId: String, name: String, parent: NodeRef, state: MemberState, children: List[ServiceNode] = List.empty[ServiceNode]) extends TreeNode[ServiceNode] {
  val nodeType: NodeType = Member



  def withService(id: String, name: String, serviceType: ServiceType, children: List[EntryActorNode] = List.empty[EntryActorNode]): MemberNode = {
    findChild(id) match {
      case Some(child) =>
        if (child.name != name || !child.hasSameChildren(children)) {
          val updatedService = child.copy(children = children, name = name)
          this.copy(children = updatedService :: this.children.filterNot(s => s.uniqueId == id))
        } else this
      case None => this.copy(children = ServiceNode(id, name, this.getRef, serviceType, children) :: this.children)
    }
  }


}

case class ServiceNode(uniqueId: String, name: String, parent: NodeRef, serviceType: ServiceType, children: List[EntryActorNode] = List.empty[EntryActorNode]) extends TreeNode[EntryActorNode] {
  val nodeType: NodeType = Service


  def withEntryActor(id: String, name: String, children: List[ActorNode] = List.empty[ActorNode]): ServiceNode = {
    findChild(id) match {
      case Some(child) =>
        if (child.name != name || !child.hasSameChildren(children)) {
          val updatedEntryActor = child.copy(name = name, children = children)
          this.copy(children = updatedEntryActor :: this.children.filterNot(a => a.uniqueId == id))
        }
        else this
      case None => this.copy(children = EntryActorNode(id, name, this.getRef, children) :: this.children)
    }
  }


}

case class EntryActorNode(uniqueId: String, name: String, parent: NodeRef, children: List[ActorNode] = List.empty[ActorNode], override val size:Int = 0) extends TreeNode[ActorNode] {
  val nodeType: NodeType = EntryActor
  val actorType: ActorType = Facade

  def withChildActor(id: String, name: String, actorType: ActorType, children: List[ActorNode] = List.empty[ActorNode]): EntryActorNode = {
    findChild(id) match {
      case Some(child) => if (child.name != name || !child.hasSameChildren(children)) {
        val updatedActorNode = child.copy(name = name, children = children)
        this.copy(children = updatedActorNode :: this.children.filterNot(a => a.uniqueId == id))
      } else this
      case None => this.copy(children = ActorNode(id, name, this.getRef, actorType, children) :: this.children)
    }
  }

  def calculateSize():Int = {
    if(parent.uniqueId.contains("singleton")) size else children.length
  }
}

case class ActorNode(uniqueId: String, name: String, parent: NodeRef, actorType: ActorType, children: List[ActorNode] = List.empty[ActorNode], override val size: Int = 0, override val states:List[String] = List.empty[String]) extends TreeNode[ActorNode] {
  val nodeType: NodeType = Actor

  def withChildActor(id: String, name: String, actorType: ActorType, children: List[ActorNode] = List.empty[ActorNode]): ActorNode = {
    findChild(id) match {
      case Some(child) => if (child.name != name || !child.hasSameChildren(children)) {
        val updatedActor = child.copy(name = name, children = children)
        this.copy(children = updatedActor :: this.children.filterNot(a => a.uniqueId == id))
      } else this
      case None => this.copy(children = ActorNode(id, name, this.getRef, actorType, children) :: this.children)
    }
  }

  def calculateSize() = actorType match {
      case Shard | Router | Facade => children.length
      case Entity | Singleton | Routee => size
      case _ => children.length
    }


  def withSize(size:Int) = this.copy(size = size)
}

object NodeSerializers {


  implicit val encodeActorNode: Encoder[ActorNode] = new Encoder[ActorNode] {
    final def apply(a: ActorNode): Json = Json.obj(
      ("id", Json.fromString(a.uniqueId)),
      ("name", Json.fromString(a.name)),
      ("type", Json.fromString(a.actorType.toString.toLowerCase())),
      ("children", if (a.children.isEmpty) Json.Null else Json.fromValues(a.children.map(c => c.asJson))),
      ("size", Json.fromInt(a.calculateSize())),
      ("states", Json.fromValues(a.states.map(state => state.asJson)))
    )
  }

  implicit val encodeEntryActorNode: Encoder[EntryActorNode] = new Encoder[EntryActorNode] {
    final def apply(a: EntryActorNode): Json = Json.obj(
      ("id", Json.fromString(a.uniqueId)),
      ("name", Json.fromString(a.name)),
      ("type", Json.fromString(a.actorType.toString.toLowerCase())),
      ("children", if (a.children.isEmpty) Json.Null else Json.fromValues(a.children.map(c => c.asJson))),
      ("size", Json.fromInt(a.calculateSize())),
      ("states", Json.fromValues(a.states.map(state => state.asJson)))
    )
  }

  private def getDescriptionForServiceNode(svc: ServiceNode): String = {
    svc.serviceType.strategy match {
      case ClusterSingleton => "singleton"
      case Sharding(shards) => s"sharding (size: $shards shards)"
      case Replication(maxSize, pernode) => s"replication (max size : $maxSize, $pernode / node)"
      case LoadBalancer => s"loadbalanced"
    }
  }

  implicit val encodeServiceNode: Encoder[ServiceNode] = new Encoder[ServiceNode] {
    final def apply(a: ServiceNode): Json = Json.obj(
      ("id", Json.fromString(a.uniqueId)),
      ("name", Json.fromString(a.name)),
      ("type", Json.fromString("service")),
      ("children", if (a.children.isEmpty) Json.Null else Json.fromValues(a.children.map(c => c.asJson))),
      ("size", Json.fromInt(a.size)),
      ("states", Json.fromValues(a.states.map(state => state.asJson)))
    )
  }

  implicit val encodeMemberNode: Encoder[MemberNode] = new Encoder[MemberNode] {
    final def apply(a: MemberNode): Json = Json.obj(
      ("id", Json.fromString(a.uniqueId)),
      ("name", Json.fromString(a.name)),
      ("type", Json.fromString(a.nodeType.toString.toLowerCase())),
      ("children", if (a.children.isEmpty) Json.Null else Json.fromValues(a.children.map(c => c.asJson))),
      ("size", Json.fromInt(a.size)),
      ("states", Json.fromValues(a.states.map(state => state.asJson)))
    )
  }

  implicit val encodeClusterNode: Encoder[ClusterNode] = new Encoder[ClusterNode] {
    override def apply(a: ClusterNode): Json = Json.obj(
      ("id", Json.fromString(a.uniqueId)),
      ("name", Json.fromString(a.name)),
      ("type", Json.fromString(a.nodeType.toString.toLowerCase())),
      ("children", if (a.children.isEmpty) Json.Null else Json.fromValues(a.children.map(c => c.asJson))),
      ("size", Json.fromInt(a.size)),
      ("states", Json.fromValues(a.states.map(state => state.asJson)))
    )
  }

}

object ClusterNode {

  import monocle.function.all._
  import monocle.unsafe.UnsafeSelect
  sealed trait NodeProps
  case object NodeName extends  NodeProps
  case object NodeSize extends NodeProps
  case object NodeState extends NodeProps

  // Lenses

  def cluster[T](name:String) = ClusterNode(name,name).asInstanceOf[T]

  val memberLens: Lens[ClusterNode, List[MemberNode]] = GenLens[ClusterNode](_.children)
  val serviceLens: Lens[MemberNode, List[ServiceNode]] = GenLens[MemberNode](_.children)
  val entryActorLens: Lens[ServiceNode, List[EntryActorNode]] = GenLens[ServiceNode](_.children)
  val shardLens: Lens[EntryActorNode, List[ActorNode]] = GenLens[EntryActorNode](_.children)
  val routeeLens: Lens[EntryActorNode, List[ActorNode]] = GenLens[EntryActorNode](_.children)
  val childActorLens: Lens[ActorNode, List[ActorNode]] = GenLens[ActorNode](_.children)
  val memberNameLens: Lens[MemberNode, String] = GenLens[MemberNode](_.name)
  val serviceNameLens: Lens[ServiceNode, String] = GenLens[ServiceNode](_.name)
  val entryActorSizeLens: Lens[EntryActorNode,Int] = GenLens[EntryActorNode](_.size)
  val actorSizeLens: Lens[ActorNode, Int] = GenLens[ActorNode](_.size)
  val actorStatesLens: Lens[ActorNode,List[String]] = GenLens[ActorNode](_.states)

  def generateMemberNodeLens(memberId: String) = memberLens composeTraversal each composePrism unsafe.UnsafeSelect.unsafeSelect(_.uniqueId == memberId)

  def generateServiceNodeLens(memberId: String, serviceType:ServiceType) = generateMemberNodeLens(memberId) composeLens serviceLens composeTraversal
    each composePrism UnsafeSelect.unsafeSelect(_.uniqueId == s"$memberId/${serviceType.name}")

  def generateEntryActorNodeLens(memberId: String, serviceType:ServiceType) = generateServiceNodeLens(memberId, serviceType) composeLens entryActorLens composeTraversal
    each composePrism UnsafeSelect.unsafeSelect(_.uniqueId == s"$memberId/${serviceType.name}-${serviceType.strategy.toString}")

  def generateRouteeNodeLens(memberId:String, serviceType: ServiceType, routeeId:String) = generateEntryActorNodeLens(memberId,serviceType) composeLens routeeLens composeTraversal each composePrism UnsafeSelect.unsafeSelect(_.uniqueId == routeeId)

  def generateShardNodeLens(memberId: String, serviceType: ServiceType, shardId: String) =  {
    generateEntryActorNodeLens(memberId, serviceType) composeLens shardLens composeTraversal each composePrism UnsafeSelect.unsafeSelect(_.uniqueId == shardId)
  }

  def generateEntityNodeLens(memberId: String, serviceType: ServiceType, shardId: String, entityId: String) = generateShardNodeLens(memberId, serviceType, shardId) composeLens childActorLens composeTraversal
    each composePrism UnsafeSelect.unsafeSelect(e => e.uniqueId == entityId)

  // Below is an implemenation of a Cats State MonadState Monad for the cluster tree
  // ClusterNode => (ClusterNode, ProcessingResult)
  // we compose various state monad functions and create a single entry point
  // into the tree modification algorithm that can handle any protocol message for updating the tree

  def handleClusterEvent(event:ClusterEvent): State[ClusterNode,ProcessingResult] = event match {
    case m:MemberStarted => for {
      result <- insertMemberNode(m)
    } yield protocol.ProcessingResult(event, List(result))
    case m:MemberStopped => for {
      result <- removeMemberNode(m)
    } yield protocol.ProcessingResult(event, List(result))
    case m:ServiceStarted => for {
      result <- insertServiceNode(m)
    } yield ProcessingResult(event, List(result))
    case m:ServiceStopped => for {
      result <- removeServiceNode(m)
    } yield ProcessingResult(event, List(result))
    case m:EntityStarted => for {
      result <- insertEntityActorNode(m)
    } yield ProcessingResult(event, List(result))
    case m:EntityStopped => for {
      result <- removeEntityActorNode(m)
    } yield ProcessingResult(event, List(result))
    case m:SingletonWeightChanged => for {
      result <- createOrUpdateSingletonWithRequestCount(m)
    } yield ProcessingResult(event, List(result))
    case m:EntityWeightChanged => for {
      result <- ChangeEntityWeight(m)
    } yield ProcessingResult(event, List(result))
    case m:EntityStateChanged => for {
      result <- ChangeEntityStates(m)
    } yield ProcessingResult(event, List(result))
    case m:RouteeWeightChanged => for {
      result <- createOrUpdateActorWithRequestCount(m)
    } yield ProcessingResult(event, List(result))
  }


  /*
  Update Methods for all Node Types in the tree
1. Member nodes : MEMBER nodes are direct children of the root node, i.e. the Cluster node
2. Service nodes : SERVICE nodes represent particular services in the  NCF cluster (e.g. inbox service, source service, ...
3. EntryActor nodes : Entry actors are the link between more coarse grained orchestration of services and a particular Akka cluster strategy applied to it
  3.1 Router Actor (when using Cluster aware Routing - aka Replication)
  3.2 Singleton Actor (when using ClusterSingleton)
  3.3 Shard Region actor (when using Akka Cluster Sharding)
4. Depending on the strategy different types of child actors  can appear as children of the entry actor
4.1 when the service applies Sharding strategy : Shard actor with below it the Entity Actors as leaves
4.2 when the servcie applies Replication strategy : Routee actors as leaves underneath the Router actor (n instances, according to the size of the routing pool)
4.3 when the service is a singleton : no child actors will appear below the service entry actor : the entry actor is the service
 */


  // 1. MEMBER FUNCTIONS
  def insertMemberNode(event: MemberStarted): State[ClusterNode,UpdateResult] =
    State[ClusterNode,UpdateResult] { cluster =>
      cluster.children.find(m => m.uniqueId == event.memberId) match {
        case Some(mbr) => ((generateMemberNodeLens(mbr.uniqueId) composeLens memberNameLens set event.memberName)(cluster),NodeUpdated(mbr.getRef,List(NodeName)))
        case _ => ((memberLens modify (MemberNode(event.memberId, event.memberName, cluster.getRef, Up) :: _))(cluster),NodeAdded(NodeRef(event.memberId,Member)))
      }
    }

  def removeMemberNode(event: MemberStopped): State[ClusterNode,UpdateResult] =
    State[ClusterNode, UpdateResult] { cluster =>
      cluster.children.find(m => m.uniqueId == event.memberId) match {
        case Some(m) => ((memberLens modify (_.filterNot(m => m.uniqueId == event.memberId)))(cluster),NodeRemoved(NodeRef(event.memberId,Member)))
        case None => (cluster, NotFound(NodeRef(event.memberId,Member)))
      }
    }


  // SERVICE FUNCTIONS : note that the creation of a service automatically adds additional child actors based on the cluster strategy of the service
  // Singleton -> Singleton actor immediately created as a child node
  // Sharding -> Shard Region actor immediately created as a child node
  // Routing -> Router actor immediately created as a child node
  def insertServiceNode(event: ServiceStarted): State[ClusterNode, UpdateResult] =
    for {
      _ <- ensureMember(event.memberId, event.memberId)
      service <- ensureService(event.memberId, event.serviceType)
    } yield service

  def removeServiceNode(event: ServiceStopped): State[ClusterNode, UpdateResult] =
    State[ClusterNode, UpdateResult] { cluster =>
      ((generateMemberNodeLens(event.memberId) composeLens serviceLens modify(_.filter(s => s.uniqueId == s"${event.memberId}/${event.serviceId}")))(cluster),NodeRemoved(NodeRef(event.serviceId,Service)))
    }


  // SINGLETON
  def createOrUpdateSingletonWithRequestCount(event: SingletonWeightChanged) = {
    for {
      _ <- ensureMember(event.memberId, event.memberId)
      _ <- ensureService(event.memberId, event.serviceType)
      result <- ChangeSingletonWeight(event)
    } yield result
  }

  def ChangeSingletonWeight(event: SingletonWeightChanged): State[ClusterNode, UpdateResult] =
    State[ClusterNode, UpdateResult] { cluster =>
      ((generateEntryActorNodeLens(event.memberId, event.serviceType) composeLens entryActorSizeLens set event.size)(cluster), Success)
    }


  /*
    UPDAT ACTOR ACTIVITY COUNT
   */

  def createOrUpdateActorWithRequestCount(event: RouteeWeightChanged) = {
    for {
      _ <- ensureMember(event.memberId, event.memberId)
      _ <- ensureService(event.memberId, event.serviceType)
      _ <- ensureRoutee(event.memberId,event.serviceType,event.routeeId)
      result <- applyRequestCount(event)
    } yield result
  }

  def applyRequestCount(event:RouteeWeightChanged): State[ClusterNode,UpdateResult] =
    State[ClusterNode,UpdateResult] { cluster =>
      ((generateRouteeNodeLens(event.memberId, event.serviceType, event.routeeId) composeLens actorSizeLens  set event.totalRequests)(cluster),Success)
    }

  /* Subordinate actor nodes
  1. Routee
  2. Shard
  3. Entity
   */

  //ROUTEE ADD
  def insertRouteeNode(event: RouteeStarted): State[ClusterNode, UpdateResult] =
    State[ClusterNode, UpdateResult] { cluster =>
      ((generateEntryActorNodeLens(event.memberId, event.serviceType) composeLens routeeLens modify(routees => ActorNode(event.routeeId, event.routeeName,NodeRef(s"$event.member/${event.serviceType.name}-${event.serviceType.strategy.toString}", EntryActor),Routee) :: routees))(cluster),NodeAdded(NodeRef(event.routeeId,Actor)))
    }

  // ROUTEE REMOVE
  def removeRouteeNode(event: RouteeStopped): State[ClusterNode, UpdateResult] =
    State[ClusterNode, UpdateResult] { cluster =>
      ((generateEntryActorNodeLens(event.memberId,event.serviceType) composeLens routeeLens modify(_.filterNot(r => r.uniqueId == event.routeeId)))(cluster),NodeRemoved(NodeRef(event.routeeId,Actor)))
    }


  // SHARDS

  def findEmptyShards(cluster:ClusterNode) = {
    for {
      member <- cluster.children
      service <- member.children if service.serviceType.strategy.isInstanceOf[Sharding]
      region <- service.children
      shard <- region.children if shard.children.isEmpty
    }  yield(member.uniqueId, service.uniqueId, region.uniqueId, shard.uniqueId)
  }


  def discardEmptyShards() : State[ClusterNode,UpdateResult] =
    State[ClusterNode, UpdateResult] { cluster =>
      val updatedCluster = findEmptyShards(cluster).foldLeft(cluster) { (acc, shardRef) =>
        (memberLens composeTraversal each composePrism UnsafeSelect.unsafeSelect(m => m.uniqueId == shardRef._1) composeLens serviceLens
          composeTraversal each composePrism unsafe.UnsafeSelect.unsafeSelect(s => s.uniqueId == shardRef._2) composeLens entryActorLens
          composeTraversal each composePrism unsafe.UnsafeSelect.unsafeSelect(sr => sr.uniqueId == shardRef._3) composeLens shardLens modify(_.filterNot(s => s.uniqueId == shardRef._4)))(acc)
      }
      (updatedCluster, Success)
    }


  // ENTITIES
  def insertEntityActorNode(event:EntityStarted): State[ClusterNode, UpdateResult] = for {
    _ <- discardEntityNodesWithId(event.entityId)
    _ <- discardEmptyShards
    _ <- ensureMember(event.memberId, event.memberId)
    _ <- ensureService(event.memberId, event.serviceType)
    _ <- ensureShard(event.memberId,event.serviceType,event.shardId)
    result <- addEntityToShard(event)
  } yield result

  def addEntityToShard(event: ClusterEvent): State[ClusterNode, UpdateResult] =
    State[ClusterNode,UpdateResult] { cluster =>
      event match {
        case EntityStarted(memberId,serviceType,shardId,entityId,entityName) =>
          ensureShard(memberId,serviceType,shardId)
          ((generateShardNodeLens(memberId,serviceType,shardId) composeLens childActorLens modify(entities => ActorNode(entityId,entityName,NodeRef(shardId,Actor),Entity) :: entities))(cluster),EntityAdded(NodeRef(shardId,Actor),NodeRef(entityId,Actor)))
        case _ =>
          (cluster, Unchanged)
      }
    }


  // CHANGE STATE OF ENTITY ACTOR (e.g. "refreshing"
  def ChangeEntityStates(event: EntityStateChanged) : State[ClusterNode, UpdateResult] =
    State[ClusterNode,UpdateResult] { cluster =>
      ((generateEntityNodeLens(event.memberId,event.serviceType, event.shardId,event.entityId) composeLens actorStatesLens  set event.states)(cluster),Success)
    }

  // CHANGE WEIGHT OF ENTITY ACTOR (e.g. : inbox size changes)
  def ChangeEntityWeight(event:EntityWeightChanged) : State[ClusterNode,UpdateResult] =
    State[ClusterNode,UpdateResult] { cluster =>
      ((generateEntityNodeLens(event.memberId,event.serviceType, event.shardId, event.entityId) composeLens actorSizeLens  set event.size)(cluster),Success)
    }

  def listPathsToEntityWithId(cluster:ClusterNode, entityId:String) = {
    for {
      member <- cluster.children
      service <- member.children if service.serviceType.strategy.isInstanceOf[Sharding]
      region <- service.children
      shard <- region.children
      actor <- shard.children if actor.uniqueId == entityId
    } yield (member.uniqueId,service.uniqueId,region.uniqueId,shard.uniqueId, actor.uniqueId)
  }

  def removeEntityActorNode(event:EntityStopped): State[ClusterNode,UpdateResult] =
    State[ClusterNode, UpdateResult] { cluster =>
      ((generateShardNodeLens(event.memberId,event.serviceType, event.shardId) composeLens childActorLens modify(_.filterNot(entity => entity.uniqueId == event.entityId)))(cluster),NodeRemoved(NodeRef(event.entityId, Actor)))
    }

  def discardEntityNodesWithId(entityId:String):State[ClusterNode,UpdateResult] =
    State[ClusterNode, UpdateResult] { cluster =>
      val updatedCluster = listPathsToEntityWithId(cluster,entityId).foldLeft(cluster) { (acc, entityRef) =>
        (memberLens composeTraversal each composePrism UnsafeSelect.unsafeSelect(m => m.uniqueId == entityRef._1) composeLens serviceLens
          composeTraversal each composePrism unsafe.UnsafeSelect.unsafeSelect(s => s.uniqueId ==  entityRef._2) composeLens entryActorLens
          composeTraversal each composePrism unsafe.UnsafeSelect.unsafeSelect(sr => sr.uniqueId == entityRef._3) composeLens shardLens
          composeTraversal each composePrism unsafe.UnsafeSelect.unsafeSelect(s => s.uniqueId == entityRef._4) composeLens childActorLens
          modify(_.filterNot(s => s.uniqueId == entityRef._5)))(acc)
      }
      (updatedCluster, Success)
    }

 /*
 HELPER METHODS : ensureX methods create missing ancestor nodes when a node is inserted at any given depth
 -> exists for : Member, Service (which automatically creates its correpsonding entry actor based on the service's cluster strategy
  */
  def ensureMember(memberId:String, memberName:String): State[ClusterNode,UpdateResult] =
    State[ClusterNode,UpdateResult] { cluster =>
      memberLens composeTraversal each composePrism UnsafeSelect.unsafeSelect(m => m.uniqueId == memberId) headOption cluster match {
        case Some(_) => (cluster,Unchanged)
        case None => ((memberLens modify(members => MemberNode(memberId,memberName,cluster.getRef,Up):: members))(cluster),NodeAdded(NodeRef(memberId,Member)))
      }
    }

  def generateEntryNodeId(memberId:String, serviceType:ServiceType): String = s"$memberId/${serviceType.name}-${serviceType.strategy.toString}"

  def ensureService(memberId:String, serviceType:ServiceType):State[ClusterNode,UpdateResult] =
    State[ClusterNode,UpdateResult] { cluster =>
      generateMemberNodeLens(memberId) composeLens serviceLens composeTraversal each composePrism UnsafeSelect.unsafeSelect(s => s.uniqueId == s"$memberId/${serviceType.name}") headOption cluster match {
        case Some(_) => (cluster,Unchanged)
        case None =>
          val serviceId = s"$memberId/${serviceType.name}-${serviceType.strategy.toString}"
          val serviceName = serviceType.strategy.toString
          val entryNode = EntryActorNode(serviceId, serviceName,NodeRef(serviceId,Service))
          val serviceNode = ServiceNode(s"$memberId/${serviceType.name}",serviceType.name,NodeRef(memberId,Member),serviceType,List(entryNode))
          ((generateMemberNodeLens(memberId) composeLens serviceLens modify(services => serviceNode :: services))(cluster),NodeAdded(NodeRef(s"$memberId/${serviceType.name}",Service)))
      }
    }

  def ensureShard(memberId:String, serviceType:ServiceType,shardId:String):State[ClusterNode,UpdateResult] =
    State[ClusterNode, UpdateResult] { cluster =>
      generateEntryActorNodeLens(memberId,serviceType) composeLens shardLens composeTraversal each composePrism UnsafeSelect.unsafeSelect(s => s.uniqueId == shardId) headOption cluster match {
        case Some(_) => (cluster,Unchanged)
        case None => ((generateEntryActorNodeLens(memberId,serviceType) composeLens shardLens modify(shards => ActorNode(shardId,s"shard-$shardId",NodeRef(generateEntryNodeId(memberId,serviceType),EntryActor),Shard) :: shards))(cluster),NodeAdded(NodeRef(shardId,Actor)))
      }
    }


  def ensureRoutee(memberId:String, serviceType:ServiceType,routeeId:String): State[ClusterNode, UpdateResult] =
    State[ClusterNode, UpdateResult] { cluster =>
      generateEntryActorNodeLens(memberId,serviceType) composeLens routeeLens composeTraversal each composePrism UnsafeSelect.unsafeSelect(r => r.uniqueId == routeeId) headOption cluster match {
        case Some(_) => (cluster,Unchanged)
        case None => ((generateEntryActorNodeLens(memberId, serviceType) composeLens routeeLens modify(routees => ActorNode(routeeId, "routee",NodeRef(generateEntryNodeId(memberId,serviceType),EntryActor),Routee,List.empty[ActorNode],0) :: routees))(cluster), NodeAdded(NodeRef(routeeId,Actor)))
      }
    }


}
