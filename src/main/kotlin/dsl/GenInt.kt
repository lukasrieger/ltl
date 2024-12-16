package dsl

import io.kotest.property.Arb
import io.kotest.property.Gen
import io.kotest.property.arbitrary.*

sealed interface Pred<A> {
    fun toAtom(): (A) -> Boolean
    fun toArb(): Arb<A>
}


sealed interface IntPred : Pred<Int> {
    data class Const(val value: Int) : IntPred
    data class Range(val min: Int, val max: Int) : IntPred
    data class Positive(val from: Int, val to: Int) : IntPred
    data class NonNegative(val from: Int, val to: Int) : IntPred
    data class Negative(val from: Int, val to: Int) : IntPred
    data class NonPositive(val from: Int, val to: Int) : IntPred
    data class Multiple(val k: Int, val max: Int) : IntPred
    data class Factor(val k: Int) : IntPred

    override fun toAtom() = evalToPred()
    override fun toArb() = evalToGen()
}

fun lam(fn: (Int) -> Boolean): (Int) -> Boolean = fn


fun IntPred.evalToPred(): (Int) -> Boolean = when(this) {
    is IntPred.Const -> lam { it == value }
    is IntPred.Factor -> lam { k % it == 0 }
    is IntPred.Multiple -> lam { it % k == 0 && it <= max }
    is IntPred.Negative -> lam { it < 0 && it in from .. to }
    is IntPred.NonNegative -> lam { it >= 0 && it in from .. to }
    is IntPred.NonPositive -> lam { it <= 0 && it in from .. to }
    is IntPred.Positive -> lam { it > 0 && it in from .. to }
    is IntPred.Range -> lam { it in min .. max }
}

fun IntPred.evalToGen(): Arb<Int> = when(this) {
    is IntPred.Const -> Arb.constant(value)
    is IntPred.Factor -> Arb.factor(k)
    is IntPred.Multiple -> Arb.multiple(k, max)
    is IntPred.Negative -> Arb.negativeInt(from)
    is IntPred.NonNegative -> Arb.nonNegativeInt(from)
    is IntPred.NonPositive -> Arb.nonPositiveInt(from)
    is IntPred.Positive -> Arb.positiveInt(from)
    is IntPred.Range -> Arb.int(min, max)
}