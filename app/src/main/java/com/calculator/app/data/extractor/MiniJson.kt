package com.calculator.app.data.extractor

/**
 * Minimal, fristående JSON-tolkare för de `application/ld+json`-block som sajterna
 * bäddar in i detaljsidorna. Den är medvetet liten och strikt, saknar beroenden och
 * kan därför enhetstestas på JVM.
 *
 * Resultatet är `Map<String, Any?>`, `List<Any?>`, `String`, `Double`, `Boolean`
 * eller `null`.
 */
object MiniJson {

    fun parse(text: String?): Any? {
        val input = text?.trim().orEmpty()
        if (input.isEmpty()) return null
        val parser = Parser(input)
        return try {
            val value = parser.readValue()
            parser.skipWhitespace()
            require(parser.isFinished()) { "Trailing JSON content" }
            value
        } catch (_: Exception) {
            null
        }
    }

    /** Plockar ut det första objektet i en djup lista/objektstruktur som matchar [predicate]. */
    fun findObject(value: Any?, predicate: (Map<String, Any?>) -> Boolean): Map<String, Any?>? {
        when (value) {
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val map = value as Map<String, Any?>
                if (predicate(map)) return map
                for (nested in map.values) {
                    findObject(nested, predicate)?.let { return it }
                }
            }

            is List<*> -> {
                for (item in value) {
                    findObject(item, predicate)?.let { return it }
                }
            }
        }
        return null
    }

    fun string(value: Any?): String? = when (value) {
        is String -> value.trim().takeIf { it.isNotEmpty() }
        is Double -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        is Number -> value.toString()
        else -> null
    }

    fun strings(value: Any?): List<String> = when (value) {
        is String -> value.split(',', '|', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        is List<*> -> value.mapNotNull { string(it) }
        else -> emptyList()
    }

    private class Parser(private val input: String) {
        private var index = 0
        private var depth = 0
        fun isFinished(): Boolean = index == input.length

        fun readValue(): Any? {
            skipWhitespace()
            if (index >= input.length) throw IllegalStateException("tom JSON")
            require(depth < 64) { "JSON nesting limit exceeded" }
            depth++
            return try {
                when (val char = input[index]) {
                    '{' -> readObject()
                    '[' -> readArray()
                    '"' -> readString()
                    't', 'f' -> readBoolean()
                    'n' -> readNull()
                    else -> if (char == '-' || char.isDigit()) readNumber() else throw IllegalStateException("oväntat tecken: $char")
                }
            } finally {
                depth--
            }
        }

        fun skipWhitespace() {
            while (index < input.length && input[index].isWhitespace()) index++
        }

        private fun readObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            index++ // {
            skipWhitespace()
            if (peek() == '}') {
                index++
                return map
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                val value = readValue()
                map[key] = value
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    '}' -> {
                        index++
                        return map
                    }

                    else -> throw IllegalStateException("fel i objekt vid $index")
                }
            }
        }

        private fun readArray(): List<Any?> {
            val list = mutableListOf<Any?>()
            index++ // [
            skipWhitespace()
            if (peek() == ']') {
                index++
                return list
            }
            while (true) {
                list.add(readValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    ']' -> {
                        index++
                        return list
                    }

                    else -> throw IllegalStateException("fel i lista vid $index")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val builder = StringBuilder()
            while (index < input.length) {
                val char = input[index++]
                when {
                    char == '"' -> return builder.toString()
                    char == '\\' -> {
                        if (index >= input.length) break
                        when (val escape = input[index++]) {
                            '"', '\\', '/' -> builder.append(escape)
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                require(index + 4 <= input.length)
                                val hex = input.substring(index, index + 4)
                                require(hex.all { it in "0123456789abcdefABCDEF" })
                                index += 4
                                builder.append(hex.toInt(16).toChar())
                            }

                            else -> throw IllegalStateException("Invalid JSON escape")
                        }
                    }

                    else -> {
                        require(char >= ' ') { "Unescaped control character" }
                        builder.append(char)
                    }
                }
            }
            throw IllegalStateException("ostängd sträng")
        }

        private fun readNumber(): Double {
            val start = index
            if (peek() == '-') index++
            while (index < input.length && (input[index].isDigit() || input[index] in ".eE+-")) index++
            val raw = input.substring(start, index)
            require(Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?").matches(raw))
            return raw.toDouble().also { require(it.isFinite()) }
        }

        private fun readBoolean(): Boolean {
            return if (input.startsWith("true", index)) {
                index += 4
                true
            } else if (input.startsWith("false", index)) {
                index += 5
                false
            } else {
                throw IllegalStateException("ogiltigt booleskt värde")
            }
        }

        private fun readNull(): Any? {
            if (!input.startsWith("null", index)) throw IllegalStateException("ogiltigt värde")
            index += 4
            return null
        }

        private fun expect(char: Char) {
            skipWhitespace()
            if (peek() != char) throw IllegalStateException("förväntade '$char' vid $index")
            index++
        }

        private fun peek(): Char = if (index < input.length) input[index] else '\u0000'
    }
}
