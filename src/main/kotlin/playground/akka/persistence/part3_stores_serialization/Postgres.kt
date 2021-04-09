package playground.akka.persistence.part3_stores_serialization

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.config.ConfigFactory

class Postgres {
}

fun main() {
    val system = ActorSystem.create("postgresStoresSystem", ConfigFactory.load().getConfig("postgresDemo"))
    val persistentActor = system.actorOf(Props.create(SimplePersistentActor::class.java), "simplePostgresPersistentActor")

    /*(1..10).forEach {
        persistentActor.tell("I Love Akka [$it]", ActorRef.noSender())
    }
    persistentActor.tell("print", ActorRef.noSender())
    persistentActor.tell("snap", ActorRef.noSender())

    (11..20).forEach {
        persistentActor.tell("I Love Akka [$it]", ActorRef.noSender())
    }*/

    persistentActor.tell("print", ActorRef.noSender())
}