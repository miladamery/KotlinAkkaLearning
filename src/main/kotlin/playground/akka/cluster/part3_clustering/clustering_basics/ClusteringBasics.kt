package playground.akka.cluster.part3_clustering.clustering_basics

import akka.actor.AbstractActor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.ClusterEvent
import akka.event.Logging
import akka.event.LoggingAdapter
import com.typesafe.config.ConfigFactory

class ClusteringBasics {
}

fun main() {
    startCluster(listOf(2551, 2552, 0))
}

fun startCluster(ports: List<Int>) {
    ports.forEach {
        val config = ConfigFactory
            .parseString("akka.remote.artery.canonical.port = $it")
            .withFallback(ConfigFactory.load("part3_clustering/clusteringBasics.conf"))
        val system = ActorSystem.create("RTJVMCluster", config) // all the actor systems in a cluster must have the same name
        system.actorOf(Props.create(ClusterSubscriber::class.java), "clusterSubscriber")

    }
}

class ClusterSubscriber: AbstractActor() {
    private val log: LoggingAdapter = Logging.getLogger(context.system, this)
    val cluster = Cluster.get(context.system)

    override fun preStart() {
        cluster.subscribe(self, ClusterEvent.MemberEvent::class.java, ClusterEvent.UnreachableMember::class.java)
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(ClusterEvent.MemberJoined::class.java) {
                log.info("New member in town: ${it.member().address()}")
            }
            .match(ClusterEvent.MemberUp::class.java) {
                log.info("Let's say welcome to newest member: ${it.member().address()}")
            }
            .match(ClusterEvent.MemberRemoved::class.java) {
                log.info("Poor ${it.member().address()}. it was removed from ${it.previousStatus()}")
            }
            .match(ClusterEvent.UnreachableMember::class.java) {
                log.info("Uh oh, ${it.member().address()} is unreachable")
            }
            .match(ClusterEvent.MemberEvent::class.java) {
                log.info("Another member event: ${it.member()}")
            }
            .build()
    }

    override fun postStop() {
        cluster.unsubscribe(self)
    }
}