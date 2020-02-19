package sample.cluster.k8s.utils

import akka.actor.ActorSystem
import akka.cluster.sharding.ShardRegion
import sample.cluster.k8s.protocol.EntityRequestMessage

object ShardingFunctions {

  class EntityRequestIdExtractor(maxShards:Int) {

    val extractShardId:ShardRegion.ExtractShardId = {
      case ec:EntityRequestMessage =>
        (math.abs(ec.userId.hashCode) % maxShards).toString
    }
    val extractEntityId:ShardRegion.ExtractEntityId = {
      // setting entity id equal to shard id as we want
      // to use a single actor per shard as an entry point
      case ec:EntityRequestMessage => (extractShardId(ec), ec)
    }
  }

  object EntityRequestIdExtractor {

    val maxShardProperty = "max-shards"

    def apply(system:ActorSystem, configPath:String) = {

      val maxShards = system.settings.config.getInt(s"$configPath.$maxShardProperty")
      new EntityRequestIdExtractor(maxShards)
    }

  }


}
