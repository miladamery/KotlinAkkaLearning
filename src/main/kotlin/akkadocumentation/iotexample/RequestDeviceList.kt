package akkadocumentation.iotexample

import akka.actor.typed.ActorRef

data class RequestDeviceList(val requestId: Long, val groupId: String, val replyTo: ActorRef<ReplyDeviceList>) :
    DeviceManager.Command, DeviceGroup.Command {
}

data class ReplyDeviceList(val requestId: Long, val ids: Set<String>)