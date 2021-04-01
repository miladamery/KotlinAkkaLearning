package akkadocumentation.chatexample.sessionevent

import akka.actor.typed.ActorRef
import akkadocumentation.chatexample.sessioncommand.PostMessage

data class SessionGranted(val handle: ActorRef<PostMessage>): SessionEvent
