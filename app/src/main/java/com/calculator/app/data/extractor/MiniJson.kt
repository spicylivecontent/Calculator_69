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

        fun readValue(): Any? {
            skipWhitespace()
            if (index >= input.length) throw IllegalStateException("tom JSON")
            return when (val char = input[index]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't', 'f' -> readBoolean()
                'n' -> readNull()
                else -> if (char == '-' || char.isDigit()) readNumber() else throw IllegalStateException("oväntat tecken: $char")
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
                                val hex = input.substring(index, (index + 4).coerceAtMost(input.length))
                                index += hex.length
                                builder.append(hex.toIntOrNull(16)?.toChar() ?: ' ')
                            }

                            else -> builder.append(escape)
                        }
                    }

                    else -> builder.append(char)
                }
            }
            throw IllegalStateException("ostängd sträng")
        }

        private fun readNumber(): Double {
            val start = index
            if (peek() == '-') index++
            while (index < input.length && (input[index].isDigit() || input[index] in ".eE+-")) index++
            return input.substring(start, index).toDoubleOrNull() ?: 0.0
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
