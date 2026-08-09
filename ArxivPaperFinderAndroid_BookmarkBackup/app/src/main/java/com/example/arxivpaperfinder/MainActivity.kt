@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.arxivpaperfinder

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import android.os.Bundle
import android.util.Xml
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

data class Author(
    val name: String,
    val affiliation: String = ""
)

data class Paper(
    val title: String,
    val summary: String,
    val authors: List<Author>,
    val categories: List<String>,
    val published: String,
    val arxivId: String,
    val arxivUrl: String
)

data class SubArea(
    val name: String,
    val arxivCode: String
)

data class InterestArea(
    val name: String,
    val subAreas: List<SubArea>
)

private val interestAreas = listOf(
    InterestArea(
        "Computer Science",
        listOf(
            SubArea("Artificial Intelligence", "cs.AI"),
            SubArea("Machine Learning", "cs.LG"),
            SubArea("Computer Vision", "cs.CV"),
            SubArea("Computation & Language / NLP", "cs.CL"),
            SubArea("Robotics", "cs.RO"),
            SubArea("Neural & Evolutionary Computing", "cs.NE"),
            SubArea("Information Retrieval", "cs.IR"),
            SubArea("Cryptography & Security", "cs.CR"),
            SubArea("Distributed / Parallel Computing", "cs.DC"),
            SubArea("Human-Computer Interaction", "cs.HC"),
            SubArea("Social & Information Networks", "cs.SI"),
            SubArea("Sound", "cs.SD"),
            SubArea("Software Engineering", "cs.SE")
        )
    ),
    InterestArea(
        "Electrical Engineering",
        listOf(
            SubArea("Signal Processing", "eess.SP"),
            SubArea("Audio & Speech Processing", "eess.AS"),
            SubArea("Image & Video Processing", "eess.IV"),
            SubArea("Systems & Control", "eess.SY")
        )
    ),
    InterestArea(
        "Mathematics",
        listOf(
            SubArea("Statistics Theory", "math.ST"),
            SubArea("Probability", "math.PR"),
            SubArea("Optimization & Control", "math.OC"),
            SubArea("Numerical Analysis", "math.NA"),
            SubArea("Analysis of PDEs", "math.AP"),
            SubArea("Dynamical Systems", "math.DS"),
            SubArea("Information Theory", "math.IT"),
            SubArea("Combinatorics", "math.CO"),
            SubArea("Algebraic Geometry", "math.AG"),
            SubArea("Algebraic Topology", "math.AT")
        )
    ),
    InterestArea(
        "Physics",
        listOf(
            SubArea("Quantum Physics", "quant-ph"),
            SubArea("Astrophysics", "astro-ph"),
            SubArea("Condensed Matter", "cond-mat"),
            SubArea("General Relativity & Quantum Cosmology", "gr-qc"),
            SubArea("High Energy Physics - Phenomenology", "hep-ph"),
            SubArea("High Energy Physics - Theory", "hep-th"),
            SubArea("Nuclear Theory", "nucl-th"),
            SubArea("Optics", "physics.optics"),
            SubArea("Medical Physics", "physics.med-ph"),
            SubArea("Computational Physics", "physics.comp-ph"),
            SubArea("Fluid Dynamics", "physics.flu-dyn"),
            SubArea("Biological Physics", "physics.bio-ph"),
            SubArea("Data Analysis / Statistics / Probability", "physics.data-an")
        )
    ),
    InterestArea(
        "Statistics",
        listOf(
            SubArea("Machine Learning", "stat.ML"),
            SubArea("Applications", "stat.AP"),
            SubArea("Computation", "stat.CO"),
            SubArea("Methodology", "stat.ME"),
            SubArea("Statistics Theory", "stat.TH")
        )
    ),
    InterestArea(
        "Biology",
        listOf(
            SubArea("Biomolecules", "q-bio.BM"),
            SubArea("Cell Behavior", "q-bio.CB"),
            SubArea("Genomics", "q-bio.GN"),
            SubArea("Molecular Networks", "q-bio.MN"),
            SubArea("Neurons & Cognition", "q-bio.NC"),
            SubArea("Other Quantitative Biology", "q-bio.OT"),
            SubArea("Populations & Evolution", "q-bio.PE"),
            SubArea("Quantitative Methods", "q-bio.QM"),
            SubArea("Subcellular Processes", "q-bio.SC"),
            SubArea("Tissues & Organs", "q-bio.TO")
        )
    ),
    InterestArea(
        "Economics",
        listOf(
            SubArea("Econometrics", "econ.EM"),
            SubArea("General Economics", "econ.GN"),
            SubArea("Theoretical Economics", "econ.TH")
        )
    ),
    InterestArea(
        "Quantitative Finance",
        listOf(
            SubArea("Computational Finance", "q-fin.CP"),
            SubArea("Economics", "q-fin.EC"),
            SubArea("General Finance", "q-fin.GN"),
            SubArea("Mathematical Finance", "q-fin.MF"),
            SubArea("Portfolio Management", "q-fin.PM"),
            SubArea("Pricing of Securities", "q-fin.PR"),
            SubArea("Risk Management", "q-fin.RM"),
            SubArea("Statistical Finance", "q-fin.ST"),
            SubArea("Trading & Market Microstructure", "q-fin.TR")
        )
    )
)

private val subAreaByCode: Map<String, SubArea> =
    interestAreas.flatMap { it.subAreas }.associateBy { it.arxivCode }

private fun bookmarksToJsonArray(
    papers: List<Paper>
): JSONArray {
    val array = JSONArray()

    papers.forEach { paper ->
        val obj = JSONObject()
        obj.put("title", paper.title)
        obj.put("summary", paper.summary)
        obj.put("published", paper.published)
        obj.put("arxivId", paper.arxivId)
        obj.put("arxivUrl", paper.arxivUrl)

        val cats = JSONArray()
        paper.categories.forEach { cats.put(it) }
        obj.put("categories", cats)

        val authors = JSONArray()
        paper.authors.forEach { author ->
            val a = JSONObject()
            a.put("name", author.name)
            a.put("affiliation", author.affiliation)
            authors.put(a)
        }
        obj.put("authors", authors)

        array.put(obj)
    }

    return array
}

