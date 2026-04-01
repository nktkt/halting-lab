package haltinglab

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val exitCode = CliApp().run(args)
    if (exitCode != 0) {
        exitProcess(exitCode)
    }
}
