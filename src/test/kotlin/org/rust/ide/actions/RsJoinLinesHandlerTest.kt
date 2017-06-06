package org.rust.ide.actions

class RsJoinLinesHandlerTest : RsJoinLinesHandlerTestBase() {
    fun `test empty file`() = doTest("/*caret*/", "/*caret*/")

    fun `test blank file1`() = doTest("/*caret*/\n\n", "/*caret*/\n")
    fun `test blank file2`() = doTest("\n/*caret*/\n", "\n/*caret*/")

    fun testNoEscape() = doTest("""
        fn main() {
            "Hello<caret>,
             World"
        }
    """, """
        fn main() {
            "Hello,<caret> World"
        }
    """)

    fun testNewlineEscape() = doTest("""
        fn main() {
            "He<caret>llo, \
             World"
        }
    """, """
        fn main() {
            "Hello,<caret> World"
        }
    """)

    fun testEscapedNewlineEscape() = doTest("""
        fn main() {
            "He<caret>llo, \\
             World"
        }
    """, """
        fn main() {
            "Hello, \\<caret> World"
        }
    """)

    fun testEscapedButNotEscapedInFactNewlineEscape() = doTest("""
        fn main() {
            "He<caret>llo, \\\
             World"
        }
    """, """
        fn main() {
            "Hello, \\<caret> World"
        }
    """)

    fun testTwoEscapedBackslashes() = doTest("""
        fn main() {
            "He<caret>llo, \\\\
             World"
        }
    """, """
        fn main() {
            "Hello, \\\\<caret> World"
        }
    """)

    fun testNoIndent() = doTest("""
        fn main() {
            "Hel<caret>lo,
World"
        }
    """, """
        fn main() {
            "Hello,<caret> World"
        }
    """)

    fun testOnlyNewlineEscape() = doTest("""
        fn main() {
            "<caret>\
            "
        }
    """, """
        fn main() {
            "<caret> "
        }
    """)

    fun testOuterDocComment() = doTest("""
        /// Hello<caret>
        /// Docs
        fn foo() {}
    """, """
        /// Hello<caret> Docs
        fn foo() {}
    """)

    fun testInnerDocComment() = doTest("""
        //! Hello<caret>
        //! Docs
    """, """
        //! Hello<caret> Docs
    """)

    fun testOuterDocCommentNotComment() = doTest("""
        /// Hello<caret>
        fn foo() {}
    """, """
        /// Hello<caret> fn foo() {}
    """)

    fun `test join struct selection`() = doTest("""
        struct S { foo: i32, bar: i32 }
        fn main() {
            let _ = S <selection>{
                foo: 42,
                bar: 42,
            };</selection>
        }
    ""","""
        struct S { foo: i32, bar: i32 }
        fn main() {
            let _ = S { foo: 42, bar: 42 };
        }
    """)

    fun `test join struct`() = doTest("""
        struct S { foo: i32 }
        fn main() {
            let _ = S { /*caret*/
                foo: 42,
            };
        }
    ""","""
        struct S { foo: i32 }
        fn main() {
            let _ = S { foo: 42,
            };
        }
    """)

    fun `test remove comma 1`() = doTest("""
        struct S { foo: i32 }
        fn main() {
            let _ = S { foo: 42, /*caret*/
             };
        }
    ""","""
        struct S { foo: i32 }
        fn main() {
            let _ = S { foo: 42 };
        }
    """)

    fun `test remove comma 2`() = doTest("""
        struct S { foo: i32, bar: i32 }
        fn main() {
            let _ = S {
                foo: 42,
                bar: /*caret*/42,
             };
        }
    ""","""
        struct S { foo: i32, bar: i32 }
        fn main() {
            let _ = S {
                foo: 42,
                bar: 42 };
        }
    """)

}