private fun paperListFromJsonArray(
    array: JSONArray
): List<Paper> {
    return buildList {
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue

            val categories = buildList {
                val cats = obj.optJSONArray("categories") ?: JSONArray()
                for (j in 0 until cats.length()) {
                    val value = cats.optString(j)
                    if (value.isNotBlank()) add(value)
                }
            }

            val authors = buildList {
                val list = obj.optJSONArray("authors") ?: JSONArray()
                for (j in 0 until list.length()) {
                    val a = list.optJSONObject(j) ?: continue
                    val name = a.optString("name")
                    if (name.isNotBlank()) {
                        add(
                            Author(
                                name = name,
                                affiliation = a.optString("affiliation")
                            )
                        )
                    }
                }
            }

            val arxivId = obj.optString("arxivId")
            val title = obj.optString("title")
            if (arxivId.isBlank() && title.isBlank()) continue

            add(
                Paper(
                    title = title,
                    summary = obj.optString("summary"),
                    authors = authors,
                    categories = categories,
                    published = obj.optString("published"),
                    arxivId = arxivId,
                    arxivUrl = obj.optString("arxivUrl")
                )
            )
        }
    }
}

private fun parseBookmarksBackup(
    raw: String
): List<Paper> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return emptyList()

    return try {
        if (trimmed.startsWith("[")) {
            // Backward compatible with the app's existing raw bookmark array.
            paperListFromJsonArray(JSONArray(trimmed))
        } else {
            val root = JSONObject(trimmed)
            val array = root.optJSONArray("bookmarks") ?: JSONArray()
            paperListFromJsonArray(array)
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun bookmarksBackupJson(
    papers: List<Paper>
): String {
    return JSONObject().apply {
        put("format", "arxiv-paper-finder-bookmarks")
        put("version", 1)
        put(
            "exportedAt",
            SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                Locale.getDefault()
            ).format(Date())
        )
        put("bookmarks", bookmarksToJsonArray(papers))
    }.toString(2)
}

private fun saveBookmarks(
    prefs: android.content.SharedPreferences,
    papers: List<Paper>
) {
    prefs.edit()
        .putString(
            "bookmarks_json",
            bookmarksToJsonArray(papers).toString()
        )
        .apply()
}

private fun loadBookmarks(
    prefs: android.content.SharedPreferences
): List<Paper> {
    val raw = prefs.getString("bookmarks_json", "[]") ?: "[]"
    return parseBookmarksBackup(raw)
}

private fun mergeBookmarks(
    current: List<Paper>,
    imported: List<Paper>
): List<Paper> {
    val merged = LinkedHashMap<String, Paper>()

    (current + imported).forEach { paper ->
        val key = if (paper.arxivId.isNotBlank()) {
            "id:${paper.arxivId.lowercase(Locale.ROOT)}"
        } else {
            "title:${paper.title.lowercase(Locale.ROOT)}"
        }
        merged[key] = paper
    }

    return merged.values.toList()
}

private fun writeBookmarksBackup(
    context: Context,
    uri: Uri,
    papers: List<Paper>
): Boolean {
    return try {
        val stream = context.contentResolver
            .openOutputStream(uri, "wt")
            ?: return false

        stream.bufferedWriter().use { writer ->
            writer.write(bookmarksBackupJson(papers))
        }
        true
    } catch (_: Exception) {
        false
    }
}

private fun readBookmarksBackup(
    context: Context,
    uri: Uri
): List<Paper>? {
    return try {
        val raw = context.contentResolver
            .openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return null

        parseBookmarksBackup(raw)
    } catch (_: Exception) {
        null
    }
}

private const val BACKUP_PREFS = "arxiv_bookmark_backup"
private const val BACKUP_FOLDER_URI = "backup_folder_uri"
private const val WEEKLY_BACKUP_ENABLED = "weekly_backup_enabled"
private const val WEEKLY_BACKUP_WORK = "arxiv_bookmark_weekly_backup"
private const val WEEKLY_BACKUP_FILE = "arxiv-bookmarks-latest.json"

private fun setWeeklyBookmarkBackup(
    context: Context,
    enabled: Boolean
) {
    val prefs = context.getSharedPreferences(
        BACKUP_PREFS,
        Context.MODE_PRIVATE
    )
    prefs.edit()
        .putBoolean(WEEKLY_BACKUP_ENABLED, enabled)
        .apply()

    val workManager = WorkManager.getInstance(context)

    if (!enabled) {
        workManager.cancelUniqueWork(WEEKLY_BACKUP_WORK)
        return
    }

    val request = PeriodicWorkRequestBuilder<BookmarkBackupWorker>(
        7,
        TimeUnit.DAYS
    ).build()

    workManager.enqueueUniquePeriodicWork(
        WEEKLY_BACKUP_WORK,
        ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
}

class BookmarkBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val backupPrefs = applicationContext.getSharedPreferences(
            BACKUP_PREFS,
            Context.MODE_PRIVATE
        )

        if (!backupPrefs.getBoolean(WEEKLY_BACKUP_ENABLED, false)) {
            return@withContext Result.success()
        }

        val folderString = backupPrefs.getString(BACKUP_FOLDER_URI, null)
            ?: return@withContext Result.success()

        val folderUri = try {
            Uri.parse(folderString)
        } catch (_: Exception) {
            return@withContext Result.failure()
        }

        val bookmarkPrefs = applicationContext.getSharedPreferences(
            "arxiv_bookmarks",
            Context.MODE_PRIVATE
        )
        val papers = loadBookmarks(bookmarkPrefs)

        try {
            val tree = DocumentFile.fromTreeUri(
                applicationContext,
                folderUri
            ) ?: return@withContext Result.failure()

            val file = tree.findFile(WEEKLY_BACKUP_FILE)
                ?: tree.createFile(
                    "application/json",
                    WEEKLY_BACKUP_FILE
                )
                ?: return@withContext Result.failure()

            val ok = writeBookmarksBackup(
                applicationContext,
                file.uri,
                papers
            )

            if (ok) Result.success() else Result.retry()
        } catch (_: SecurityException) {
            Result.failure()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ArxivRoot()
        }
    }
}

