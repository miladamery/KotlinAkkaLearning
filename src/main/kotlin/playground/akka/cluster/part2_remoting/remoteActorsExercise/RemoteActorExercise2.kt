package playground.akka.cluster.part2_remoting.remoteActorsExercise

import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.config.ConfigFactory

class RemoteActorExercise2 {
}

fun main() {
    val config = ConfigFactory.parseString("""
        akka.remote.artery.canonical.port = 2552
    """.trimIndent())
        .withFallback(ConfigFactory.load("part2_remoting/remoteActorsExercise.conf"))
    val system = ActorSystem.create("WorkerSystem", config)
    (1..5).map {
        system.actorOf(Props.create(WordCountWorker::class.java), "wordCountWorker$it")
    }
}