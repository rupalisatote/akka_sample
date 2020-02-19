package sample.cluster.k8s.protocol

import sample.cluster.k8s.model.ClusterNode.NodeProps
import sample.cluster.k8s.model.{NodeRef, ServiceType}


sealed trait EntityRequestMessage extends Product with Serializable {
  def userId:String
}
case class InboxRequestMessage(userId:String) extends EntityRequestMessage

case class InboxRefreshMessage(userId:String, currentSize:Int) extends EntityRequestMessage


sealed trait EntityResponseMessage extends Product with Serializable {
  def userId:String
}
case class InboxResponseMessage(userId:String, taskCount:Int) extends EntityResponseMessage

case class InboxRefreshCompleted(userId:String, size:Int) extends EntityResponseMessage


sealed trait ClusterEvent

case class MemberStarted(memberId:String, memberName:String) extends ClusterEvent
case class MemberStopped(memberId:String) extends ClusterEvent

case class ServiceStarted(memberId:String, serviceType:ServiceType) extends ClusterEvent
case class ServiceStopped(memberId:String,serviceId:String) extends ClusterEvent

case class EntityStarted(memberId:String, serviceType:ServiceType, shardId:String, entityId:String, entityName:String) extends ClusterEvent
case class EntityStopped(memberId:String, serviceType:ServiceType, shardId:String, entityId:String) extends ClusterEvent

case class RouteeStarted(memberId:String, serviceType:ServiceType, routeeId:String, routeeName:String) extends ClusterEvent
case class RouteeStopped(memberId:String, serviceType:ServiceType, routeeId:String) extends ClusterEvent

case class HttpServiceStarted(memberId:String, serviceId:String) extends ClusterEvent
case class HttpServiceStopped(memberId:String, serviceId:String) extends ClusterEvent

case class EntityStateChanged(memberId:String, serviceType:ServiceType, shardId:String, entityId:String, states:List[String]) extends ClusterEvent
case class EntityWeightChanged(memberId:String, serviceType:ServiceType, shardId:String, entityId:String, size:Int) extends ClusterEvent
case class RouteeWeightChanged(memberId:String, serviceType:ServiceType, routeeId:String, totalRequests:Int) extends ClusterEvent
case class SingletonWeightChanged(memberId:String, serviceType: ServiceType, size: Int) extends ClusterEvent


sealed trait UpdateResult
case class NotFound(ref:NodeRef) extends UpdateResult
case class AlreadyExists(ref:NodeRef) extends UpdateResult
case object Success extends UpdateResult
case class NodeAdded(ref:NodeRef) extends UpdateResult
case class NodeUpdated(ref:NodeRef, attr:List[NodeProps]) extends UpdateResult
case class NodeRemoved(ref:NodeRef) extends UpdateResult
case class EntityAdded(shardId:NodeRef,entityId:NodeRef) extends UpdateResult
case class Error(message:String) extends UpdateResult
case object Unchanged extends UpdateResult
case class ProcessingResult(event:ClusterEvent, effects:List[UpdateResult])