@Composable
fun ArxivRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current

    val themePrefs = remember {
        context.getSharedPreferences(
            "arxiv_theme",
            Context.MODE_PRIVATE
        )
    }

    var darkMode by rememberSaveable {
        mutableStateOf(
            themePrefs.getBoolean("dark_mode", false)
        )
    }

    MaterialTheme(
        colorScheme =
            if (darkMode) {
                darkColorScheme()
            } else {
                lightColorScheme()
            }
    ) {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            ArxivApp(
                darkMode = darkMode,
                onToggleDarkMode = {
                    darkMode = !darkMode
                    themePrefs
                        .edit()
                        .putBoolean(
                            "dark_mode",
                            darkMode
                        )
                        .apply()
                }
            )
        }
    }
}

@Composable
fun ArxivApp(
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("arxiv_interests", Context.MODE_PRIVATE)
    }

    val bookmarkPrefs = remember {
        context.getSharedPreferences("arxiv_bookmarks", Context.MODE_PRIVATE)
    }

    var bookmarks by remember {
        mutableStateOf(loadBookmarks(bookmarkPrefs))
    }

    var currentTab by remember {
        mutableStateOf("Papers")
    }

    // Search state is intentionally owned by ArxivApp instead of SearchScreen.
    // SearchScreen leaves the composition when Bookmarks is selected; keeping
    // these values here prevents them from being reset when returning to Papers.
    var searchKeyword by rememberSaveable {
        mutableStateOf("")
    }
    var searchKeywordMode by rememberSaveable {
        mutableStateOf("Any")
    }
    var searchPeriodDays by rememberSaveable {
        mutableIntStateOf(7)
    }
    var searchMaxResults by rememberSaveable {
        mutableIntStateOf(100)
    }
    var searchPapers by remember {
        mutableStateOf<List<Paper>>(emptyList())
    }
    var searchStatus by rememberSaveable {
        mutableStateOf("Ready")
    }
    var searchListIndex by rememberSaveable {
        mutableIntStateOf(0)
    }
    var searchListOffset by rememberSaveable {
        mutableIntStateOf(0)
    }

    var inAppWebUrl by remember {
        mutableStateOf<String?>(null)
    }

    var inAppWebTitle by remember {
        mutableStateOf("arXiv")
    }

    var selectedCodes by remember {
        mutableStateOf(loadInterestCodes(prefs))
    }

    var andCodes by remember {
        mutableStateOf(loadAndCodes(prefs).intersect(selectedCodes))
    }

    var editingInterests by remember {
        mutableStateOf(selectedCodes.isEmpty())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
    if (editingInterests) {
        InterestSetupScreen(
            initialCodes = selectedCodes,
            onDone = { newCodes ->
                selectedCodes = newCodes
                andCodes = andCodes.intersect(newCodes)
                saveInterestCodes(prefs, newCodes)
                saveAndCodes(prefs, andCodes)
                editingInterests = false
            }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRow(
                selectedTabIndex = if (currentTab == "Papers") 0 else 1
            ) {
                Tab(
                    selected = currentTab == "Papers",
                    onClick = { currentTab = "Papers" },
                    text = { Text("Papers") }
                )
                Tab(
                    selected = currentTab == "Bookmarks",
                    onClick = { currentTab = "Bookmarks" },
                    text = {
                        Text("Bookmarks (${bookmarks.size})")
                    }
                )
            }

            if (currentTab == "Papers") {
                SearchScreen(
                    selectedCodes = selectedCodes,
                    andCodes = andCodes,
                    bookmarks = bookmarks,
                    keyword = searchKeyword,
                    onKeywordChange = { searchKeyword = it },
                    keywordMode = searchKeywordMode,
                    onKeywordModeChange = { searchKeywordMode = it },
                    periodDays = searchPeriodDays,
                    onPeriodDaysChange = { searchPeriodDays = it },
                    maxResults = searchMaxResults,
                    onMaxResultsChange = { searchMaxResults = it },
                    papers = searchPapers,
                    onPapersChange = { searchPapers = it },
                    status = searchStatus,
                    onStatusChange = { searchStatus = it },
                    initialListIndex = searchListIndex,
                    initialListOffset = searchListOffset,
                    onListPositionChange = { index, offset ->
                        searchListIndex = index
                        searchListOffset = offset
                    },
                    onToggleLogic = { code ->
                        andCodes =
                            if (code in andCodes) {
                                andCodes - code
                            } else {
                                andCodes + code
                            }
                        saveAndCodes(prefs, andCodes)
                    },
                    onToggleBookmark = { paper ->
                        bookmarks =
                            if (bookmarks.any { it.arxivId == paper.arxivId }) {
                                bookmarks.filterNot {
                                    it.arxivId == paper.arxivId
                                }
                            } else {
                                bookmarks + paper
                            }
                        saveBookmarks(bookmarkPrefs, bookmarks)
                    },
                    onEditInterests = { editingInterests = true },
                    darkMode = darkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    onOpenInApp = { title, url ->
                        inAppWebTitle = title
                        inAppWebUrl = url
                    }
                )
            } else {
                BookmarksScreen(
                    bookmarks = bookmarks,
                    darkMode = darkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    onToggleBookmark = { paper ->
                        bookmarks =
                            bookmarks.filterNot {
                                it.arxivId == paper.arxivId
                            }
                        saveBookmarks(bookmarkPrefs, bookmarks)
                    },
                    onImportBookmarks = { imported ->
                        bookmarks = mergeBookmarks(
                            bookmarks,
                            imported
                        )
                        saveBookmarks(bookmarkPrefs, bookmarks)
                    },
                    onOpenInApp = { title, url ->
                        inAppWebTitle = title
                        inAppWebUrl = url
                    }
                )
            }
        }
    }
    }

    inAppWebUrl?.let { url ->
        InAppWebPage(
            title = inAppWebTitle,
            url = url,
            darkMode = darkMode,
            onClose = {
                inAppWebUrl = null
            }
        )
    }
}

