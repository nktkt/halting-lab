package haltinglab

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

class CorpusRepository(
    private val root: Path = Paths.get("corpus"),
) {
    fun loadAll(): List<SampleEntry> {
        if (!Files.exists(root)) {
            return emptyList()
        }

        return Files.walk(root).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.extension == "pack" }
                .sortedBy { it.toString() }
                .flatMap { parsePack(it).asSequence() }
                .toList()
        }
    }

    fun find(name: String): SampleEntry? = loadAll().firstOrNull { it.name == name }

    fun search(term: String): List<SampleEntry> {
        val query = term.lowercase()
        return loadAll().filter {
            it.name.lowercase().contains(query) ||
                it.category.lowercase().contains(query) ||
                it.description.lowercase().contains(query)
        }
    }

    private fun parsePack(path: Path): List<SampleEntry> {
        val entries = mutableListOf<SampleEntry>()
        var activeName: String? = null
        var activeCategory: String? = null
        var activeDescription: String? = null
        val buffer = mutableListOf<String>()

        fun flush() {
            val name = activeName ?: return
            entries += SampleEntry(
                name = name,
                category = activeCategory ?: "uncategorized",
                description = activeDescription ?: "No description",
                source = buffer.joinToString("\n").trimEnd(),
                origin = path.name,
            )
            activeName = null
            activeCategory = null
            activeDescription = null
            buffer.clear()
        }

        path.toFile().readLines().forEachIndexed { index, line ->
            when {
                line.startsWith("=== ") && line != "=== end" -> {
                    flush()
                    val payload = line.removePrefix("=== ").trim()
                    val parts = payload.split("|").map { it.trim() }
                    require(parts.size >= 3) {
                        "Invalid pack header in ${path.name} at line ${index + 1}"
                    }
                    activeName = parts[0]
                    activeCategory = parts[1]
                    activeDescription = parts.subList(2, parts.size).joinToString(" | ")
                }
                line == "=== end" -> flush()
                activeName != null -> buffer += line
                line.isBlank() -> Unit
                else -> error("Unexpected line outside of sample entry in ${path.name} at line ${index + 1}: $line")
            }
        }

        flush()
        return entries
    }
}

class GuideRepository(
    private val root: Path = Paths.get("guide"),
) {
    fun loadAll(): List<GuideTopic> {
        if (!Files.exists(root)) {
            return emptyList()
        }

        return Files.walk(root).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.extension == "txt" }
                .sortedBy { it.toString() }
                .flatMap { parseGuideFile(it).asSequence() }
                .toList()
        }
    }

    fun find(key: String): GuideTopic? = loadAll().firstOrNull { it.key == key }

    private fun parseGuideFile(path: Path): List<GuideTopic> {
        val topics = mutableListOf<GuideTopic>()
        var currentKey: String? = null
        var currentTitle: String? = null
        val buffer = mutableListOf<String>()

        fun flush() {
            val key = currentKey ?: return
            topics += GuideTopic(
                key = key,
                title = currentTitle ?: key,
                body = buffer.joinToString("\n").trim(),
                origin = path.name,
            )
            currentKey = null
            currentTitle = null
            buffer.clear()
        }

        path.toFile().readLines().forEach { line ->
            if (line.startsWith("@@ ")) {
                flush()
                val payload = line.removePrefix("@@ ").trim()
                val parts = payload.split("|").map { it.trim() }
                currentKey = parts.first()
                currentTitle = parts.getOrNull(1) ?: parts.first()
            } else if (currentKey == null && line.isBlank()) {
                Unit
            } else if (currentKey == null) {
                currentKey = path.fileName.toString().substringBeforeLast('.')
                currentTitle = currentKey
                buffer += line
            } else {
                buffer += line
            }
        }

        flush()
        return topics
    }
}
