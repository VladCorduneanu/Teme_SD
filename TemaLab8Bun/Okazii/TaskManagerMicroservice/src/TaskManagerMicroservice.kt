
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.collections.ArrayList
import kotlin.system.exitProcess

class TaskManagerMicroservice {
    private var taskManagerSocket: ServerSocket
    private lateinit var messageProcessorSocket: Socket
    private var receiveMessageObservable: Observable<String>
    private val subscriptions = CompositeDisposable()
    private val bidQueue: Queue<Message> = LinkedList<Message>()
    private val Connections: MutableList<Socket> = mutableListOf()

    companion object Constants {
        const val fileName = "/home/vladcorduneanu/Documents/Facultate/Teme/TemaLab8Bun/Okazii/resources/log.txt"
        const val TASK_MANAGER_PORT = 2100
        const val MONITORING_DURATION :Long = 40_000
    }

    init {
        writeLog("Started TASK MANAGER\n============\n============\n", fileName)
        taskManagerSocket = ServerSocket(TASK_MANAGER_PORT)
        taskManagerSocket.setSoTimeout(MONITORING_DURATION.toInt())
        println("TaskManagerMicroservice se executa pe portul: ${taskManagerSocket.localPort}")
        println("Se asteapta date")

        // se creeaza obiectul Observable cu care se genereaza evenimente cand se primesc oferte de la bidderi
        receiveMessageObservable = Observable.create<String> { emitter ->
            while (true) {
                try {
                    val Connection = taskManagerSocket.accept()
                    Connections.add(Connection)

                    // se citeste mesajul de la bidder de pe socketul TCP
                    val bufferReader = BufferedReader(InputStreamReader(Connection.inputStream))
                    val receivedMessage = bufferReader.readLine()

                    // daca se primeste un mesaj gol (NULL), atunci inseamna ca cealalta parte a socket-ului a fost inchisa
                    if (receivedMessage == null) {
                        // deci subscriber-ul respectiv a fost deconectat
                        bufferReader.close()
                        Connection.close()
                        Connections.remove(Connection)

                        emitter.onError(Exception("Eroare: Bidder-ul ${Connection.port} a fost deconectat."))
                    }

                    // se emite ce s-a citit ca si element in fluxul de mesaje
                    emitter.onNext(receivedMessage)

                } catch (e: SocketTimeoutException) {
                    // daca au trecut cele 15 secunde de la pornirea licitatiei, inseamna ca licitatia s-a incheiat
                    // se emite semnalul Complete pentru a incheia fluxul de oferte
                    emitter.onComplete()
                    break
                }
            }
        }
    }

    private fun receiveMessage()
    {
        subscriptions.add(receiveMessageObservable.subscribeBy (
            onNext = {

                val mesaj = Message.deserialize(it.toByteArray())

                println("S-a primit:" + mesaj)

                var data = mesaj.body.split(" ")
                writeLog("Ora: ${mesaj.timestamp} Mesaj:${mesaj.body}", fileName)

            },
            onComplete = {
                println("Am terminat de observat.")

                writeLog("S-a term observarea",fileName)

                for(it in Connections)
                {
                    it.close();
                }

                taskManagerSocket.close()
                subscriptions.dispose()
            }
        ))
    }
    fun run() {
        receiveMessage()
    }
}

fun main(args: Array<String>) {
    val Microservice = TaskManagerMicroservice()
    Microservice.run()
}

fun writeLog(string: String,file_name: String){
    val file = File(file_name)

    Files.write(file.toPath(),(string+"\n").toByteArray(),StandardOpenOption.APPEND)
}