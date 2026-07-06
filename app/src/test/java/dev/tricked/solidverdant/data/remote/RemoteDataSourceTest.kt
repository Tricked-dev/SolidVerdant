package dev.tricked.solidverdant.data.remote

import dev.tricked.solidverdant.data.model.Project
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteDataSourceTest {
    @Test fun fake_remote_source_returns_configured_projects() = runTest {
        val source: RemoteDataSource = FakeRemoteDataSource(
            projects = listOf(Project(id = "p1", name = "P", color = "#fff"))
        )
        assertEquals("p1", source.getProjects("org1").getOrThrow().first().id)
    }
}
