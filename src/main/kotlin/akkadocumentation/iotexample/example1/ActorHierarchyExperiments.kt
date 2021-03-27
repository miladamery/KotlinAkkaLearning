package akkadocumentation.iotexample.example1

import akka.actor.typed.ActorSystem

fun main() {
    val testSystem = ActorSystem.create(Main.create(), "testSystem")
    testSystem.tell("start")
}