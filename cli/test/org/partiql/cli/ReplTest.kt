/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 *  You may not use this file except in compliance with the License.
 * A copy of the License is located at:
 *
 *      http://aws.amazon.com/apache2.0/
 *
 *  or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package org.partiql.cli

import com.amazon.ion.system.*
import org.partiql.lang.eval.*
import org.partiql.lang.syntax.*
import org.junit.*
import org.junit.Assert.*
import org.partiql.lang.*
import java.io.*

class ReplTest {
    private val ion = IonSystemBuilder.standard().build()
    private val valueFactory = ExprValueFactory.standard(ion)
    
    private val output = ByteArrayOutputStream()
    private val parser: Parser = SqlParser(ion)
    private val compiler = CompilerPipeline.build(ion) {
        sqlParser(parser)
    }

    private val regex = Regex("===' \n" + "(.*?)\n" + "---", RegexOption.DOT_MATCHES_ALL)

    private fun makeRepl(input: String, bindings: Bindings = Bindings.EMPTY) =
        Repl(valueFactory, input.byteInputStream(Charsets.UTF_8), output, parser, compiler, bindings)

    private fun Repl.runAndOutput(): String {
        run()
        return output.toString(Charsets.UTF_8.name())
    }

    private fun assertIon(expected: String, actualRepl: String) {
        val actualClean = regex.findAll(actualRepl).map { it.groups[1]!!.value }.joinToString(" ")
        assertEquals(ion.loader.load(expected), ion.loader.load(actualClean))
    }

    @Test
    fun singleQuery() {
        val repl = makeRepl("1 + 1\n")
        val actual = repl.runAndOutput()
        assertIon("2", actual)
    }

    @Test
    fun multipleQuery() {
        val repl = makeRepl("1 + 1\n\n 2 + 2\n")
        val actual = repl.runAndOutput()
        assertIon("2 4", actual)
    }

    @Test
    fun astWithoutMetas() {
        val repl = makeRepl("1 + 1\n!!")
        val actual = repl.runAndOutput()
        assertIon("(ast (version 1) (root (+ (lit 1) (lit 1))))", actual)
    }

    @Test
    fun astWithMetas() {
        val repl = makeRepl("1 + 1\n!?")
        val actual = repl.runAndOutput()
        //Note: ${'$'} is the only way to escape $ in a multiline string
        assertIon(
            """
                (ast
                    (version 1)
                    (root
                        (term
                            (exp
                                (+
                                    (term
                                        (exp (lit 1))
                                        (meta (${'$'}source_location ({line_num:1,char_offset:1}))))
                                    (term
                                        (exp (lit 1))
                                        (meta (${'$'}source_location ({line_num:1,char_offset:5}))))))
                            (meta (${'$'}source_location ({line_num:1,char_offset:3}))))))
            """,
            actual)
    }
}