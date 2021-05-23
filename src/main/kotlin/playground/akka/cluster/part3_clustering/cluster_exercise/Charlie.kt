package playground.akka.cluster.part3_clustering.cluster_exercise

class Charlie {
}

fun main() {
    val alice = ChatApp("Charlie", 2553)
    alice.readLineAndSendToCluster()
}