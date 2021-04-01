package akkadocumentation.chatexample.sessioncommand

import akkadocumentation.chatexample.sessionevent.MessagePosted

data class NotifyClient(val message: MessagePosted) : SessionCommand
