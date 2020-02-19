package sample.cluster.k8s.model

import org.scalatest.flatspec.AnyFlatSpec
import sample.cluster.k8s.protocol._


class ClusterTreeTests extends AnyFlatSpec {

  val initialTree = ClusterNode.cluster[ClusterNode]("ncf-cluster")
  val inboxService = InboxService(Sharding(100))

  behavior of "Member Node Lens"

  it should "after inserting a member, allow me to retrieve that member node instance from the tree" in {
    val tree = ClusterNode("ncf-cluster", "ncf-cluster", List(MemberNode("member-1", "member-2", NodeRef("ncf-cluster", Cluster), Up)))

    assert(ClusterNode.generateMemberNodeLens("member-1").getAll(tree).head.uniqueId == "member-1")
  }

  behavior of "Service Node Lens"

  it should "after inserting a service, allow me to focus in on that service, given the member id and the service type" in {

    val (treeWithService,result) = ClusterNode.handleClusterEvent(ServiceStarted("member-1", inboxService)).run(initialTree).value

    assert(ClusterNode.generateServiceNodeLens("member-1", inboxService).getAll(treeWithService).head.uniqueId == "member-1/inbox-service")
  }

  behavior of "Entry Actor Lens"

  it should "after inserting a service, allow me to focus in on the entry actor node of that service (according to the expected naming convention)" in {

    val (treeWithService,result) = ClusterNode.handleClusterEvent(ServiceStarted("member-1", inboxService)).run(initialTree).value

    assert(ClusterNode.generateEntryActorNodeLens("member-1", inboxService).getAll(treeWithService).head.uniqueId == "member-1/inbox-service-shard-region")

  }

  behavior of "Shard Actor Lens"


  behavior of "Entity Actor Lens"

  it should "after inserting an entity, allow me to focus in on that entity actor node" in {

    val (treeWithEntity, result) = ClusterNode.handleClusterEvent(EntityStarted("member-1", inboxService, "shard-1", "BE02851", "BE02851")).run(initialTree).value

    assert(ClusterNode.generateEntityNodeLens("member-1", inboxService,"shard-1", "BE02851").getAll(treeWithEntity).head.name == "BE02851")

  }


  behavior of "Cluster Node State Monad"


  it should "create the service's entry id based on the member id and the name of the Service Type" in {
     val serviceType = InboxService(Sharding(100))
     val (updatedTree, result) = ClusterNode.handleClusterEvent(ServiceStarted("member-1", serviceType)).run(initialTree).value
     assert(ClusterNode.generateServiceNodeLens("member-1", serviceType ).getAll(updatedTree).head.uniqueId == "member-1/inbox-service")
  }

  it should "return a NodeAdded result after successfully adding a node to the tree" in {
    val serviceType = InboxService(Sharding(100))
    val (updatedTree, result) = ClusterNode.handleClusterEvent(ServiceStarted("member-1",serviceType)).run(initialTree).value
    assert(result.effects.head.isInstanceOf[NodeAdded])
  }

  it should "return an unchanged tree after trying to insert the same node twice" in {
    val serviceType = InboxService(Sharding(100))
    val (updatedTree, result) = ClusterNode.handleClusterEvent(ServiceStarted("member-1",serviceType)).run(initialTree).value
    val (updatedTree2, result2) = ClusterNode.handleClusterEvent(ServiceStarted("member-1",serviceType)).run(updatedTree).value
    assert(result2.effects.head.isInstanceOf[Unchanged.type])
  }

  it should "automatically create the entry node actor for a singleton service with an id [member-id]/[service-id]-singleton" in {
    val services: List[ServiceType] = List(ProviderHubService(ClusterSingleton),SourceService(Replication(100,2)),InboxService(Sharding(100)))
    val updatedTree = services.foldLeft(initialTree) { (acc:ClusterNode, svc:ServiceType) =>
      ClusterNode.handleClusterEvent(ServiceStarted("member-1", svc)).run(acc).value._1
    }
    services.foreach { svc =>
      assert(ClusterNode.generateEntryActorNodeLens("member-1", svc).getAll(updatedTree.asInstanceOf[ClusterNode]).head.uniqueId == s"member-1/${svc.name}-${svc.strategy.toString}")
    }
  }

  it should "allow for insertion of entities without prior creation of ancestors" in {

      val tree = ClusterNode("ncf-cluster","cluster-node")

      val (updatedTree, result) = ClusterNode.handleClusterEvent(EntityStarted("member-1", InboxService(Sharding(100)),"shard-1","BE02851", "BE02851")).run(tree).value

      assert(updatedTree.children.size == 1)
      assert(updatedTree.children.filter(m => m.uniqueId == "member-1")
        .map(m => m.children.filter(s => s.uniqueId == "member-1/inbox-service")
          .map(s => s.children.filter(s => s.uniqueId == "member-1/inbox-service/inbox-service-shard-region")
          .map(sr => sr.children.filter(sr => sr.uniqueId == "shard-1")
          .map(shard => shard.children.filter(e => e.uniqueId == "BE02851"))))).headOption.isDefined)

  }

  it should "remove any existing entity node from the tree when an entity with the same id is inserted" in {

    val (updatedTree, result) = ClusterNode.handleClusterEvent(EntityStarted("member-1", inboxService,"shard-1","BE02851", "BE02851")).run(initialTree).value
    val (updatedTree2, result2) = ClusterNode.handleClusterEvent(EntityStarted("member-2", inboxService,"shard-10","BE02851", "BE02851")).run(updatedTree).value

    assert(ClusterNode.generateShardNodeLens("member-1", inboxService,"shard-1").getAll(updatedTree2).isEmpty)
    assert(ClusterNode.generateEntityNodeLens("member-2", inboxService,"shard-10", "BE02851").getAll(updatedTree2).head.name == "BE02851")

  }

  it should "increase the weight of an entity node after receiving a Weight Changed event" in {
    val (updatedTree, result) = ClusterNode.handleClusterEvent(EntityStarted("member-1", inboxService,"shard-1","BE02851", "BE02851")).run(initialTree).value

    assert(ClusterNode.generateEntityNodeLens("member-1", inboxService, "shard-1", "BE02851").getAll(updatedTree).head.size == 0)

    val (updatedTree2, result2) = ClusterNode.handleClusterEvent(EntityWeightChanged("member-1", inboxService,"shard-1","BE02851", 3)).run(updatedTree).value

    assert(ClusterNode.generateEntityNodeLens("member-1", inboxService, "shard-1", "BE02851").getAll(updatedTree2).head.size == 3)

  }

  it should "update the states list of an entity after receiving a Entity States Changed event" in {
    val (updatedTree, result) = ClusterNode.handleClusterEvent(EntityStarted("member-1", inboxService,"shard-1","BE02851", "BE02851")).run(initialTree).value

    assert(ClusterNode.generateEntityNodeLens("member-1", inboxService, "shard-1", "BE02851").getAll(updatedTree).head.states.isEmpty)

    val (updatedTree2, result2) = ClusterNode.handleClusterEvent(EntityStateChanged("member-1", inboxService,"shard-1","BE02851", List("refreshing"))).run(updatedTree).value

    assert(ClusterNode.generateEntityNodeLens("member-1", inboxService, "shard-1", "BE02851").getAll(updatedTree2).head.states.head == "refreshing")

  }

}
