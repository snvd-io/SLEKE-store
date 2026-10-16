import pathlib
import subprocess
import sys
import tempfile
import unittest

CHECKER = pathlib.Path(__file__).resolve().parents[1] / "check_sleke_architecture.py"


class ArchitectureTests(unittest.TestCase):
    def check(self, source, package="com.sleke.library.catalog.domain", gradle=""):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            path = root / "sleke/src/test/java/com/sleke/library/nested/Example.kt"
            path.parent.mkdir(parents=True)
            path.write_text(f"package {package}\n{source}\n")
            (root / "sleke/build.gradle.kts").write_text(gradle)
            app = root / "app/src/main/java/Adapter.kt"
            app.parent.mkdir(parents=True)
            app.write_text("import com.aurora.store.AuroraApp\n")
            return subprocess.run([sys.executable, str(CHECKER), "--root", str(root)], capture_output=True, text=True)

    def test_plain_domain_and_host_adapter_pass(self):
        self.assertEqual(0, self.check("data class App(val packageName: String)").returncode)

    def test_aurora_import_and_fully_qualified_reference_fail(self):
        for source in ["import com.aurora.store.AuroraApp", "val app: com.aurora.store.AuroraApp? = null", "import com.sleke.integration.auth.PlayGateway"]:
            with self.subTest(source=source):
                result = self.check(source, "com.sleke.library.catalog.data")
                self.assertNotEqual(0, result.returncode)
                self.assertIn("Example.kt:2", result.stdout)

    def test_domain_platform_and_network_types_fail(self):
        for name in ["android.content.Context", "androidx.compose.runtime.State", "com.google.firebase.auth.FirebaseUser", "androidx.work.WorkManager", "androidx.paging.PagingData", "io.ktor.client.HttpClient", "okhttp3.Response", "java.net.URL", "java.io.File", "android.net.Uri", "com.google.android.gms.tasks.Task", "org.apache.http.HttpResponse"]:
            with self.subTest(name=name):
                self.assertNotEqual(0, self.check(f"val value: {name}? = null").returncode)

    def test_non_domain_platform_adapter_passes(self):
        self.assertEqual(0, self.check("import android.content.Context", "com.sleke.library.runtime.data").returncode)

    def test_app_and_aurora_dependencies_fail(self):
        for declaration in ['implementation(project(":app"))', 'implementation(project(path = ":app"))', 'api("com.aurora:gplayapi:1.0")', 'implementation(projects.app)']:
            with self.subTest(declaration=declaration):
                self.assertNotEqual(0, self.check("data class App(val name: String)", gradle=declaration).returncode)

    def test_comments_and_string_literals_do_not_trigger_source_rule(self):
        source = '''/* import android.content.Context
         * com.aurora.store.AuroraApp
         */
        /* Outer /* nested */ com.aurora.store.AuroraApp */
        // io.ktor.client.HttpClient
        val message = "android.content.Context com.aurora.store.AuroraApp"
        val raw = """androidx.work.WorkManager"""
        data class App(val name: String)
        '''
        self.assertEqual(0, self.check(source, gradle='// implementation(project(":app"))').returncode)


if __name__ == "__main__":
    unittest.main()
