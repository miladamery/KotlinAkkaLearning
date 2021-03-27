package playground

import akka.actor.ActorSystem

fun main(args: Array<String>) {
    val actorSystem = ActorSystem.create("HelloAkka")
    println(actorSystem.name())
}