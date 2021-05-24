package playground.akka.cluster.part4_advanced_clustering.advanced_clustering_example

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.singleton.ClusterSingletonProxy
import akka.cluster.singleton.ClusterSingletonProxySettings
import com.typesafe.config.ConfigFactory
import java.time.Duration
import java.util.*

class PaymentSystemClient {
}

fun main() {
    val config = ConfigFactory
        .parseMap(mutableMapOf("akka.remote.artery.canonical.port" to 0))
        .withFallback(ConfigFactory.load("part4_advanced_clustering/clusterSingletonExample.conf"))

    val system = ActorSystem.create("RTJVMCluster", config)
    val proxy = system.actorOf(
        ClusterSingletonProxy.props(
            "/user/paymentSystem",
            ClusterSingletonProxySettings.create(system)
        ), "paymentSystemProxy")
    val onlineShopCheckout = system.actorOf(Props.create(OnlineShopCheckout::class.java, proxy))
    system.scheduler.scheduleWithFixedDelay(Duration.ofSeconds(5), Duration.ofSeconds(1),
        {
            val randomOrder = AdvancedClusteringExample.Order(listOf(), Random().nextDouble() * 100)
            onlineShopCheckout.tell(randomOrder, ActorRef.noSender())
    }, system.dispatcher)
}