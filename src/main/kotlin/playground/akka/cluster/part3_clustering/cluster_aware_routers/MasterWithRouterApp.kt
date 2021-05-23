package playground.akka.cluster.part3_clustering.cluster_aware_routers

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.routing.ConsistentHashingPool
import com.typesafe.config.ConfigFactory

class MasterWithRouterApp {
}

fun main() {
    val masterConfig = ConfigFactory.load("part3_clustering/clusterAwareRouters.conf")
    val config = masterConfig.getConfig("masterWithRouterApp").withFallback(masterConfig)
    val system = ActorSystem.create("RTJVMCluster", config)
    val masterActor = system.actorOf(Props.create(MasterWithRouter::class.java), "master")
    Thread.sleep(10000)
    masterActor.tell(ClusterAwareRouters.StartWork, ActorRef.noSender())
}