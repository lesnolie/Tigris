package com.leslie.tvhome
import org.junit.Assert.*
import org.junit.Test

class DoubleHomePolicyTest {
 @Test fun acceptsDeliberateDoublePress() {
  assertTrue(DoubleHomePolicy.second(1000L,1250L,true))
  assertTrue(DoubleHomePolicy.second(1000L,1520L,true))
 }
 @Test fun rejectsSingleFastRepeatOrDelayed() {
  assertFalse(DoubleHomePolicy.second(0L,200L,true))
  assertFalse(DoubleHomePolicy.second(1000L,1030L,true))
  assertFalse(DoubleHomePolicy.second(1000L,1600L,true))
  assertFalse(DoubleHomePolicy.second(1000L,1250L,false))
 }
}
