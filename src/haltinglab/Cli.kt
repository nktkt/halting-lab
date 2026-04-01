package haltinglab

class CliApp(
    private val corpusRepository: CorpusRepository = CorpusRepository(),
    private val guideRepository: GuideRepository = GuideRepository(),
) {
    fun run(args: Array<String>): Int {
        val command = args.firstOrNull()?.lowercase() ?: return printOverview()

        return try {
            when (command) {
                "help" -> printOverview()
                "list" -> listSamples(args.drop(1))
                "search" -> searchSamples(args.drop(1))
                "show" -> showSample(args.drop(1))
                "run" -> runSample(args.drop(1))
                "analyze" -> analyzeSample(args.drop(1))
                "stats" -> printStats()
                "guide" -> showGuide(args.drop(1))
                "guide-topics" -> listGuideTopics()
                else -> {
                    println("Unknown command: $command")
                    println()
                    printOverview()
                }
            }
        } catch (error: IllegalArgumentException) {
            println("Invalid input: ${error.message}")
            1
        } catch (error: IllegalStateException) {
            println("State error: ${error.message}")
            1
        }
    }

    private fun printOverview(): Int {
        println("Halting Lab")
        println("A Kotlin CLI for experimenting with bounded halting analysis.")
        println()
        println("Commands:")
        println("  help")
        println("  list [limit]")
        println("  search <term>")
        println("  show <sample-name>")
        println("  run <sample-name> [max-steps]")
        println("  analyze <sample-name> [max-steps]")
        println("  stats")
        println("  guide [topic-key]")
        println("  guide-topics")
        println()
        println("The CLI intentionally exposes UNKNOWN as a possible analysis outcome.")
        return 0
    }

    private fun listSamples(arguments: List<String>): Int {
        val limit = arguments.firstOrNull()?.toIntOrNull() ?: 20
        val samples = corpusRepository.loadAll()
        println("Samples: ${samples.size}")
        println()
        samples.take(limit).forEach { sample ->
            println("${sample.name.padEnd(22)} ${sample.category.padEnd(14)} ${sample.description}")
        }
        if (samples.size > limit) {
            println()
            println("Showing $limit of ${samples.size} samples")
        }
        return 0
    }

    private fun searchSamples(arguments: List<String>): Int {
        require(arguments.isNotEmpty()) { "search requires a term" }
        val query = arguments.joinToString(" ")
        val matches = corpusRepository.search(query)
        println("Matches for '$query': ${matches.size}")
        println()
        matches.take(40).forEach { sample ->
            println("${sample.name.padEnd(22)} ${sample.category.padEnd(14)} ${sample.description}")
        }
        if (matches.size > 40) {
            println()
            println("Showing 40 of ${matches.size} matches")
        }
        return 0
    }

    private fun showSample(arguments: List<String>): Int {
        val sample = requireSample(arguments)
        println("Name: ${sample.name}")
        println("Category: ${sample.category}")
        println("Origin: ${sample.origin}")
        println("Description: ${sample.description}")
        println()
        println(sample.source)
        return 0
    }

    private fun runSample(arguments: List<String>): Int {
        val sample = requireSample(arguments)
        val maxSteps = arguments.getOrNull(1)?.toIntOrNull() ?: 100
        val program = ProgramParser.parse(sample)
        val result = Interpreter.run(program, maxSteps)

        println("Run: ${sample.name}")
        println("Description: ${sample.description}")
        println("Steps: ${result.steps}")
        println("Terminated: ${result.terminated}")
        println("Reason: ${result.reason}")
        if (result.error != null) {
            println("Error: ${result.error}")
        }
        println("Registers: ${result.finalState.renderRegisters()}")
        println("Output: ${if (result.finalState.output.isEmpty()) "<empty>" else result.finalState.output.joinToString(", ")}")
        println()
        println("Trace:")
        result.trace.forEach { frame ->
            println(
                frame.step.toString().padStart(4) +
                    "  pc=" + frame.pc.toString().padStart(3) +
                    "  line=" + frame.lineNumber.toString().padStart(3) +
                    "  " + frame.instruction.padEnd(24) +
                    "  " + frame.registers
            )
        }
        return 0
    }

    private fun analyzeSample(arguments: List<String>): Int {
        val sample = requireSample(arguments)
        val maxSteps = arguments.getOrNull(1)?.toIntOrNull() ?: 200
        val program = ProgramParser.parse(sample)
        val metrics = ProgramMetrics.from(program)
        val analysis = BoundedAnalyzer.analyze(program, maxSteps)

        println("Analysis: ${sample.name}")
        println("Category: ${sample.category}")
        println("Description: ${sample.description}")
        println()
        println("Verdict: ${analysis.verdict}")
        println("Steps: ${analysis.steps}")
        println("Reason: ${analysis.reason}")
        println("Final registers: ${analysis.finalState.renderRegisters()}")
        println()
        println("Program metrics:")
        println("  instructions=${metrics.instructions}")
        println("  labels=${metrics.labels}")
        println("  jumps=${metrics.jumps}")
        println("  backwardJumps=${metrics.backwardJumps}")
        println("  outputs=${metrics.outputs}")
        println("  halts=${metrics.halts}")
        println()
        println("Observations:")
        if (analysis.observations.isEmpty()) {
            println("  <none>")
        } else {
            analysis.observations.forEach { println("  $it") }
        }
        return 0
    }

    private fun printStats(): Int {
        val samples = corpusRepository.loadAll()
        val parsed = samples.map(ProgramParser::parse)
        val categoryCounts = samples.groupingBy { it.category }.eachCount().toSortedMap()
        val averageInstructions = if (parsed.isEmpty()) 0.0 else parsed.map { it.instructions.size }.average()
        val totalInstructions = parsed.sumOf { it.instructions.size }
        val totalLabels = parsed.sumOf { it.labels.size }
        val guides = guideRepository.loadAll()

        println("Workspace statistics")
        println()
        println("Corpus samples: ${samples.size}")
        println("Guide topics: ${guides.size}")
        println("Total parsed instructions: $totalInstructions")
        println("Total parsed labels: $totalLabels")
        println("Average instructions per sample: ${"%.2f".format(averageInstructions)}")
        println()
        println("Categories:")
        categoryCounts.forEach { (category, count) ->
            println("  ${category.padEnd(16)} $count")
        }
        return 0
    }

    private fun showGuide(arguments: List<String>): Int {
        val topics = guideRepository.loadAll()
        if (arguments.isEmpty()) {
            println("Guide topics: ${topics.size}")
            println()
            topics.take(40).forEach { topic ->
                println("${topic.key.padEnd(24)} ${topic.title}")
            }
            if (topics.size > 40) {
                println()
                println("Showing 40 of ${topics.size} topics")
            }
            return 0
        }

        val key = arguments.first()
        val topic = guideRepository.find(key)
        requireNotNull(topic) { "Unknown guide topic '$key'" }
        println("${topic.title} (${topic.key})")
        println("Origin: ${topic.origin}")
        println()
        println(topic.body)
        return 0
    }

    private fun listGuideTopics(): Int {
        val topics = guideRepository.loadAll()
        println("Guide topics: ${topics.size}")
        println()
        topics.forEach { topic ->
            println("${topic.key.padEnd(24)} ${topic.title}")
        }
        return 0
    }

    private fun requireSample(arguments: List<String>): SampleEntry {
        require(arguments.isNotEmpty()) { "sample name is required" }
        val name = arguments.first()
        return requireNotNull(corpusRepository.find(name)) { "Unknown sample '$name'" }
    }
}
