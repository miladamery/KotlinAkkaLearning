package playground.akka.streams.part5_advanced

import akka.actor.ActorSystem
import akka.stream.KillSwitches
import akka.stream.javadsl.*
import java.time.Duration
import java.util.stream.Stream

class DynamicStreamHandling {
}

fun main() {
    val system = ActorSystem.create("DynamicStreamHandling")

    // #1: Kill Switch
    val killSwitchFlow = KillSwitches.single<Int>()
    val counter = Source
        .fromJavaStream { Stream.iterate(1) { it + 1 } }
        .throttle(1, Duration.ofSeconds(1))
        .log("counter")
    val sink = Sink.ignore<Int>()

    /*val killSwitch = counter.viaMat(killSwitchFlow, Keep.right()).to(sink).run(system)

    system.scheduler.scheduleOnce(
        Duration.ofSeconds(3),
        { killSwitch.shutdown() },
        system.dispatcher
    )*/

    val anotherCounter = Source
        .fromJavaStream { Stream.iterate(1) { it + 1 } }
        .throttle(2, Duration.ofSeconds(1))
        .log("anotherCounter")
    val sharedKillSwitch = KillSwitches.shared("oneButtonToRuleThemAll")
    /*counter.via(sharedKillSwitch.flow()).runWith(Sink.ignore(), system)
    anotherCounter.via(sharedKillSwitch.flow()).runWith(Sink.ignore(), system)

    system.scheduler.scheduleOnce(
        Duration.ofSeconds(3),
        { sharedKillSwitch.shutdown() },
        system.dispatcher
    )*/

    // MergeHub
    val dynamicMerge = MergeHub.of(Int::class.java)
    /*val materializedSink = dynamicMerge.to(Sink.foreach(::println)).run(system)*/

    // use this sink any time we like
    /*Source.range(1, 10).runWith(materializedSink, system)
    counter.runWith(materializedSink, system)*/

    // Broadcast Hub
    // Dynamic Broadcast materialized source can be plugged
    // into any number of graphs any number of times to any number of components
    // same is true for MergeHub sink
    val dynamicBroadcast = BroadcastHub.of(Int::class.java)
    // val materializedSource = Source.range(1, 100).runWith(dynamicBroadcast, system)

    // materializedSource.runWith(Sink.ignore(), system)

    /**
     * Challenge - combine a mergeHub and a broadcastHub
     *
     * A publisher-subscriber component
     */

    val merge = MergeHub.of(String::class.java)
    val bcast = BroadcastHub.of(String::class.java)
    val pubsub = merge.toMat(bcast, Keep.both()).run(system)
    val publisherPort = pubsub.first()
    val subscriberPort = pubsub.second()

    subscriberPort.runWith(Sink.foreach { println("I received: $it") }, system)
    subscriberPort.map { it.length }.runWith(Sink.foreach { println("I got a number: $it") }, system)

    Source.fromIterator { listOf("Akka", "is", "amazing").iterator() }.runWith(publisherPort, system)
    Source.fromIterator { listOf("I", "love", "Scala").iterator() }.runWith(publisherPort, system)
    Source.single("STREEEEEEEAMS").runWith(publisherPort, system)
}