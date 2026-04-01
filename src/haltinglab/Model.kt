package haltinglab

enum class Verdict {
    HALTS,
    LOOPS,
    UNKNOWN,
}

sealed interface Operand {
    data class Literal(val value: Long) : Operand
    data class Register(val name: String) : Operand

    fun render(): String = when (this) {
        is Literal -> value.toString()
        is Register -> name
    }
}

sealed interface Instruction {
    val lineNumber: Int
    val raw: String

    data class Set(
        val register: String,
        val value: Operand,
        override val lineNumber: Int,
        override val raw: String,
    ) : Instruction

    data class Add(
        val register: String,
        val value: Operand,
        override val lineNumber: Int,
        override val raw: String,
    ) : Instruction

    data class Sub(
        val register: String,
        val value: Operand,
        override val lineNumber: Int,
        override val raw: String,
    ) : Instruction

    data class Mul(
        val register: String,
        val value: Operand,
        override val lineNumber: Int,
        override val raw: String,
    ) : Instruction

    data class Mod(
        val register: String,
        val value: Operand,
        override val lineNumber: Int,
        override val raw: String,
    ) : Instruction

    data class Copy(
        val register: String,
        val value: Operand,
        override val lineNumber: Int,
        override val raw: String,
    ) : Instruction

    data class Jump(
        val target: Int,
        val label: String,
        override val lineNumber: Int,
        override val raw: String,
    ) : Instruction

    data class JumpIfZero(
        val value: Operand,
        val target: Int,
        val label: String,
        override val lineNumber: Int,
        override val raw: String,
    ) : Instruction

    data class JumpIfNotZero(
        val value: Operand,
        val target: Int,
        val label: String,
        override val lineNumber: Int,
        override val raw: String,
    ) : Instruction

    data class Output(
        val value: Operand,
        override val lineNumber: Int,
        override val raw: String,
    ) : Instruction

    data class Nop(
        override val lineNumber: Int,
        override val raw: String,
    ) : Instruction

    data class Halt(
        override val lineNumber: Int,
        override val raw: String,
    ) : Instruction
}

data class Program(
    val name: String,
    val category: String,
    val description: String,
    val source: String,
    val instructions: List<Instruction>,
    val labels: Map<String, Int>,
) {
    val instructionCount: Int
        get() = instructions.size
}

data class MachineState(
    val pc: Int = 0,
    val registers: Map<String, Long> = sortedMapOf(),
    val output: List<String> = emptyList(),
) {
    fun read(operand: Operand): Long = when (operand) {
        is Operand.Literal -> operand.value
        is Operand.Register -> registers[operand.name] ?: 0L
    }

    fun write(register: String, value: Long): MachineState {
        val next = registers.toMutableMap()
        if (value == 0L) {
            next.remove(register)
        } else {
            next[register] = value
        }
        return copy(registers = next.toSortedMap())
    }

    fun appendOutput(value: String): MachineState = copy(output = output + value)

    fun renderRegisters(limit: Int = 12): String {
        if (registers.isEmpty()) {
            return "{}"
        }
        val shown = registers.entries.take(limit).joinToString(", ") { "${it.key}=${it.value}" }
        return if (registers.size <= limit) "{$shown}" else "{$shown, ...}"
    }
}

data class TraceFrame(
    val step: Int,
    val pc: Int,
    val lineNumber: Int,
    val instruction: String,
    val registers: String,
)

data class RunResult(
    val terminated: Boolean,
    val steps: Int,
    val reason: String,
    val finalState: MachineState,
    val trace: List<TraceFrame>,
    val error: String? = null,
)

data class AnalysisResult(
    val verdict: Verdict,
    val steps: Int,
    val reason: String,
    val observations: List<String>,
    val finalState: MachineState,
)

data class SampleEntry(
    val name: String,
    val category: String,
    val description: String,
    val source: String,
    val origin: String,
)

data class GuideTopic(
    val key: String,
    val title: String,
    val body: String,
    val origin: String,
)

data class ProgramMetrics(
    val instructions: Int,
    val labels: Int,
    val jumps: Int,
    val backwardJumps: Int,
    val outputs: Int,
    val halts: Int,
) {
    companion object {
        fun from(program: Program): ProgramMetrics {
            var jumps = 0
            var backwardJumps = 0
            var outputs = 0
            var halts = 0
            program.instructions.forEachIndexed { index, instruction ->
                when (instruction) {
                    is Instruction.Jump -> {
                        jumps += 1
                        if (instruction.target <= index) {
                            backwardJumps += 1
                        }
                    }
                    is Instruction.JumpIfZero -> {
                        jumps += 1
                        if (instruction.target <= index) {
                            backwardJumps += 1
                        }
                    }
                    is Instruction.JumpIfNotZero -> {
                        jumps += 1
                        if (instruction.target <= index) {
                            backwardJumps += 1
                        }
                    }
                    is Instruction.Output -> outputs += 1
                    is Instruction.Halt -> halts += 1
                    else -> Unit
                }
            }
            return ProgramMetrics(
                instructions = program.instructions.size,
                labels = program.labels.size,
                jumps = jumps,
                backwardJumps = backwardJumps,
                outputs = outputs,
                halts = halts,
            )
        }
    }
}

fun Instruction.render(): String = when (this) {
    is Instruction.Set -> "set $register ${value.render()}"
    is Instruction.Add -> "add $register ${value.render()}"
    is Instruction.Sub -> "sub $register ${value.render()}"
    is Instruction.Mul -> "mul $register ${value.render()}"
    is Instruction.Mod -> "mod $register ${value.render()}"
    is Instruction.Copy -> "copy $register ${value.render()}"
    is Instruction.Jump -> "jump $label"
    is Instruction.JumpIfZero -> "jz ${value.render()} $label"
    is Instruction.JumpIfNotZero -> "jnz ${value.render()} $label"
    is Instruction.Output -> "out ${value.render()}"
    is Instruction.Nop -> "nop"
    is Instruction.Halt -> "halt"
}
