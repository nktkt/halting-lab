package haltinglab

private data class PendingInstruction(
    val operation: String,
    val arguments: List<String>,
    val lineNumber: Int,
    val raw: String,
)

object ProgramParser {
    fun parse(sample: SampleEntry): Program {
        val labels = linkedMapOf<String, Int>()
        val pending = mutableListOf<PendingInstruction>()

        sample.source.lines().forEachIndexed { index, originalLine ->
            val lineNumber = index + 1
            val withoutComment = originalLine.substringBefore("#").trim()
            if (withoutComment.isBlank()) {
                return@forEachIndexed
            }

            var remainder = withoutComment
            while (true) {
                val colonIndex = remainder.indexOf(':')
                if (colonIndex <= 0) {
                    break
                }
                val label = remainder.substring(0, colonIndex).trim()
                if (!isIdentifier(label)) {
                    break
                }
                require(labels.putIfAbsent(label, pending.size) == null) {
                    "Duplicate label '$label' in ${sample.name} at line $lineNumber"
                }
                remainder = remainder.substring(colonIndex + 1).trim()
                if (remainder.isEmpty()) {
                    break
                }
            }

            if (remainder.isBlank()) {
                return@forEachIndexed
            }

            val pieces = remainder.split(Regex("\\s+"))
            val operation = pieces.first().lowercase()
            val arguments = pieces.drop(1)
            pending += PendingInstruction(
                operation = operation,
                arguments = arguments,
                lineNumber = lineNumber,
                raw = originalLine,
            )
        }

        val instructions = pending.map { resolveInstruction(it, labels, sample.name) }
        return Program(
            name = sample.name,
            category = sample.category,
            description = sample.description,
            source = sample.source,
            instructions = instructions,
            labels = labels.toMap(),
        )
    }

    private fun resolveInstruction(
        pending: PendingInstruction,
        labels: Map<String, Int>,
        sampleName: String,
    ): Instruction {
        fun requireArgumentCount(expected: Int) {
            require(pending.arguments.size == expected) {
                "Expected $expected arguments for ${pending.operation} in $sampleName at line ${pending.lineNumber}"
            }
        }

        return when (pending.operation) {
            "set" -> {
                requireArgumentCount(2)
                Instruction.Set(
                    register = pending.arguments[0],
                    value = parseOperand(pending.arguments[1]),
                    lineNumber = pending.lineNumber,
                    raw = pending.raw,
                )
            }
            "add" -> {
                requireArgumentCount(2)
                Instruction.Add(
                    register = pending.arguments[0],
                    value = parseOperand(pending.arguments[1]),
                    lineNumber = pending.lineNumber,
                    raw = pending.raw,
                )
            }
            "sub" -> {
                requireArgumentCount(2)
                Instruction.Sub(
                    register = pending.arguments[0],
                    value = parseOperand(pending.arguments[1]),
                    lineNumber = pending.lineNumber,
                    raw = pending.raw,
                )
            }
            "mul" -> {
                requireArgumentCount(2)
                Instruction.Mul(
                    register = pending.arguments[0],
                    value = parseOperand(pending.arguments[1]),
                    lineNumber = pending.lineNumber,
                    raw = pending.raw,
                )
            }
            "mod" -> {
                requireArgumentCount(2)
                Instruction.Mod(
                    register = pending.arguments[0],
                    value = parseOperand(pending.arguments[1]),
                    lineNumber = pending.lineNumber,
                    raw = pending.raw,
                )
            }
            "copy" -> {
                requireArgumentCount(2)
                Instruction.Copy(
                    register = pending.arguments[0],
                    value = parseOperand(pending.arguments[1]),
                    lineNumber = pending.lineNumber,
                    raw = pending.raw,
                )
            }
            "jump" -> {
                requireArgumentCount(1)
                val label = pending.arguments[0]
                Instruction.Jump(
                    target = resolveLabel(label, labels, sampleName, pending.lineNumber),
                    label = label,
                    lineNumber = pending.lineNumber,
                    raw = pending.raw,
                )
            }
            "jz" -> {
                requireArgumentCount(2)
                val label = pending.arguments[1]
                Instruction.JumpIfZero(
                    value = parseOperand(pending.arguments[0]),
                    target = resolveLabel(label, labels, sampleName, pending.lineNumber),
                    label = label,
                    lineNumber = pending.lineNumber,
                    raw = pending.raw,
                )
            }
            "jnz" -> {
                requireArgumentCount(2)
                val label = pending.arguments[1]
                Instruction.JumpIfNotZero(
                    value = parseOperand(pending.arguments[0]),
                    target = resolveLabel(label, labels, sampleName, pending.lineNumber),
                    label = label,
                    lineNumber = pending.lineNumber,
                    raw = pending.raw,
                )
            }
            "out" -> {
                requireArgumentCount(1)
                Instruction.Output(
                    value = parseOperand(pending.arguments[0]),
                    lineNumber = pending.lineNumber,
                    raw = pending.raw,
                )
            }
            "nop" -> {
                requireArgumentCount(0)
                Instruction.Nop(
                    lineNumber = pending.lineNumber,
                    raw = pending.raw,
                )
            }
            "halt" -> {
                requireArgumentCount(0)
                Instruction.Halt(
                    lineNumber = pending.lineNumber,
                    raw = pending.raw,
                )
            }
            else -> error("Unknown operation '${pending.operation}' in $sampleName at line ${pending.lineNumber}")
        }
    }

    private fun parseOperand(token: String): Operand {
        return token.toLongOrNull()?.let(Operand::Literal)
            ?: run {
                require(isIdentifier(token)) { "Invalid operand '$token'" }
                Operand.Register(token)
            }
    }

    private fun resolveLabel(label: String, labels: Map<String, Int>, sampleName: String, lineNumber: Int): Int {
        return labels[label]
            ?: error("Unknown label '$label' in $sampleName at line $lineNumber")
    }

    private fun isIdentifier(token: String): Boolean {
        return token.isNotBlank() && token.all { it == '_' || it.isLetterOrDigit() }
    }
}
