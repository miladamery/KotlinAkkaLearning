package akkadocumentation.chatexample

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.Terminated
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akkadocumentation.chatexample.roomcommand.GetSession
import akkadocumentation.chatexample.roomcommand.RoomCommand
import akkadocumentation.chatexample.sessioncommand.NotifyClient
import akkadocumentation.chatexample.sessioncommand.PostMessage
import akkadocumentation.chatexample.sessioncommand.SessionCommand
import akkadocumentation.chatexample.sessionevent.MessagePosted
import akkadocumentation.chatexample.sessionevent.SessionDenied
import akkadocumentation.chatexample.sessionevent.SessionEvent
import akkadocumentation.chatexample.sessionevent.SessionGranted
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class FunctionalStyleChatRoom private constructor(val context: ActorContext<RoomCommand>) {

    private data class PublishSessionMessage(val screenName: String, val message: String) : RoomCommand
    private class Session {
        companion object {
            fun create(
                room: ActorRef<RoomCommand>,
                screenName: String,
                client: ActorRef<SessionEvent>
            ): Behavior<SessionCommand> {
                return Behaviors.receive(SessionCommand::class.java)
                    .onMessage(PostMessage::class.java) { postMessage -> onPostMessage(room, screenName, postMessage) }
                    .onMessage(NotifyClient::class.java) { notification -> onNotifyClient(client, notification) }
                    .build()
            }

            private fun onPostMessage(
                room: ActorRef<RoomCommand>,
                screenName: String,
                post: PostMessage
            ): Behavior<SessionCommand> {
                // from client, publish to others via the room
                room.tell(PublishSessionMessage(screenName, post.message))
                return Behaviors.same()
            }

            private fun onNotifyClient(
                client: ActorRef<SessionEvent>,
                notification: NotifyClient
            ): Behavior<SessionCommand> {
                // published from room
                client.tell(notification.message)
                return Behaviors.same()
            }
        }


    }

    companion object {
        fun create(): Behavior<RoomCommand> {
            return Behaviors.setup { ctx -> FunctionalStyleChatRoom(ctx).chatRoom(listOf()) }
        }
    }

    private fun chatRoom(sessions: List<ActorRef<SessionCommand>>): Behavior<RoomCommand> {
        return Behaviors.receive(RoomCommand::class.java)
            .onMessage(GetSession::class.java) { getSession -> onGetSession(sessions, getSession) }
            .onMessage(PublishSessionMessage::class.java) { pub -> onPublishSessionMessage(sessions, pub) }
            .build()
    }

    private fun onGetSession(sessions: List<ActorRef<SessionCommand>>, getSession: GetSession): Behavior<RoomCommand> {
        val client = getSession.replyTo
        val ses = context.spawn(
            Session.create(context.self, getSession.screenName, client),
            URLEncoder.encode(getSession.screenName, StandardCharsets.UTF_8.name())
        )
        // narrow to only expose PostMessage
        client.tell(SessionGranted(ses.narrow()))
        val newSessions = sessions.toMutableList()
        newSessions.add(ses)
        return chatRoom(newSessions)
    }

    private fun onPublishSessionMessage(
        sessions: List<ActorRef<SessionCommand>>,
        pub: PublishSessionMessage
    ): Behavior<RoomCommand> {
        val notification = NotifyClient(MessagePosted(pub.screenName, pub.message))
        sessions.forEach { it.tell(notification) }
        return Behaviors.same()
    }
}

class Gabbler private constructor(private val context: ActorContext<SessionEvent>) {
    companion object {
        fun create(): Behavior<SessionEvent> {
            return Behaviors.setup { ctx -> Gabbler(ctx).behavior() }
        }
    }

    private fun behavior(): Behavior<SessionEvent> {
        return Behaviors.receive(SessionEvent::class.java)
            .onMessage(SessionDenied::class.java) { msg -> onSessionDenied(msg) }
            .onMessage(SessionGranted::class.java) { msg -> onSessionGranted(msg) }
            .onMessage(MessagePosted::class.java) { msg -> onMessagePosted(msg) }
            .build()
    }

    private fun onSessionDenied(message: SessionDenied): Behavior<SessionEvent?>? {
        context.log.info("cannot start chat room session: {}", message.reason)
        return Behaviors.stopped()
    }

    private fun onSessionGranted(message: SessionGranted): Behavior<SessionEvent?>? {
        message.handle.tell(PostMessage("Hello World!"))
        return Behaviors.same()
    }

    private fun onMessagePosted(message: MessagePosted): Behavior<SessionEvent?>? {
        context
            .log
            .info("message has been posted by '{}': {}", message.screenName, message.message)
        return Behaviors.stopped()
    }
}

class Main() {
    companion object {
        fun create(): Behavior<Unit> {
            return Behaviors.setup { context ->
                val chatRoom = context.spawn(FunctionalStyleChatRoom.create(), "chatRoom")
                val gabbler = context.spawn(Gabbler.create(), "gabbler")
                context.watch(gabbler)
                chatRoom.tell(GetSession("Ol' Gabbler", gabbler))
                Behaviors.receive(Unit::class.java)
                    .onSignal(Terminated::class.java) {Behaviors.stopped()}
                    .build()
            }
        }
    }
}

fun main() {
    val system = ActorSystem.create(Main.create(), "chatRoomDemo")
}