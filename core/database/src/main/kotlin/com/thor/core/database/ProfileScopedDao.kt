package com.thor.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Wraps a DAO so it always talks to the signed-in profile's database.
 *
 * The problem this solves is not "which database" — a holder would answer that.
 * It is that repositories build their flows once, at construction: `val games =
 * gameDao.observeAll()` stays subscribed to one database forever, so a profile
 * switch would leave every screen showing the previous profile's library until
 * the process restarted. Fixing that at each call site means `flatMapLatest`
 * around all hundred and seventy of them, and one missed site is a silent bug
 * that shows one person another's games.
 *
 * So a flow-returning method returns a flow that re-subscribes when the profile
 * changes, and every other call goes to the database open right now. Repositories
 * keep injecting DAOs and never learn that profiles exist.
 *
 * Reflection is the price, and it is contained to this file. The alternative —
 * a hand-written delegate for each of nine DAOs — is several hundred lines of
 * forwarding that has to be extended every time a query is added, which is
 * exactly the kind of thing that gets forgotten.
 *
 * Generic in the database type so the machinery can be tested against ordinary
 * interfaces rather than only against a built Room instance.
 */
@Suppress("UNCHECKED_CAST")
fun <D : Any, T : Any> profileScopedDao(
    daoClass: Class<T>,
    current: StateFlow<D?>,
    require: () -> D,
    select: (D) -> T,
): T = Proxy.newProxyInstance(
    daoClass.classLoader,
    arrayOf(daoClass),
    ProfileScopedDaoHandler(daoClass, current, require, select),
) as T

private class ProfileScopedDaoHandler<D : Any, T : Any>(
    private val daoClass: Class<T>,
    private val current: StateFlow<D?>,
    private val require: () -> D,
    private val select: (D) -> T,
) : InvocationHandler {

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        val arguments = args ?: EMPTY_ARGS

        // Answered here rather than forwarded: identity belongs to the proxy, and
        // forwarding `equals` to a database that may not be open yet would make a
        // debugger inspection open one.
        if (method.declaringClass == Any::class.java) {
            return when (method.name) {
                "equals" -> proxy === arguments.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "ProfileScopedDao(${daoClass.simpleName})"
                else -> null
            }
        }

        return if (Flow::class.java.isAssignableFrom(method.returnType)) {
            /*
             * Re-subscribed on every switch. `filterNotNull` matters on a cold
             * start: a screen can collect before the profile has resolved, and
             * without it that collector would be handed nothing and sit there.
             */
            current
                .filterNotNull()
                .flatMapLatest { database ->
                    method.callOn(select(database), arguments) as Flow<Any?>
                }
        } else {
            method.callOn(select(require()), arguments)
        }
    }

    /**
     * Forwards a call, unwrapping reflection's exception wrapper.
     *
     * An [InvocationTargetException] escaping here would reach callers instead of
     * the `SQLiteConstraintException` they catch, and would break a suspending
     * caller's cancellation — a `CancellationException` that arrives wrapped is
     * not recognised as one, and surfaces as a crash.
     */
    private fun Method.callOn(target: Any, args: Array<out Any?>): Any? = try {
        invoke(target, *args)
    } catch (e: InvocationTargetException) {
        throw e.cause ?: e
    }

    private companion object {
        val EMPTY_ARGS = emptyArray<Any?>()
    }
}
