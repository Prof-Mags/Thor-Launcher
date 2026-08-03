package com.thor.core.database

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Exercises the DAO proxy against plain interfaces.
 *
 * The behaviour under test is not Room's — it is that a flow re-subscribes when
 * the profile changes and that a direct call reaches the database open at the
 * time, which is what nothing else in the codebase asserts and what a profile
 * switch depends on entirely.
 */
class ProfileScopedDaoTest {

    private interface FakeDao {
        fun observeTitles(): Flow<List<String>>
        fun count(): Int
        fun rename(from: String, to: String): String
        fun explode(): Int
    }

    private class FakeDatabase(private val titles: List<String>) {
        val dao = object : FakeDao {
            override fun observeTitles(): Flow<List<String>> = flowOf(titles)
            override fun count(): Int = titles.size
            override fun rename(from: String, to: String): String = "$from->$to"
            override fun explode(): Int = throw IllegalStateException("boom")
        }
    }

    private val joe = FakeDatabase(listOf("Metroid", "Zelda"))
    private val guest = FakeDatabase(listOf("Tetris"))

    private fun dao(active: MutableStateFlow<FakeDatabase?>): FakeDao = profileScopedDao(
        daoClass = FakeDao::class.java,
        current = active,
        require = { active.value ?: error("no database") },
        select = FakeDatabase::dao,
    )

    @Test
    fun `a flow re-emits against the new profile's database on a switch`() = runTest {
        val active = MutableStateFlow<FakeDatabase?>(joe)

        dao(active).observeTitles().test {
            assertThat(awaitItem()).containsExactly("Metroid", "Zelda")

            active.value = guest

            assertThat(awaitItem()).containsExactly("Tetris")
        }
    }

    @Test
    fun `a flow collected before a profile resolves waits rather than reporting empty`() = runTest {
        val active = MutableStateFlow<FakeDatabase?>(null)

        dao(active).observeTitles().test {
            expectNoEvents()

            active.value = guest

            assertThat(awaitItem()).containsExactly("Tetris")
        }
    }

    @Test
    fun `a direct call goes to the database open at the time`() {
        val active = MutableStateFlow<FakeDatabase?>(joe)
        val dao = dao(active)

        assertThat(dao.count()).isEqualTo(2)

        active.value = guest

        assertThat(dao.count()).isEqualTo(1)
    }

    @Test
    fun `arguments are forwarded in order`() {
        val active = MutableStateFlow<FakeDatabase?>(joe)

        assertThat(dao(active).rename("a", "b")).isEqualTo("a->b")
    }

    @Test
    fun `a failure arrives as itself, not wrapped by reflection`() {
        val active = MutableStateFlow<FakeDatabase?>(joe)

        val thrown = runCatching { dao(active).explode() }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
        assertThat(thrown).hasMessageThat().isEqualTo("boom")
    }

    @Test
    fun `identity is the proxy's own, not the database's`() {
        val active = MutableStateFlow<FakeDatabase?>(joe)
        val dao = dao(active)

        assertThat(dao).isEqualTo(dao)
        assertThat(dao).isNotEqualTo(dao(active))
        assertThat(dao.toString()).contains("FakeDao")
    }
}
