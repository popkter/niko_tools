import unittest
from release_version import read_version, should_publish


class ReleaseVersionTest(unittest.TestCase):
    def test_only_increased_main_push_publishes(self):
        self.assertTrue(should_publish("push", "refs/heads/main", "0.1.2", "0.1.1"))
        self.assertTrue(should_publish("push", "refs/heads/main", "0.1.10", "0.1.9"))
        self.assertFalse(should_publish("push", "refs/heads/main", "0.1.2", "0.1.2"))
        self.assertFalse(should_publish("push", "refs/heads/feature", "0.1.2", "0.1.1"))
        self.assertFalse(should_publish("pull_request", "refs/pull/1/merge", "0.1.2", "0.1.1"))
        self.assertFalse(should_publish("push", "refs/tags/v0.1.2", "0.1.2", "0.1.1"))
        self.assertFalse(should_publish("push", "refs/heads/main", "0.1.2"))
        with self.assertRaises(ValueError):
            should_publish("push", "refs/heads/main", "0.1.1", "0.1.2")

    def test_manual_publish_requires_main_and_explicit_input(self):
        self.assertFalse(should_publish("workflow_dispatch", "refs/heads/main", "0.1.2"))
        self.assertTrue(should_publish("workflow_dispatch", "refs/heads/main", "0.1.2", manual=True))
        self.assertFalse(should_publish("workflow_dispatch", "refs/heads/feature", "0.1.2", manual=True))

    def test_properties_have_one_valid_version(self):
        self.assertEqual("0.1.2", read_version("group = com.poptools\nversion = 0.1.2\n"))
        for value in ["version = bad", "version = 0.1", "version = 0.1.2\nversion = 0.1.3", ""]:
            with self.assertRaises(ValueError):
                read_version(value)


if __name__ == "__main__":
    unittest.main()
