enum class Verdict {
    HALTS,
    LOOPS,
    UNKNOWN,
}

data class State(
    val pc: Int = 0,
    val acc: Int = 0,
)

sealed interface Instruction {
    data class Add(val delta: Int) : Instruction
    data class Jump(val offset: Int) : Instruction
    data class JumpIfZero(val offset: Int) : Instruction
    data object Halt : Instruction
}

data class Program(
    val name: String,
    val instructions: List<Instruction>,
    val initialState: State = State(),
)

data class AnalysisResult(
    val verdict: Verdict,
    val steps: Int,
    val reason: String,
)

object BoundedHaltingAnalyzer {
    fun analyze(program: Program, maxSteps: Int): AnalysisResult {
        require(maxSteps > 0) { "maxSteps must be positive" }

        var state = program.initialState
        val visited = mutableSetOf<State>()

        repeat(maxSteps) { step ->
            val instruction = program.instructions.getOrNull(state.pc)
                ?: return AnalysisResult(
                    verdict = Verdict.HALTS,
                    steps = step,
                    reason = "Program counter moved outside the program",
                )

            if (!visited.add(state)) {
                return AnalysisResult(
                    verdict = Verdict.LOOPS,
                    steps = step,
                    reason = "Reached the same state again",
                )
            }

            state = execute(instruction, state)
            if (instruction is Instruction.Halt) {
                return AnalysisResult(
                    verdict = Verdict.HALTS,
                    steps = step + 1,
                    reason = "Encountered HALT",
                )
            }
        }

        return AnalysisResult(
            verdict = Verdict.UNKNOWN,
            steps = maxSteps,
            reason = "Did not halt or repeat a known state within the step limit",
        )
    }

    private fun execute(instruction: Instruction, state: State): State {
        return when (instruction) {
            is Instruction.Add -> state.copy(pc = state.pc + 1, acc = state.acc + instruction.delta)
            is Instruction.Jump -> state.copy(pc = state.pc + instruction.offset)
            is Instruction.JumpIfZero -> {
                val nextPc = if (state.acc == 0) state.pc + instruction.offset else state.pc + 1
                state.copy(pc = nextPc)
            }
            Instruction.Halt -> state.copy(pc = state.pc + 1)
        }
    }
}

fun main(args: Array<String>) {
    val maxSteps = args.firstOrNull()?.toIntOrNull() ?: 20

    val samples = listOf(
        Program(
            name = "haltsImmediately",
            instructions = listOf(Instruction.Halt),
        ),
        Program(
            name = "simpleLoop",
            instructions = listOf(Instruction.Jump(0)),
        ),
        Program(
            name = "longRunningThenHalts",
            instructions = buildList {
                repeat(50) {
                    add(Instruction.Add(1))
                }
                add(Instruction.Halt)
            },
        ),
        Program(
            name = "countdownLoop",
            instructions = listOf(
                Instruction.JumpIfZero(3),
                Instruction.Add(-1),
                Instruction.Jump(-2),
                Instruction.Halt,
            ),
            initialState = State(acc = 5),
        ),
    )

    println("Bounded halting analysis with maxSteps=$maxSteps")
    println("This is not a solution to the general halting problem.")
    println()

    for (program in samples) {
        val result = BoundedHaltingAnalyzer.analyze(program, maxSteps)
        println("${program.name}: ${result.verdict} after ${result.steps} steps")
        println("  ${result.reason}")
    }

    println()
    println("Why UNKNOWN exists:")
    println("A general Kotlin program may run for a very long time before halting.")
    println("Without a universal algorithm for the halting problem, a practical tool must keep UNKNOWN as an outcome.")
}
