package com.sd.laborator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.messaging.Processor
import org.springframework.integration.annotation.Transformer
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.DateFormat
import java.text.SimpleDateFormat

@EnableBinding(Processor::class)
@SpringBootApplication
class SpringDataFlowTimeProcessorApplication {
    @Transformer(inputChannel = Processor.INPUT, outputChannel = Processor.OUTPUT)
    fun transform(inputString: String): Any? {
        var comenzi = inputString.split("--")
        var rezultatComanda = "";
        if(comenzi[0] == "fortune")
        {
            rezultatComanda = ExecuteCommand("fortune")
        }
        else if(comenzi[0] == "cowsay")
        {
            rezultatComanda = ExecuteCommand("cowsay " + comenzi[comenzi.size - 1])
        }
        else if(comenzi[0] == "lolcat" )
        {
            File("argumente.txt").writeText(comenzi[comenzi.size-1])
            rezultatComanda = ExecuteCommand("lolcat " + "argumente.txt")
        }
        else
        {
            //EROARE
        }
        var result = "";
        for(i in 1 until comenzi.size)
        {
            result = result + comenzi[i]+"--"
        }
        result = result + "$rezultatComanda"
        return result
    }

    fun ExecuteCommand(cmd:String):String
    {
        var output: String = ""
        try{
            var line:String?
            val p = Runtime.getRuntime().exec(cmd)
            val `in` = BufferedReader(InputStreamReader(p.inputStream))
            while(`in`.readLine().also {line = it} != null){
                output+=line+"\n"
            }
            `in`.close()

        }catch (ex:Exception){
            ex.printStackTrace()
        }
        return output
    }
}

fun main(args: Array<String>) {
    runApplication<SpringDataFlowTimeProcessorApplication>(*args)
}