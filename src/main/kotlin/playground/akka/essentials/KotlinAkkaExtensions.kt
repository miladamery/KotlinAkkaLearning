package playground.akka.essentials

import akka.actor.ActorRef

infix fun ActorRef.tell(msg: Any) {
    tell(msg, ActorRef.noSender())
}