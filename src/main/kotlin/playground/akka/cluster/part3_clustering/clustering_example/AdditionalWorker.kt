package playground.akka.cluster.part3_clustering.clustering_example

import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.config.ConfigFactory

class AdditionalWorker {
}

fun main() {
    val config = ConfigFactory.parseString(
        """
        akka.cluster.roles = ["worker"]
        akka.remote.artery.canonical.port = 2555
    """.trimIndent()
    )
        .withFallback(ConfigFactory.load("part3_clustering/clusteringExample.conf"))
    val system = ActorSystem.create("RTJVMCluster", config)
    system.actorOf(Props.create(Worker::class.java), "worker")
}