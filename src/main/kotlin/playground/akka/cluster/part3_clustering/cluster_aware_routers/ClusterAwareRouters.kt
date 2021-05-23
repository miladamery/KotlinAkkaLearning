package playground.akka.cluster.part3_clustering.cluster_aware_routers

import akka.actor.AbstractActor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.Logging
import akka.event.LoggingAdapter
import akka.routing.FromConfig
import com.typesafe.config.ConfigFactory

class ClusterAwareRouters {
    data class SimpleTask(val contents: String)
    object StartWork
}

fun main() {
    startRouteeNode(2551)
    startRouteeNode(2552)
}

class MasterWithRouter: AbstractActor() {
    private val log: LoggingAdapter = Logging.getLogger(context.system, this)
    private val router = context.actorOf(FromConfig.getInstance().props(Props.create(SimpleRoutee::class.java)), "clusterAwareRouter")
    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(ClusterAwareRouters.StartWork::class.java) {
                log.info("Starting work")
                (1..100).forEach { id ->
                    router.tell(ClusterAwareRouters.SimpleTask("Simple task $id"), self)
                }
            }
            .build()
    }
}

class SimpleRoutee: AbstractActor() {
    private val log: LoggingAdapter = Logging.getLogger(context.system, this)
    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(ClusterAwareRouters.SimpleTask::class.java) {
                log.info("Processing: ${it.contents}")
            }
            .build()
    }

}

fun startRouteeNode(port: Int) {
    val config = ConfigFactory.parseString("""
        akka.remote.artery.canonical.port: $port
    """.trimIndent())
        .withFallback(ConfigFactory.load("part3_clustering/clusterAwareRouters.conf"))
    val system = ActorSystem.create("RTJVMCluster", config)
}