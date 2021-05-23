package playground.akka.cluster.part3_clustering.clustering_example

import akka.actor.*
import akka.cluster.Cluster
import akka.cluster.ClusterEvent
import akka.dispatch.PriorityGenerator
import akka.dispatch.UnboundedPriorityMailbox
import akka.event.Logging
import akka.event.LoggingAdapter
import akka.pattern.Patterns.pipe
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.*


class ClusteringExample {
}

object ClusteringExampleDomain {
    data class ProcessFile(val fileName: String) : Serializable
    data class ProcessLine(val line: String, val aggregator: ActorRef) : Serializable
    data class ProcessLineResult(val count: Int) : Serializable
}

fun main() {
    val master = createNode(2551, "master", Props.create(Master::class.java), "master")
    createNode(2552, "worker", Props.create(Worker::class.java), "worker")
    createNode(2553, "worker", Props.create(Worker::class.java), "worker")

    Thread.sleep(10000)
    master.tell(
        ClusteringExampleDomain.ProcessFile("E:\\training\\KotlinAkkaLearning\\src\\main\\resources\\txt\\lipsum.txt"),
        ActorRef.noSender()
    )
}

fun createNode(port: Int, role: String, props: Props, actorName: String): ActorRef {
    val config = ConfigFactory.parseString(
        """
        akka.cluster.roles = ["$role"]
        akka.remote.artery.canonical.port = $port
    """.trimIndent()
    )
        .withFallback(ConfigFactory.load("part3_clustering/clusteringExample.conf"))
    val system = ActorSystem.create("RTJVMCluster", config)
    return system.actorOf(props, actorName)
}

class ClusterWordCountPriorityMailbox(val settings: ActorSystem.Settings, val config: Config) :
    UnboundedPriorityMailbox(object : PriorityGenerator() {
        override fun gen(message: Any?): Int {
            return when (message) {
                is ClusterEvent.MemberUp -> 0
                else -> 4
            }
        }
    })

class Master : AbstractActor() {
    private val log: LoggingAdapter = Logging.getLogger(context.system, this)
    val cluster: Cluster = Cluster.get(context.system)
    private val workers = mutableMapOf<Address, ActorRef>()
    private val pendingRemoval = mutableMapOf<Address, ActorRef>()

    override fun preStart() {
        cluster.subscribe(self, ClusterEvent.MemberEvent::class.java, ClusterEvent.UnreachableMember::class.java)
    }

    override fun createReceive(): Receive = handleClusterEvents()
        .orElse(handleWorkerRegistration())
        .orElse(handleJob())

    private fun handleClusterEvents(): Receive {
        return receiveBuilder()
            .match(ClusterEvent.MemberUp::class.java) {
                log.info("Member is up: ${it.member().address()}")
                if (it.member().hasRole("worker")) {
                    if (pendingRemoval.containsKey(it.member().address())) {
                        pendingRemoval.remove(it.member().address())
                    } else {
                        val workerSelection = context.actorSelection("${it.member().address()}/user/worker")
                        val workerPair = workerSelection.resolveOne(Duration.ofSeconds(3)).thenApply { actorRef ->
                            it.member().address() to actorRef
                        }
                        pipe(workerPair, context.dispatcher).to(self)
                    }
                }
            }
            .match(ClusterEvent.UnreachableMember::class.java) {
                log.info("Member detected as unreachable: ${it.member().address()}")
                if (it.member().hasRole("worker")) {
                    val workerOption = workers[it.member().address()]
                    workerOption?.let { worker ->
                        pendingRemoval[it.member().address()] = worker
                    }
                }
            }
            .match(ClusterEvent.MemberRemoved::class.java) {
                log.info("Member ${it.member().address()} removed after ${it.previousStatus()}")
                workers.remove(it.member().address())
            }
            .match(ClusterEvent.MemberEvent::class.java) {
                log.info("Another Member event i don't care about: ${it.member()}")
            }
            .build()
    }

    private fun handleWorkerRegistration(): Receive {
        return receiveBuilder()
            .match(Pair::class.java) {
                log.info("Registering worker: $it")
                workers[it.first as Address] = it.second as ActorRef
            }
            .build()
    }

    private fun handleJob(): Receive {
        return receiveBuilder()
            .match(ClusteringExampleDomain.ProcessFile::class.java) {
                val aggregator = context.actorOf(Props.create(Aggregator::class.java), "aggregator")
                Files.lines(Paths.get(it.fileName))
                    .forEach { line ->
                        self.tell(ClusteringExampleDomain.ProcessLine(line, aggregator), self)
                    }
            }
            .match(ClusteringExampleDomain.ProcessLine::class.java) {
                val actualWorkers = (workers - pendingRemoval)
                val workerIndex = Random().nextInt(actualWorkers.size)
                val worker = actualWorkers.values.toTypedArray()[workerIndex]
                worker.tell(it, self)
                Thread.sleep(10)
            }
            .build()
    }

    override fun postStop() {
        cluster.unsubscribe(self)
    }
}

class Worker : AbstractActor() {
    private val log: LoggingAdapter = Logging.getLogger(context.system, this)
    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(ClusteringExampleDomain.ProcessLine::class.java) {
                log.info("Processing: ${it.line}")
                it.aggregator.tell(ClusteringExampleDomain.ProcessLineResult(it.line.split(" ").size), self)
            }
            .build()
    }

}

class Aggregator : AbstractActor() {
    private val log: LoggingAdapter = Logging.getLogger(context.system, this)
    override fun createReceive(): Receive = online(0)
    val a = context.setReceiveTimeout(Duration.ofSeconds(3))

    private fun online(totalCount: Int): Receive {
        return receiveBuilder()
            .match(ClusteringExampleDomain.ProcessLineResult::class.java) {
                context.become(online(totalCount + it.count))
            }
            .match(ReceiveTimeout::class.java) {
                log.info("TOTAL COUTN: $totalCount")
                context.receiveTimeout = Duration.ofDays(10)
            }
            .build()
    }
}