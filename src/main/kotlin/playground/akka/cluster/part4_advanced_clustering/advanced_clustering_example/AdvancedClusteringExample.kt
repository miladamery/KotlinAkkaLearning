package playground.akka.cluster.part4_advanced_clustering.advanced_clustering_example

import akka.actor.*
import akka.cluster.singleton.ClusterSingletonManager
import akka.cluster.singleton.ClusterSingletonManagerSettings
import akka.event.Logging
import com.typesafe.config.ConfigFactory
import java.io.Serializable
import java.util.*

class AdvancedClusteringExample {
    data class Order(val items: List<String>, val total: Double): Serializable
    data class Transaction(val orderId: Int, val txnId: String, val amount: Double): Serializable
}

/*
    A small payment service - CENTRALIZED
    - maintaining a well-defined transaction ordering
    - interacting with legacy systems
    - etc
 */

class PaymentSystem: AbstractActor() {
    private val log = Logging.getLogger(context.system, this)

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(AdvancedClusteringExample.Transaction::class.java) {
                log.info("Validating transaction: $it")
                // add complex business logic here.
            }
            .build()
    }
}

class OnlineShopCheckout(val paymentSystem: ActorRef): AbstractActor() {
    private val log = Logging.getLogger(context.system, this)
    private var orderId = 0
    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(AdvancedClusteringExample.Order::class.java) {
                log.info("Received order $orderId for amount ${it.total}, sending transaction to validate")
                val newTransaction = AdvancedClusteringExample.Transaction(orderId, UUID.randomUUID().toString(), it.total)
                paymentSystem.tell(newTransaction, self)
                orderId += 1
            }
            .build()
    }
}

fun paymentSystemNode(port: Int) {
    val config = ConfigFactory.parseMap(mutableMapOf("akka.remote.artery.canonical.port" to port))
        .withFallback(ConfigFactory.load("part4_advanced_clustering/clusterSingletonExample.conf"))
    val system = ActorSystem.create("RTJVMCluster", config)

    system.actorOf(
        ClusterSingletonManager.props(
            Props.create(PaymentSystem::class.java),
            PoisonPill::class.java,
            ClusterSingletonManagerSettings.create(system)
        ),
        "paymentSystem"
    )
}