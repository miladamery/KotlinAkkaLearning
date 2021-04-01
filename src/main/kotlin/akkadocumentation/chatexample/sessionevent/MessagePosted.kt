package akkadocumentation.chatexample.sessionevent

data class MessagePosted(val screenName: String, val message: String): SessionEvent
