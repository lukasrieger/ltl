package com.lukas

import com.lukas.Accept.Fail
import com.lukas.Accept.Pass
import com.lukas.LTL.Always
import com.lukas.LTL.Atom

sealed interface LTL<out A> {
    data object Top : LTL<Nothing>
    data object Bottom : LTL<Nothing>

    data class Atom<A>(val p: (A) -> Boolean) : LTL<A>
    data class Not<A>(val p: LTL<A>) : LTL<A>
    data class Or<A>(val p: LTL<A>, val q: LTL<A>) : LTL<A>
    data class Until<A>(val p: LTL<A>, val q: LTL<A>) : LTL<A>
    data class Next<A>(val p: LTL<A>) : LTL<A>
    data class And<A>(val p: LTL<A>, val q: LTL<A>) : LTL<A>
    data class Implies<A>(val p: LTL<A>, val q: LTL<A>) : LTL<A>
    data class Always<A>(val p: LTL<A>) : LTL<A>
    data class Eventually<A>(val p: LTL<A>) : LTL<A>
    data class Accept<A>(val a: (A) -> LTL<A>) : LTL<A>
}

operator fun <A> LTL<A>.not(): LTL<A> = LTL.Not(this)
infix fun <A> LTL<A>.and(q: LTL<A>) = LTL.And(this, q)
infix fun <A> LTL<A>.or(q: LTL<A>) = LTL.Or(this, q)
infix fun <A> LTL<A>.implies(q: LTL<A>) = LTL.Implies(this, q)
infix fun <A> LTL<A>.until(q: LTL<A>) = LTL.Until(this, q)
fun <A> LTL<A>.next() = LTL.Next(this)
fun <A> LTL<A>.always() = Always(this)

fun <A> accept(fn: (A) -> LTL<A>) = LTL.Accept(fn)

sealed interface Accept<out A> {
    data object Pass : Accept<Nothing>
    data object Fail : Accept<Nothing>
    data class Next<A>(val n: (A) -> Accept<A>) : Accept<A>
}

fun <A> negate(a: Accept<A>): Accept<A> = when (a) {
    Pass -> Fail
    Fail -> Pass
    is Accept.Next<A> -> Accept.Next { negate(a.n(it)) }
}

infix fun <A> Accept<A>.andA(other: Accept<A>): Accept<A> = when {
    this is Fail || other is Fail -> Fail
    this is Pass -> other
    other is Pass -> this
    this is Accept.Next<A> && other is Accept.Next<A> -> Accept.Next { x -> this.n(x) andA other.n(x) }
    else -> error("unreachable.")
}

fun <A> eval(formula: LTL<A>): Accept<A> = when (formula) {
    LTL.Top -> Pass
    LTL.Bottom -> Fail
    is Always -> eval(formula.p and LTL.Next(Always(formula.p)))
    is LTL.And -> eval(formula.p) andA eval(formula.q)
    is Atom -> eval(LTL.Accept { n -> if (formula.p(n)) LTL.Top else LTL.Bottom })
    is LTL.Eventually -> eval(formula.p or LTL.Next(LTL.Eventually(formula.p)))
    is LTL.Implies -> eval(!formula.p or formula.q)
    is LTL.Next -> Accept.Next { eval(formula.p) }
    is LTL.Not -> negate(eval(formula.p))
    is LTL.Or -> eval(!(!formula.p and !formula.q))
    is LTL.Until -> eval(formula.q or (formula.p and LTL.Next(LTL.Until(formula.p, formula.q))))
    is LTL.Accept -> Accept.Next { p -> eval(formula.a(p)) }
}

// Until reaching 1000, all numbers are even. Afterward, all numbers are odd
val even = Atom<Int> { it % 2 == 0 }
val thousand = Atom<Int> { it > 1000 }
val exactly2thousand = Atom<Int> { it == 2000 }
val odd = Atom<Int> { it % 2 != 0 }
val composite: LTL<Int> = even until (odd until exactly2thousand)
val alwaysOdd = odd.always()

val succ = accept<Int> { n -> Atom((n + 1)::equals) }.always()

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