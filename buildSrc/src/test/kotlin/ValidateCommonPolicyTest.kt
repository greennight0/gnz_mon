import kotlin.test.Test
import kotlin.test.assertFails

class ValidateCommonPolicyTest {
    private fun policy(label: String = "banana", score: Double = .75, sha: String = "abc", mode: String = "imagenet") = """
      {"schemaVersion":1,"version":"test","mode":"$mode","modelSha256":"$sha","classes":{
        "$label":{"minimumMean":$score,"minimumView":0.5,"minimumMargin":0.2,"minimumViewMargin":0.1,
          "suggestionMinimum":null,"confirmationEnabled":true}}}
    """
    @Test fun acceptsMatchingModelPolicy() = validateCommonPolicy(policy(), "abc", setOf("banana", "other"))
    @Test fun rejectsUnsupportedClassAndWrongModel() {
        assertFails { validateCommonPolicy(policy(label = "kumquat"), "abc", setOf("banana")) }
        assertFails { validateCommonPolicy(policy(sha = "wrong"), "abc", setOf("banana")) }
    }
    @Test fun rejectsInvalidThresholdAndMissingSpecializedClass() {
        assertFails { validateCommonPolicy(policy(score = 1.1), "abc", setOf("banana")) }
        assertFails { validateCommonPolicy(policy(mode = "produce20"), "abc", setOf("banana", "lime")) }
    }
}
