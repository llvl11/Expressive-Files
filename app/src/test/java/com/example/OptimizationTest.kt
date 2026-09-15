package com.example

import android.app.Application
import android.content.Context
import com.baiel.expressivefiles.data.FileManagerRepository
import com.baiel.expressivefiles.model.FileType
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OptimizationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: FileManagerRepository

    @Before
    fun setup(): Unit {
        repository = FileManagerRepository(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `test search capping results`() = runBlocking {
        val root = tempFolder.newFolder("search_test")
        // Create 2000 files
        for (i in 1..1500) {
            File(root, "test_file_$i.txt").createNewFile()
        }

        val results = repository.searchFiles(
            startDir = root,
            query = "test",
            recursive = false,
            maxResults = 1000
        )

        assertEquals(1000, results.size)
    }

    @Test
    fun `test category capping results`() = runBlocking {
        val root = tempFolder.newFolder("category_test")
        // Create 1500 images
        for (i in 1..1500) {
            File(root, "img_$i.jpg").createNewFile()
        }

        val results = repository.getFilesByCategory(
            category = FileType.IMAGE,
            baseDir = root,
            maxResults = 500
        )

        assertEquals(500, results.size)
    }
}
