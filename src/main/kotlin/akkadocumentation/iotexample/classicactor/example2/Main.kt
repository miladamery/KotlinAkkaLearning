package akkadocumentation.iotexample.classicactor.example2

import akka.actor.ActorRef
import akka.actor.ActorSystem

fun main() {
    val classicExample2System = ActorSystem.create("classicExample2System")
    val startStopActor1 = classicExample2System.actorOf(StartStopActor1.create(), "StartStopActor1")
    startStopActor1.tell("stop", ActorRef.noSender())
}