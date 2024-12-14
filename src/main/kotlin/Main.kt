package com.lukas

import kotlinx.coroutines.flow.*
import kotlin.math.abs

typealias Endo<A> = (A) -> A
data class Fix<A>(val fn: (Fix<A>) -> Endo<A>)

fun <T> fix2(f: (T) -> T): T = f(fix2(f))


fun <A> fix(f: (Endo<A>) -> Endo<A>): Endo<A> {
    fun <A> fix_(f: Fix<A>):Endo<A> = f.fn(f)

    return fix_(Fix { rec -> f { x -> fix_(rec)(x) } })
}



sealed interface Either<out A, out B> {
    data class Right<out B>(val value: B) : Either<Nothing, B>
    data class Left<out A>(val value: A) : Either<A, Nothing>
}

fun interface Machine<in A, out B> {
    fun step(a: A): Either<Machine<A, B>, B>
}

fun <A, B, C> Machine<A, B>.map(fn: (B) -> C): Machine<A, C> = Machine<A, C> { a ->
    when(val res = this.step(a)) {
        is Either.Left -> Either.Left(res.value.map(fn))
        is Either.Right -> Either.Right(fn(res.value))
    }
}

sealed interface Reason<out A> {
    data class HitBottom<A>(val reason: String) : Reason<A>
    data class Rejected<A>(val reason: A) : Reason<A>
    data class BothFailed<A>(val left: Reason<A>, val right: Reason<A>) : Reason<A>
    data class LeftFailed<A>(val reason: Reason<A>) : Reason<A>
    data class RightFailed<A>(val reason: Reason<A>) : Reason<A>
}

typealias Result<A> = Reason<A>?

typealias LTL<A> = Machine<A, Result<A>>

fun <A> stop(result: Result<A>): LTL<A> = Machine { Either.Right(result) }

fun <A> top(): LTL<A> = stop(null)

fun <A> bottom(reason: String): LTL<A> = stop(Reason.HitBottom(reason))

fun <A> invert(result: Result<A>): Result<A> = when(result) {
    null -> Reason.HitBottom("neg")
    else -> null
}

infix fun <A> LTL<A>.or(q: LTL<A>): LTL<A> = TODO()

infix fun <A> LTL<A>.andNext(next: LTL<A>): LTL<A> = TODO()

fun <A> neg(ltl: LTL<A>): LTL<A> = ltl.map(::invert)

fun <A> accept(fn: (A) -> LTL<A>): LTL<A> = Machine { a -> fn(a).step(a) }

fun <A> reject(fn: (A) -> LTL<A>): LTL<A> = neg(accept(fn))

fun <A> next(ltl: LTL<A>): LTL<A> = Machine { Either.Left(ltl) }

infix fun <A> LTL<A>.until(q: LTL<A>): LTL<A> = Machine { a ->

    val fn1 = {a1: LTL<A> -> a1 or q }
    val fn2 = {a2: LTL<A> -> a2 andNext this }

    fix { a3 -> fn1(fn2(a3)) }
}

infix fun <A> LTL<A>.release(q: LTL<A>): LTL<A> = TODO()

fun <A> always(ltl: LTL<A>): LTL<A> = bottom<A>("always") release ltl

fun <A> truth(b: Boolean): LTL<A> = if (b) top() else bottom("false")

fun <A> test(fn: (A) -> Boolean): LTL<A> = accept { a -> truth(fn(a)) }

fun <A> equal(a: A): LTL<A> = test { b -> a == b }



fun main() = Unit