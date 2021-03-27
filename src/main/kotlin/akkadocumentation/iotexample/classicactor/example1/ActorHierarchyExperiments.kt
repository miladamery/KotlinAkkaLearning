package akkadocumentation.iotexample.classicactor.example1

import akka.actor.ActorRef
import akka.actor.ActorSystem

fun main() {
    val testSystem = ActorSystem.create("testSystem")

    val mainActor = testSystem.actorOf(Main.create())
    mainActor.tell("start", ActorRef.noSender())
}