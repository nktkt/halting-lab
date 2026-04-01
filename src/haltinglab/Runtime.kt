package haltinglab

internal data class StepTransition(
    val nextState: MachineState,
    val terminated: Boolean = false,
    val reason: String? = null,
    val error: String? = null,
)

object Interpreter {
    fun run(
        program: Program,
        maxSteps: Int,
        captureTrace: Boolean = true,
        traceLimit: Int = 80,
    ): RunResult {
        require(maxSteps > 0) { "maxSteps must be positive" }

        var state = MachineState()
        val trace = mutableListOf<TraceFrame>()

        repeat(maxSteps) { step ->
            val instruction = program.instructions.getOrNull(state.pc)
                ?: return RunResult(
                    terminated = true,
                    steps = step,
                    reason = "Program counter moved outside the instruction list",
                    finalState = state,
                    trace = trace.toList(),
                )

            if (captureTrace && trace.size < traceLimit) {
                trace += TraceFrame(
                    step = step,
                    pc = state.pc,
                    lineNumber = instruction.lineNumber,
                    instruction = instruction.render(),
                    registers = state.renderRegisters(),
                )
            }

            val transition = step(program, state, instruction)
            if (transition.error != null) {
                return RunResult(
                    terminated = true,
                    steps = step + 1,
                    reason = "Runtime error",
                    finalState = state,
                    trace = trace.toList(),
                    error = transition.error,
                )
            }

            state = transition.nextState
            if (transition.terminated) {
                return RunResult(
                    terminated = true,
                    steps = step + 1,
                    reason = transition.reason ?: "Terminated",
                    finalState = state,
                    trace = trace.toList(),
                )
            }
        }

        return RunResult(
            terminated = false,
            steps = maxSteps,
            reason = "Reached the step limit without termination",
            finalState = state,
            trace = trace.toList(),
        )
    }

    internal fun step(program: Program, state: MachineState, instruction: Instruction? = null): StepTransition {
        val resolved = instruction ?: program.instructions.getOrNull(state.pc)
            ?: return StepTransition(
                nextState = state,
                terminated = true,
                reason = "Program counter moved outside the instruction list",
            )

        return when (resolved) {
            is Instruction.Set -> {
                val nextValue = state.read(resolved.value)
                StepTransition(state.write(resolved.register, nextValue).copy(pc = state.pc + 1))
            }
            is Instruction.Add -> {
                val nextValue = (state.registers[resolved.register] ?: 0L) + state.read(resolved.value)
                StepTransition(state.write(resolved.register, nextValue).copy(pc = state.pc + 1))
            }
            is Instruction.Sub -> {
                val nextValue = (state.registers[resolved.register] ?: 0L) - state.read(resolved.value)
                StepTransition(state.write(resolved.register, nextValue).copy(pc = state.pc + 1))
            }
            is Instruction.Mul -> {
                val nextValue = (state.registers[resolved.register] ?: 0L) * state.read(resolved.value)
                StepTransition(state.write(resolved.register, nextValue).copy(pc = state.pc + 1))
            }
            is Instruction.Mod -> {
                val divisor = state.read(resolved.value)
                if (divisor == 0L) {
                    StepTransition(
                        nextState = state,
                        terminated = true,
                        error = "Modulo by zero at line ${resolved.lineNumber}",
                    )
                } else {
                    val nextValue = (state.registers[resolved.register] ?: 0L) % divisor
                    StepTransition(state.write(resolved.register, nextValue).copy(pc = state.pc + 1))
                }
            }
            is Instruction.Copy -> {
                val nextValue = state.read(resolved.value)
                StepTransition(state.write(resolved.register, nextValue).copy(pc = state.pc + 1))
            }
            is Instruction.Jump -> StepTransition(state.copy(pc = resolved.target))
            is Instruction.JumpIfZero -> {
                val nextPc = if (state.read(resolved.value) == 0L) resolved.target else state.pc + 1
                StepTransition(state.copy(pc = nextPc))
            }
            is Instruction.JumpIfNotZero -> {
                val nextPc = if (state.read(resolved.value) != 0L) resolved.target else state.pc + 1
                StepTransition(state.copy(pc = nextPc))
            }
            is Instruction.Output -> {
                val nextState = state.appendOutput(state.read(resolved.value).toString()).copy(pc = state.pc + 1)
                StepTransition(nextState)
            }
            is Instruction.Nop -> StepTransition(state.copy(pc = state.pc + 1))
            is Instruction.Halt -> StepTransition(
                nextState = state.copy(pc = state.pc + 1),
                terminated = true,
                reason = "Encountered HALT",
            )
        }
    }
}

object BoundedAnalyzer {
    fun analyze(program: Program, maxSteps: Int): AnalysisResult {
        require(maxSteps > 0) { "maxSteps must be positive" }

        var state = MachineState()
        val observations = mutableListOf<String>()
        val seen = linkedMapOf<String, Int>()

        repeat(maxSteps) { step ->
            val instruction = program.instructions.getOrNull(state.pc)
                ?: return AnalysisResult(
                    verdict = Verdict.HALTS,
                    steps = step,
                    reason = "Program counter moved outside the instruction list",
                    observations = observations.toList(),
                    finalState = state,
                )

            val signature = signatureOf(state)
            val firstSeen = seen.putIfAbsent(signature, step)
            if (firstSeen != null) {
                observations += "Repeated machine state first seen at step $firstSeen"
                return AnalysisResult(
                    verdict = Verdict.LOOPS,
                    steps = step,
                    reason = "Detected a repeated state at instruction ${state.pc}",
                    observations = observations.toList(),
                    finalState = state,
                )
            }

            when (instruction) {
                is Instruction.Jump -> if (instruction.target == state.pc) {
                    observations += "Instruction ${state.pc} jumps to itself"
                }
                is Instruction.JumpIfZero -> if (instruction.target <= state.pc) {
                    observations += "Backward conditional jump from ${state.pc} to ${instruction.target}"
                }
                is Instruction.JumpIfNotZero -> if (instruction.target <= state.pc) {
                    observations += "Backward conditional jump from ${state.pc} to ${instruction.target}"
                }
                else -> Unit
            }

            val transition = Interpreter.step(program, state, instruction)
            if (transition.error != null) {
                observations += transition.error
                return AnalysisResult(
                    verdict = Verdict.UNKNOWN,
                    steps = step + 1,
                    reason = "Runtime error interrupted the analysis",
                    observations = observations.toList(),
                    finalState = state,
                )
            }

            state = transition.nextState
            if (transition.terminated) {
                return AnalysisResult(
                    verdict = Verdict.HALTS,
                    steps = step + 1,
                    reason = transition.reason ?: "Encountered HALT",
                    observations = observations.toList(),
                    finalState = state,
                )
            }
        }

        observations += "No halting witness or repeated state was found within $maxSteps steps"
        return AnalysisResult(
            verdict = Verdict.UNKNOWN,
            steps = maxSteps,
            reason = "The bounded analysis exhausted its step budget",
            observations = observations.toList(),
            finalState = state,
        )
    }

    private fun signatureOf(state: MachineState): String {
        val registers = if (state.registers.isEmpty()) {
            "-"
        } else {
            state.registers.entries.joinToString(",") { "${it.key}=${it.value}" }
        }
        val output = when {
            state.output.isEmpty() -> "-"
            state.output.size <= 4 -> state.output.joinToString("|")
            else -> "${state.output.size}:${state.output.takeLast(4).joinToString("|")}"
        }
        return "${state.pc};$registers;$output"
    }
}
