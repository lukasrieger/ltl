import com.lukas.*
import com.lukas.LTL.Always
import com.lukas.LTL.Atom
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.resolution.default
import io.kotest.property.resolution.resolve
import kotlin.reflect.KType
import kotlin.reflect.typeOf


@Suppress("UNCHECKED_CAST")
fun <A> defaultArb(type: KType): Arb<A> = resolve(type) as Arb<A>

sealed interface TransduceAccept<out A> {
    data object Pass : TransduceAccept<Nothing>
    data object Fail : TransduceAccept<Nothing>

    data class NextA<A>(
        val n: (A) -> TransduceAccept<A>,
        val source: Arb<A>
    ) : TransduceAccept<A>
}

fun interface Transducer<A> {
    fun nextT(): Pair<A, Transducer<A>>?
}

fun <A> rejectTransducer(): Transducer<A> = Transducer { null }

fun <A> arbTransduce(arb: Arb<A>): Transducer<A> = Transducer { arb.next() to arbTransduce(arb) }

fun <A> sourcedTransducer(acc: TransduceAccept.NextA<A>): Transducer<A> {
    fun loop(gas: Int): Pair<A, Transducer<A>>? = when(gas) {
        0 ->  null // .also {println("Ran out of gas!")}
        else -> {
            //println(gas)
            val value = acc.source.next()
            when (val res = acc.n(value)) {
                TransduceAccept.Fail -> loop(gas - 1) //.also { println("Fail!") }
                is TransduceAccept.NextA -> value to sourcedTransducer(res) //.also { println("NextA!") }
                TransduceAccept.Pass -> value to arbTransduce(acc.source) //.also { println("Pass!") }
            }
        }
    }

    return Transducer { loop(1000) }
}

inline fun <reified A> LTL<A>.satisfyingArb(): Arb<Sequence<A>> {
    return arbitrary {
        sequence {
            var trans = transducer(this@satisfyingArb)
            while (true) {
                trans.nextT()?.let { (value, next) ->
                    if (value != null) {
                        trans = next
                        yield(value)
                    }
                } ?: break
            }
        }
    }
}

inline fun <reified A> transducer(formula: LTL<A>): Transducer<A> = transducer(trans(formula, typeOf<A>()))

inline fun <reified A> transducer(accept: TransduceAccept<A>) = when (accept) {
    TransduceAccept.Pass -> arbTransduce(Arb.default<A>())
    TransduceAccept.Fail -> rejectTransducer()
    is TransduceAccept.NextA -> sourcedTransducer(accept)
}

fun <A> negate(a: TransduceAccept<A>): TransduceAccept<A> = when (a) {
    TransduceAccept.Pass -> TransduceAccept.Fail
    TransduceAccept.Fail -> TransduceAccept.Pass
    is TransduceAccept.NextA<A> -> TransduceAccept.NextA(
        n = { negate(a.n(it)) },
        source = a.source
    )
}

infix fun <A> TransduceAccept<A>.andA(other: TransduceAccept<A>): TransduceAccept<A> =
    when {
        this is TransduceAccept.Fail || other is TransduceAccept.Fail ->
            TransduceAccept.Fail
        this is TransduceAccept.Pass -> other
        other is TransduceAccept.Pass -> this
        this is TransduceAccept.NextA && other is TransduceAccept.NextA ->
            TransduceAccept.NextA(
                n = { x -> this.n(x) andA other.n(x) },
                source = this.source
            )
        else -> error("unreachable.")
    }

fun <A> transProxy(formula: LTL<A>, type: KType): TransduceAccept<A> =
    trans(formula, type)

fun <A> trans(formula: LTL<A>, type: KType): TransduceAccept<A> =
    when (formula) {
        is LTL.Accept<A> -> TransduceAccept.NextA(
            n = { p -> transProxy(formula.a(p), type) },
            source = defaultArb(type)
        )
        is Always<A> -> transProxy(formula.p and LTL.Next(Always(formula.p)), type)
        is LTL.And<A> -> transProxy(formula.p, type) andA transProxy(formula.q, type)
        is Atom<A> -> TransduceAccept.NextA(
            n = { p ->
                val test = formula.p
                val result = if (test(p)) LTL.Top else LTL.Bottom
                transProxy(result, type)
            },
            source = defaultArb<A>(type).filter(formula.p)
        )
        LTL.Bottom -> TransduceAccept.Fail
        is LTL.Eventually<A> -> transProxy(formula.p or LTL.Next(LTL.Eventually(formula.p)), type)
        is LTL.Implies<A> -> transProxy(!formula.p or formula.q, type)
        is LTL.Next<A> -> TransduceAccept.NextA(
            n = { transProxy(formula.p, type)},
            source = defaultArb(type)
        )
        is LTL.Not<A> -> negate(transProxy(formula.p, type))
        is LTL.Or<A> -> transProxy(!(!formula.p and !formula.q), type)
        LTL.Top -> TransduceAccept.Pass
        is LTL.Until<A> -> transProxy(formula.q or (formula.p and LTL.Next(LTL.Until(formula.p, formula.q))), type)
        is LTL.Pred -> TransduceAccept.NextA(
            n = { p ->
                val test = formula.pred.toAtom()
                val result = if (test(p)) LTL.Top else LTL.Bottom
                transProxy(result, type)
            },
            source = formula.pred.toArb()
        )
    }