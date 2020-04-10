import java.text.SimpleDateFormat
import java.util.*



class Message private constructor(val sender: String, val body: String, val timestamp: Date, val nume: String, val telefon: String,val email: String) {
    companion object {
        fun create(sender: String, body: String, nume: String = "none",telefon: String = "none",email: String = "none"): Message {
            return Message(sender, body, Date(),nume,telefon,email)
        }

        fun deserialize(msg: ByteArray): Message {
            val msgString = String(msg)
            val words = msgString.split("_")
            val nume = words[3]
            val email = words[5]
            val telefon = words[4]
            val data = Date(words[0].toLong())
            val sender = words[1]
            val body = words[2]

            return Message(sender, body, data,nume,telefon,email)
        }
    }

    fun serialize(): ByteArray {
        return "${timestamp.time}_${sender}_${body}_${nume}_${telefon}_$email\n".toByteArray()
    }

    override fun toString(): String {
        val dateString = SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(timestamp)
        return "[$dateString]  $nume $telefon $email $sender >>> $body"
    }
}

fun main(args: Array<String>) {
    val msg = Message.create("localhost:4848", "test mesaj")
    println(msg)
    val serialized = msg.serialize()
    val deserialized = Message.deserialize(serialized)
    println(deserialized)
}