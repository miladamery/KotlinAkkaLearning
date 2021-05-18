package playground.akka.cluster.part2_remoting.remoteActors

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.config.ConfigFactory
import playground.akka.cluster.part2_remoting.SimpleActor

class RemoteActors2 {
}

fun main() {
    val remoteSystem = ActorSystem.create("remoteSystem", ConfigFactory.load("part2_remoting/remoteActors.conf").getConfig("remoteSystem"))
    val remoteSimpleActor = remoteSystem.actorOf(Props.create(SimpleActor::class.java), "remoteSimpleActor")
    remoteSimpleActor.tell("Hello, remote actor", ActorRef.noSender())
}