private fun loadInterestCodes(
    prefs: android.content.SharedPreferences
): Set<String> {
    return prefs.getStringSet("codes", emptySet())?.toSet() ?: emptySet()
}

private fun saveInterestCodes(
    prefs: android.content.SharedPreferences,
    codes: Set<String>
) {
    prefs.edit().putStringSet("codes", codes).apply()
}

private fun loadAndCodes(
    prefs: android.content.SharedPreferences
): Set<String> {
    return prefs.getStringSet("and_codes", emptySet())?.toSet() ?: emptySet()
}

private fun saveAndCodes(
    prefs: android.content.SharedPreferences,
    codes: Set<String>
) {
    prefs.edit().putStringSet("and_codes", codes).apply()
}


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun InterestSetupScreen(
    initialCodes: Set<String>,
    onDone: (Set<String>) -> Unit
) {
    var selectedCodes by remember { mutableStateOf(initialCodes) }
    var selectedArea by remember { mutableStateOf<InterestArea?>(null) }
    var pendingCodes by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Choose your interests",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "You can add several research areas",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (selectedArea == null) {
                Text(
                    if (selectedCodes.isEmpty())
                        "What broad area are you interested in?"
                    else
                        "Add another area",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "Pick an area first. You will choose its sub-areas next.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(16.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    interestAreas.forEach { area ->
                        AssistChip(
                            onClick = {
                                selectedArea = area
                                pendingCodes = area.subAreas
                                    .map { it.arxivCode }
                                    .filter { it in selectedCodes }
                                    .toSet()
                            },
                            label = { Text(area.name) }
                        )
                    }
                }

                if (selectedCodes.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Your selected interests",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedCodes.sorted().forEach { code ->
                            val sub = subAreaByCode[code]
                            InputChip(
                                selected = true,
                                onClick = {
                                    selectedCodes = selectedCodes - code
                                },
                                label = {
                                    Text(sub?.name ?: code)
                                },
                                trailingIcon = {
                                    Text("×")
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = { onDone(selectedCodes) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Continue to papers")
                    }
                }
            } else {
                val area = selectedArea!!

                Text(
                    area.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    "Which sub-areas interest you? Select as many as you want.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(16.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    area.subAreas.forEach { sub ->
                        val selected = sub.arxivCode in pendingCodes

                        FilterChip(
                            selected = selected,
                            onClick = {
                                pendingCodes =
                                    if (selected) {
                                        pendingCodes - sub.arxivCode
                                    } else {
                                        pendingCodes + sub.arxivCode
                                    }
                            },
                            label = { Text(sub.name) }
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                Button(
                    onClick = {
                        selectedCodes = selectedCodes + pendingCodes
                        selectedArea = null
                        pendingCodes = emptySet()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = pendingCodes.isNotEmpty()
                ) {
                    Text("Add selected sub-areas")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        selectedArea = null
                        pendingCodes = emptySet()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(
    selectedCodes: Set<String>,
    andCodes: Set<String>,
    bookmarks: List<Paper>,
    keyword: String,
    onKeywordChange: (String) -> Unit,
    keywordMode: String,
    onKeywordModeChange: (String) -> Unit,
    periodDays: Int,
    onPeriodDaysChange: (Int) -> Unit,
    maxResults: Int,
    onMaxResultsChange: (Int) -> Unit,
    papers: List<Paper>,
    onPapersChange: (List<Paper>) -> Unit,
    status: String,
    onStatusChange: (String) -> Unit,
    initialListIndex: Int,
    initialListOffset: Int,
    onListPositionChange: (Int, Int) -> Unit,
    onToggleLogic: (String) -> Unit,
    onToggleBookmark: (Paper) -> Unit,
    onEditInterests: () -> Unit,
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onOpenInApp: (String, String) -> Unit
) {
    var selectedPaper by remember {
        mutableStateOf<Paper?>(null)
    }
    var loading by remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialListIndex,
        initialFirstVisibleItemScrollOffset = initialListOffset
    )

    LaunchedEffect(
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset
    ) {
        onListPositionChange(
            listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset
        )
    }

    val compactHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 120
        }
    }

    fun runSearch() {
        loading = true
        onStatusChange("Searching arXiv…")

        scope.launch {
            try {
                val foundPapers = fetchArxivPapers(
                    keyword = keyword,
                    keywordMode = keywordMode,
                    days = periodDays,
                    selectedCodes = selectedCodes,
                    andCodes = andCodes,
                    maxResults = maxResults
                )

                onPapersChange(foundPapers)
                onStatusChange(
                    "${foundPapers.size} paper" +
                        if (foundPapers.size == 1) {
                            " found"
                        } else {
                            "s found"
                        }
                )
            } catch (e: Exception) {
                onPapersChange(emptyList())
                onStatusChange(
                    "Search failed: " +
                        (e.message ?: e.javaClass.simpleName)
                )
            } finally {
                loading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "arXiv Paper Finder",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Your research interests",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onToggleDarkMode
                    ) {
                        Text(
                            if (darkMode) {
                                "Light"
                            } else {
                                "Night"
                            }
                        )
                    }

                    TextButton(
                        onClick = onEditInterests
                    ) {
                        Text("Edit interests")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (compactHeader) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 12.dp,
                                vertical = 8.dp
                            )
                    ) {
                        OutlinedTextField(
                            value = keyword,
                            onValueChange = onKeywordChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Keywords") },
                            placeholder = {
                                Text("e.g. diffusion, beamforming")
                            },
                            singleLine = true
                        )

                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(8.dp),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            SimpleDropdown(
                                modifier = Modifier.weight(0.42f),
                                label = "Period",
                                selected = when (periodDays) {
                                    1 -> "1 day"
                                    30 -> "1 month"
                                    else -> "1 week"
                                },
                                options = listOf(
                                    "1 day",
                                    "1 week",
                                    "1 month"
                                ),
                                onSelected = {
                                    onPeriodDaysChange(
                                        when (it) {
                                            "1 day" -> 1
                                            "1 month" -> 30
                                            else -> 7
                                        }
                                    )
                                }
                            )

                            Button(
                                onClick = { runSearch() },
                                modifier = Modifier
                                    .weight(0.58f)
                                    .heightIn(min = 48.dp),
                                enabled = !loading
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }

                                Text(
                                    if (loading) {
                                        "Searching…"
                                    } else {
                                        "Search arXiv"
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp
                    )
                ) {
                    Text(
                        "Tap an interest pill to switch OR ↔ AND. " +
                            "AND pills have a red outline.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement =
                            Arrangement.spacedBy(6.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(6.dp)
                    ) {
                        selectedCodes.sorted().forEach { code ->
                            val isAnd = code in andCodes
                            val name =
                                subAreaByCode[code]?.name ?: code

                            OutlinedButton(
                                onClick = {
                                    onToggleLogic(code)
                                },
                                shape = CircleShape,
                                border = BorderStroke(
                                    if (isAnd) 2.dp else 1.dp,
                                    if (isAnd) {
                                        Color.Red
                                    } else {
                                        MaterialTheme
                                            .colorScheme
                                            .outline
                                    }
                                ),
                                contentPadding = PaddingValues(
                                    horizontal = 12.dp,
                                    vertical = 5.dp
                                )
                            ) {
                                Text(
                                    "$name ($code) · " +
                                        if (isAnd) {
                                            "AND"
                                        } else {
                                            "OR"
                                        }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = keyword,
                        onValueChange = onKeywordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Keywords") },
                        placeholder = {
                            Text("e.g. diffusion, beamforming")
                        },
                        singleLine = true
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        SimpleDropdown(
                            modifier = Modifier.weight(1f),
                            label = "Keyword match",
                            selected = keywordMode,
                            options = listOf("Any", "All"),
                            onSelected = onKeywordModeChange
                        )

                        SimpleDropdown(
                            modifier = Modifier.weight(1f),
                            label = "Period",
                            selected = when (periodDays) {
                                1 -> "1 day"
                                30 -> "1 month"
                                else -> "1 week"
                            },
                            options = listOf(
                                "1 day",
                                "1 week",
                                "1 month"
                            ),
                            onSelected = {
                                onPeriodDaysChange(
                                    when (it) {
                                        "1 day" -> 1
                                        "1 month" -> 30
                                        else -> 7
                                    }
                                )
                            }
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    SimpleDropdown(
                        modifier = Modifier.fillMaxWidth(),
                        label = "Max results",
                        selected = maxResults.toString(),
                        options = listOf(
                            "50",
                            "100",
                            "200",
                            "300"
                        ),
                        onSelected = {
                            onMaxResultsChange(it.toInt())
                        }
                    )

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = { runSearch() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Searching…")
                        } else {
                            Text("Search arXiv")
                        }
                    }
                }
            }

            Text(
                text = status,
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 8.dp
                ),
                style = MaterialTheme.typography.bodySmall
            )

            if (papers.isEmpty() && !loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (status.startsWith("Search failed")) {
                            status
                        } else {
                            "Search papers matching your interests."
                        }
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = 20.dp
                    ),
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        papers,
                        key = { it.arxivId }
                    ) { paper ->
                        PaperCard(
                            paper = paper,
                            isBookmarked = bookmarks.any {
                                it.arxivId == paper.arxivId
                            },
                            onBookmark = {
                                onToggleBookmark(paper)
                            },
                            onClick = {
                                selectedPaper = paper
                            }
                        )
                    }
                }
            }
        }
    }

    selectedPaper?.let { paper ->
        ModalBottomSheet(
            onDismissRequest = {
                selectedPaper = null
            }
        ) {
            PaperDetails(
                paper = paper,
                onOpenInApp = { title, url ->
                    selectedPaper = null
                    onOpenInApp(title, url)
                }
            )
        }
    }
}

@Composable
private fun SimpleDropdown(
    modifier: Modifier,
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(
                start = 4.dp,
                bottom = 4.dp
            )
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selected,
                    modifier = Modifier.weight(1f)
                )
                Text("▼")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(option)
                        },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PaperCard(
    paper: Paper,
    isBookmarked: Boolean,
    onBookmark: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        paper.published,
                        style = MaterialTheme.typography.labelSmall
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        paper.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                TextButton(
                    onClick = onBookmark,
                    contentPadding = PaddingValues(
                        horizontal = 8.dp,
                        vertical = 2.dp
                    )
                ) {
                    Text(
                        if (isBookmarked) "★" else "☆",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            val authorText =
                paper.authors.joinToString(", ") { it.name }

            Text(
                if (authorText.length > 180)
                    authorText.take(177) + "…"
                else
                    authorText,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(8.dp))

            Text(
                paper.categories
                    .take(6)
                    .joinToString("  •  "),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksScreen(
    bookmarks: List<Paper>,
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onToggleBookmark: (Paper) -> Unit,
    onImportBookmarks: (List<Paper>) -> Unit,
    onOpenInApp: (String, String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val backupPrefs = remember {
        context.getSharedPreferences(
            BACKUP_PREFS,
            Context.MODE_PRIVATE
        )
    }

    var selectedPaper by remember {
        mutableStateOf<Paper?>(null)
    }
    var backupMenuExpanded by remember {
        mutableStateOf(false)
    }
    var backupStatus by remember {
        mutableStateOf("")
    }
    var weeklyEnabled by remember {
        mutableStateOf(
            backupPrefs.getBoolean(
                WEEKLY_BACKUP_ENABLED,
                false
            )
        )
    }
    var backupFolderName by remember {
        mutableStateOf(
            backupPrefs.getString(
                BACKUP_FOLDER_URI,
                null
            )?.let { "Backup folder selected" } ?: "No backup folder"
        )
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/json"
        )
    ) { uri ->
        if (uri != null) {
            val ok = writeBookmarksBackup(
                context,
                uri,
                bookmarks
            )
            backupStatus = if (ok) {
                "Bookmarks exported successfully."
            } else {
                "Could not export bookmarks."
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val imported = readBookmarksBackup(
                context,
                uri
            )

            if (imported == null) {
                backupStatus = "Could not read that backup file."
            } else {
                onImportBookmarks(imported)
                backupStatus =
                    "Imported ${imported.size} bookmark" +
                        if (imported.size == 1) "." else "s. Existing bookmarks were kept."
            }
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                backupPrefs.edit()
                    .putString(
                        BACKUP_FOLDER_URI,
                        uri.toString()
                    )
                    .apply()

                backupFolderName = "Backup folder selected"
                weeklyEnabled = true
                setWeeklyBookmarkBackup(
                    context,
                    true
                )
                backupStatus =
                    "Weekly automatic backup enabled. The latest backup will be saved as $WEEKLY_BACKUP_FILE."
            } catch (_: Exception) {
                backupStatus = "Could not keep access to that folder."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Bookmarks",
                            fontWeight = FontWeight.Bold
                        )
                        if (weeklyEnabled) {
                            Text(
                                "Weekly backup on",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = onToggleDarkMode
                    ) {
                        Text(
                            if (darkMode) {
                                "Light"
                            } else {
                                "Night"
                            }
                        )
                    }

                    Box {
                        TextButton(
                            onClick = {
                                backupMenuExpanded = true
                            }
                        ) {
                            Text("Backup")
                        }

                        DropdownMenu(
                            expanded = backupMenuExpanded,
                            onDismissRequest = {
                                backupMenuExpanded = false
                            }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export bookmarks now") },
                                onClick = {
                                    backupMenuExpanded = false
                                    val stamp = SimpleDateFormat(
                                        "yyyy-MM-dd",
                                        Locale.getDefault()
                                    ).format(Date())
                                    exportLauncher.launch(
                                        "arxiv-bookmarks-$stamp.json"
                                    )
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Import bookmarks") },
                                onClick = {
                                    backupMenuExpanded = false
                                    importLauncher.launch(
                                        arrayOf(
                                            "application/json",
                                            "text/plain"
                                        )
                                    )
                                }
                            )

                            HorizontalDivider()

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (weeklyEnabled) {
                                            "Change weekly backup folder"
                                        } else {
                                            "Enable weekly backup"
                                        }
                                    )
                                },
                                onClick = {
                                    backupMenuExpanded = false
                                    folderLauncher.launch(null)
                                }
                            )

                            if (weeklyEnabled) {
                                DropdownMenuItem(
                                    text = {
                                        Text("Disable weekly backup")
                                    },
                                    onClick = {
                                        backupMenuExpanded = false
                                        weeklyEnabled = false
                                        setWeeklyBookmarkBackup(
                                            context,
                                            false
                                        )
                                        backupStatus =
                                            "Weekly automatic backup disabled."
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (backupStatus.isNotBlank()) {
                Text(
                    backupStatus,
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (weeklyEnabled) {
                Text(
                    "$backupFolderName · automatic backup every 7 days",
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (bookmarks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No bookmarked papers yet.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        bookmarks,
                        key = { it.arxivId }
                    ) { paper ->
                        PaperCard(
                            paper = paper,
                            isBookmarked = true,
                            onBookmark = {
                                onToggleBookmark(paper)
                            },
                            onClick = {
                                selectedPaper = paper
                            }
                        )
                    }
                }
            }
        }
    }

    selectedPaper?.let { paper ->
        ModalBottomSheet(
            onDismissRequest = {
                selectedPaper = null
            }
        ) {
            PaperDetails(
                paper = paper,
                onOpenInApp = { title, url ->
                    selectedPaper = null
                    onOpenInApp(title, url)
                }
            )
        }
    }
}

@Composable
private fun PaperDetails(
    paper: Paper,
    onOpenInApp: (String, String) -> Unit
) {
    var htmlAvailable by remember(paper.arxivId) {
        mutableStateOf<Boolean?>(null)
    }

    LaunchedEffect(paper.arxivId) {
        htmlAvailable = checkArxivHtmlAvailable(paper.arxivId)
    }

    val htmlUrl = "https://arxiv.org/html/${paper.arxivId}"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            Text(
                "Selected paper",
                style = MaterialTheme.typography.labelMedium
            )

            Spacer(Modifier.height(5.dp))

            Text(
                paper.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(10.dp))

            Text("Published: ${paper.published}")
            Text("arXiv ID: ${paper.arxivId}")
            Text(
                "Categories: " +
                    paper.categories.joinToString(", ")
            )

            Spacer(Modifier.height(18.dp))

            Text(
                "Authors / associated institution or industry",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            paper.authors.forEach { author ->
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            author.name,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            author.affiliation.ifBlank {
                                "Affiliation not listed by arXiv"
                            },
                            style =
                                MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                "Abstract",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                paper.summary,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(20.dp))
        }

        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    onOpenInApp(
                        "arXiv",
                        paper.arxivUrl
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Go to arXiv")
            }

            Button(
                onClick = {
                    onOpenInApp(
                        "arXiv HTML",
                        htmlUrl
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = htmlAvailable == true
            ) {
                when (htmlAvailable) {
                    null -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("HTML")
                    }
                    true -> Text("View HTML")
                    false -> Text("HTML unavailable")
                }
            }
        }
    }
}

private suspend fun checkArxivHtmlAvailable(
    arxivId: String
): Boolean = withContext(Dispatchers.IO) {
    val url = URL("https://arxiv.org/html/$arxivId")

    val connection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "ArxivPaperFinderAndroid/14.0"
            )
            setRequestProperty(
                "Accept",
                "text/html"
            )
            // We only need to determine whether the HTML representation exists.
            setRequestProperty("Range", "bytes=0-1023")
        }

    try {
        val status = connection.responseCode
        status in 200..299
    } catch (_: Exception) {
        false
    } finally {
        connection.disconnect()
    }
}

private fun applyWebViewTheme(
    webView: WebView,
    darkMode: Boolean
) {
    if (
        WebViewFeature.isFeatureSupported(
            WebViewFeature.ALGORITHMIC_DARKENING
        )
    ) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(
            webView.settings,
            darkMode
        )
    }

    webView.setBackgroundColor(
        if (darkMode) {
            android.graphics.Color.rgb(18, 18, 18)
        } else {
            android.graphics.Color.WHITE
        }
    )

    applyArxivDocumentTheme(
        webView = webView,
        darkMode = darkMode
    )
}

private fun applyArxivDocumentTheme(
    webView: WebView,
    darkMode: Boolean
) {
    val javascript =
        if (darkMode) {
            """
            (function() {
                var styleId = 'arxiv-paper-finder-dark-theme';
                var oldStyle = document.getElementById(styleId);
                if (oldStyle) {
                    oldStyle.remove();
                }

                document.documentElement.style.colorScheme = 'dark';

                var style = document.createElement('style');
                style.id = styleId;
                style.textContent = `
                    html,
                    body,
                    .ltx_page_main,
                    .ltx_page_content,
                    .ltx_document,
                    .ltx_main,
                    .ltx_article {
                        background: #121212 !important;
                        color: #e8e8e8 !important;
                    }

                    article,
                    section,
                    header,
                    footer,
                    nav,
                    aside {
                        color: #e8e8e8 !important;
                    }

                    p,
                    span,
                    li,
                    dt,
                    dd,
                    blockquote,
                    figcaption,
                    caption,
                    th,
                    td,
                    h1,
                    h2,
                    h3,
                    h4,
                    h5,
                    h6 {
                        color: inherit !important;
                    }

                    a,
                    a:visited {
                        color: #9ecbff !important;
                    }

                    pre,
                    code,
                    .ltx_verbatim,
                    .ltx_listing {
                        background: #1d1d1d !important;
                        color: #eeeeee !important;
                    }

                    table,
                    th,
                    td {
                        border-color: #666666 !important;
                    }

                    .ltx_note,
                    .ltx_theorem,
                    .ltx_proof,
                    .ltx_example,
                    .ltx_definition {
                        background-color: transparent !important;
                    }

                    /* Keep figures, plots and raster images in their
                       original colours. */
                    img,
                    canvas,
                    video {
                        background: white;
                    }

                    /* MathML normally inherits text colour. */
                    math,
                    .ltx_Math,
                    .ltx_math {
                        color: #eeeeee !important;
                    }

                    ::selection {
                        background: #5f4b8b !important;
                        color: white !important;
                    }
                `;

                document.head.appendChild(style);
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                var style =
                    document.getElementById(
                        'arxiv-paper-finder-dark-theme'
                    );

                if (style) {
                    style.remove();
                }

                document.documentElement.style.colorScheme = 'light';
            })();
            """.trimIndent()
        }

    webView.evaluateJavascript(
        javascript,
        null
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InAppWebPage(
    title: String,
    url: String,
    darkMode: Boolean,
    onClose: () -> Unit
) {
    var canGoBack by remember {
        mutableStateOf(false)
    }

    var webViewRef by remember {
        mutableStateOf<WebView?>(null)
    }

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    TextButton(
                        onClick = {
                            val webView = webViewRef
                            if (
                                webView != null &&
                                webView.canGoBack()
                            ) {
                                webView.goBack()
                                canGoBack = webView.canGoBack()
                            } else {
                                onClose()
                            }
                        }
                    ) {
                        Text(
                            if (canGoBack)
                                "Back"
                            else
                                "Close"
                        )
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webViewClient =
                        object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView?,
                                finishedUrl: String?
                            ) {
                                canGoBack =
                                    view?.canGoBack() == true

                                view?.let {
                                    applyWebViewTheme(
                                        webView = it,
                                        darkMode = darkMode
                                    )
                                }
                            }
                        }

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true

                    applyWebViewTheme(
                        webView = this,
                        darkMode = darkMode
                    )

                    webViewRef = this
                    loadUrl(url)
                }
            },
            update = { webView ->
                applyWebViewTheme(
                    webView = webView,
                    darkMode = darkMode
                )

                if (webView.url != url) {
                    webView.loadUrl(url)
                }

                webViewRef = webView
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }
}

private suspend fun fetchArxivPapers(
    keyword: String,
    keywordMode: String,
    days: Int,
    selectedCodes: Set<String>,
    andCodes: Set<String>,
    maxResults: Int
): List<Paper> = withContext(Dispatchers.IO) {

    val formatter =
        SimpleDateFormat(
            "yyyyMMddHHmm",
            Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    val now = Date()
    val start = Date(
        now.time -
        days * 24L * 60L * 60L * 1000L
    )

    val dateFilter =
        "submittedDate:[" +
        "${formatter.format(start)} TO " +
        "${formatter.format(now)}]"

    val requiredCodes =
        andCodes.intersect(selectedCodes).sorted()

    val optionalCodes =
        (selectedCodes - requiredCodes.toSet()).sorted()

    val andPart =
        requiredCodes.joinToString(" AND ") { code ->
            "cat:$code"
        }

    val orPart =
        optionalCodes.joinToString(" OR ") { code ->
            "cat:$code"
        }

    val interestFilter =
        when {
            andPart.isNotBlank() && orPart.isNotBlank() ->
                "$andPart AND ($orPart)"

            andPart.isNotBlank() ->
                andPart

            orPart.isNotBlank() ->
                "($orPart)"

            else ->
                ""
        }

    val keywordTerms =
        keyword
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

    fun escapeArxivPhrase(term: String): String {
        return term.replace("\"", "\\\"")
    }

    val keywordClauses =
        keywordTerms.map { term ->
            val escaped = escapeArxivPhrase(term)
            val fieldTerm =
                if (escaped.contains(" ")) {
                    "\"$escaped\""
                } else {
                    escaped
                }

            "(ti:$fieldTerm OR abs:$fieldTerm)"
        }

    val keywordFilter =
        when {
            keywordClauses.isEmpty() ->
                ""

            keywordMode == "All" ->
                keywordClauses.joinToString(" AND ")

            else ->
                keywordClauses.joinToString(" OR ")
        }

    val queryParts =
        mutableListOf<String>()

    if (interestFilter.isNotBlank()) {
        queryParts.add("($interestFilter)")
    }

    if (keywordFilter.isNotBlank()) {
        queryParts.add("($keywordFilter)")
    }

    queryParts.add(dateFilter)

    val searchQuery =
        queryParts.joinToString(" AND ")

    val encodedQuery =
        URLEncoder.encode(
            searchQuery,
            StandardCharsets.UTF_8.toString()
        )

    val url = URL(
        "https://export.arxiv.org/api/query" +
        "?search_query=$encodedQuery" +
        "&start=0" +
        "&max_results=${min(maxResults, 300)}" +
        "&sortBy=submittedDate" +
        "&sortOrder=descending"
    )

    val connection =
        (url.openConnection() as HttpURLConnection)
            .apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty(
                    "User-Agent",
                    "ArxivPaperFinderAndroid/14.0"
                )
                setRequestProperty(
                    "Accept",
                    "application/atom+xml"
                )
            }

    try {
        val status = connection.responseCode

        if (status !in 200..299) {
            throw IllegalStateException(
                "arXiv returned HTTP $status"
            )
        }

        val papers =
            BufferedInputStream(
                connection.inputStream
            ).use {
                parseArxivFeed(it)
            }

        papers
    } finally {
        connection.disconnect()
    }
}

private fun parseArxivFeed(
    input: java.io.InputStream
): List<Paper> {
    val parser = Xml.newPullParser()

    parser.setFeature(
        XmlPullParser.FEATURE_PROCESS_NAMESPACES,
        true
    )
    parser.setInput(input, "UTF-8")

    val papers = mutableListOf<Paper>()
    var event = parser.eventType

    var inEntry = false
    var inAuthor = false

    var title = ""
    var summary = ""
    var published = ""
    var arxivId = ""
    var arxivUrl = ""

    var categoriesList =
        mutableListOf<String>()
    var authorsList =
        mutableListOf<Author>()

    var currentAuthorName = ""
    var currentAffiliations =
        mutableListOf<String>()

    while (
        event != XmlPullParser.END_DOCUMENT
    ) {
        when (event) {
            XmlPullParser.START_TAG -> {
                val name = parser.name
                val namespace =
                    parser.namespace ?: ""

                when {
                    name == "entry" -> {
                        inEntry = true
                        title = ""
                        summary = ""
                        published = ""
                        arxivId = ""
                        arxivUrl = ""
                        categoriesList =
                            mutableListOf()
                        authorsList =
                            mutableListOf()
                    }

                    inEntry &&
                    name == "author" -> {
                        inAuthor = true
                        currentAuthorName = ""
                        currentAffiliations =
                            mutableListOf()
                    }

                    inEntry &&
                    inAuthor &&
                    name == "name" -> {
                        currentAuthorName =
                            parser.nextText().trim()
                    }

                    inEntry &&
                    inAuthor &&
                    name == "affiliation" &&
                    namespace ==
                    "http://arxiv.org/schemas/atom" -> {
                        currentAffiliations.add(
                            parser.nextText().trim()
                        )
                    }

                    inEntry &&
                    !inAuthor &&
                    name == "title" -> {
                        title =
                            parser.nextText()
                                .replace(
                                    Regex("\\s+"),
                                    " "
                                )
                                .trim()
                    }

                    inEntry &&
                    name == "summary" -> {
                        summary =
                            parser.nextText()
                                .replace(
                                    Regex("\\s+"),
                                    " "
                                )
                                .trim()
                    }

                    inEntry &&
                    name == "published" -> {
                        published =
                            parser.nextText()
                                .take(10)
                    }

                    inEntry &&
                    name == "id" -> {
                        val idUrl =
                            parser.nextText().trim()

                        arxivId =
                            idUrl.substringAfterLast("/")
                    }

                    inEntry &&
                    name == "category" -> {
                        parser
                            .getAttributeValue(
                                null,
                                "term"
                            )
                            ?.let {
                                categoriesList.add(it)
                            }
                    }

                    inEntry &&
                    name == "link" -> {
                        val rel =
                            parser.getAttributeValue(
                                null,
                                "rel"
                            )
                        val href =
                            parser.getAttributeValue(
                                null,
                                "href"
                            )

                        if (
                            rel == "alternate" &&
                            !href.isNullOrBlank()
                        ) {
                            arxivUrl =
                                href.replaceFirst(
                                    "http://",
                                    "https://"
                                )
                        }
                    }
                }
            }

            XmlPullParser.END_TAG -> {
                when (parser.name) {
                    "author" -> {
                        if (inAuthor) {
                            authorsList.add(
                                Author(
                                    name =
                                        currentAuthorName,
                                    affiliation =
                                        currentAffiliations
                                            .filter {
                                                it.isNotBlank()
                                            }
                                            .joinToString(
                                                "; "
                                            )
                                )
                            )
                        }
                        inAuthor = false
                    }

                    "entry" -> {
                        papers.add(
                            Paper(
                                title = title,
                                summary = summary,
                                authors =
                                    authorsList.toList(),
                                categories =
                                    categoriesList.toList(),
                                published = published,
                                arxivId = arxivId,
                                arxivUrl =
                                    arxivUrl.ifBlank {
                                        "https://arxiv.org/abs/$arxivId"
                                    }
                            )
                        )
                        inEntry = false
                    }
                }
            }
        }

        event = parser.next()
    }

    return papers
}
