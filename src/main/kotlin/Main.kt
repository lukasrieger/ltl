import com.lukas.*
import com.lukas.LTL.Atom


fun main() = println(resultLTL2)




// Until reaching 1000, all numbers are even. Afterward, all numbers are odd
val even = Atom<Int> { it % 2 == 0 }
val thousand = Atom<Int> { it > 1000 }
val exactly2thousand = Atom<Int> { it == 2000 }
val odd = Atom<Int> { it % 2 != 0 }
val composite: LTL<Int> = even until (odd until exactly2thousand)
val alwaysOdd = odd.always()

val succ = accept<Int> { n -> atom((n + 1)::equals) }.always()

val seq1 = (1..1000).filter { it % 2 == 0 }
val seq2 = (1000..5000).filter { it % 2 != 0 }

val combined = seq1 + seq2 + listOf(2000)

val resultLTL: Accept<Int> = combined.fold(eval(composite)) { acc, curr ->
    when (acc) {
        is Accept.Next -> acc.n(curr)
        else -> acc
    }
}

val resultLTL2 = ((1..20000) + 30000).fold(eval(succ)) { acc, curr ->
    when (acc) {
        is Accept.Next -> acc.n(curr)
        else -> acc
    }
}