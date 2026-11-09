package com.leslie.tvhome
import org.junit.Assert.*
import org.junit.Test
class FolderProfileKeyTest {
 @Test fun defaultProfileIsBackwardCompatible() {
  assertEquals("folders", FolderProfileKey.key(0))
  assertEquals("folders", FolderProfileKey.key(-1))
 }
 @Test fun laterProfilesAreIsolated() {
  assertEquals("folders_profile_1", FolderProfileKey.key(1))
  assertEquals("folders_profile_2", FolderProfileKey.key(2))
  assertEquals("folders_profile_2", FolderProfileKey.key(99))
 }
}
