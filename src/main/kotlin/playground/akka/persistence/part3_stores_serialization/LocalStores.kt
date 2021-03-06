package playground.akka.persistence.part3_stores_serialization

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.Logging
import akka.persistence.*
import com.typesafe.config.ConfigFactory

class LocalStores {
}

fun main() {
    val system = ActorSystem.create("localStoresSystem", ConfigFactory.load().getConfig("localStores"))
    val persistentActor = system.actorOf(Props.create(SimplePersistentActor::class.java), "simplePersistentActor")

    (1..10).forEach {
        persistentActor.tell("I Love Akka [$it]", ActorRef.noSender())
    }
    persistentActor.tell("print", ActorRef.noSender())
    persistentActor.tell("snap", ActorRef.noSender())

    (11..20).forEach {
        persistentActor.tell("I Love Akka [$it]", ActorRef.noSender())
    }
}