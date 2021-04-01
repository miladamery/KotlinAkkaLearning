package akkadocumentation.chatexample.roomcommand

import akka.actor.typed.ActorRef
import akkadocumentation.chatexample.sessionevent.SessionEvent

data class GetSession(val screenName: String, val replyTo: ActorRef<SessionEvent>): RoomCommand
