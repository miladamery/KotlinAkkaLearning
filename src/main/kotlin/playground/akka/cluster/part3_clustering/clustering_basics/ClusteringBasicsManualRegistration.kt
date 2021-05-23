package playground.akka.cluster.part3_clustering.clustering_basics

import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.Props
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory

class ClusteringBasicsManualRegistration {
}

fun main() {
    val system = ActorSystem.create("RTJVMCluster",
        ConfigFactory.load("part3_clustering/clusteringBasics.conf").getConfig("manualRegistration")
    )
    val cluster = Cluster.get(system)
    // joining seed nodes
    cluster.joinSeedNodes(listOf(
        Address("akka", "RTJVMCluster", "localhost", 2551),
        Address("akka", "RTJVMCluster", "localhost", 2552),
        // equalsTo AddressFromURIString
    ))
    // join another node
    // cluster.join(Address("akka", "RTJVMCluster", "localhost", 49527))
    system.actorOf(Props.create(ClusterSubscriber::class.java), "clusterSubscriber")
}